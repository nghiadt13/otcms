CREATE TABLE walk_in_reception_queue_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    visit_id UUID NOT NULL REFERENCES visits(id) ON DELETE RESTRICT,
    arrived_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'WAITING',
    entered_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    left_at TIMESTAMPTZ,
    exit_reason VARCHAR(24),
    CONSTRAINT ck_walk_in_reception_queue_status
        CHECK (status IN ('WAITING', 'LEFT_RECEPTION')),
    CONSTRAINT ck_walk_in_reception_queue_exit
        CHECK (
            (status = 'WAITING' AND left_at IS NULL AND exit_reason IS NULL)
            OR (status = 'LEFT_RECEPTION' AND left_at IS NOT NULL
                AND exit_reason IN ('CARE_STARTED', 'CANCELLED', 'VISIT_CLOSED'))
        )
);

CREATE UNIQUE INDEX uk_walk_in_reception_queue_active_visit
    ON walk_in_reception_queue_entries (visit_id)
    WHERE status = 'WAITING';

CREATE INDEX ix_walk_in_reception_queue_fifo
    ON walk_in_reception_queue_entries (arrived_at, id)
    WHERE status = 'WAITING';

CREATE TABLE walk_in_reception_queue_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_entry_id UUID NOT NULL REFERENCES walk_in_reception_queue_entries(id) ON DELETE RESTRICT,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    reason VARCHAR(24),
    actor_account_id UUID REFERENCES accounts(id) ON DELETE RESTRICT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    correlation_id VARCHAR(128),
    CONSTRAINT ck_walk_in_reception_queue_history_transition
        CHECK (
            (from_status IS NULL AND to_status = 'WAITING' AND reason IS NULL)
            OR (from_status = 'WAITING' AND to_status = 'LEFT_RECEPTION'
                AND reason IN ('CARE_STARTED', 'CANCELLED', 'VISIT_CLOSED'))
        ),
    CONSTRAINT ck_walk_in_reception_queue_history_correlation
        CHECK (correlation_id IS NULL OR length(trim(correlation_id)) BETWEEN 1 AND 128)
);

CREATE INDEX ix_walk_in_reception_queue_history_entry_time
    ON walk_in_reception_queue_history (queue_entry_id, occurred_at, id);

CREATE FUNCTION enforce_walk_in_reception_queue_visit() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    visit_source VARCHAR(16);
    visit_arrived_at TIMESTAMPTZ;
BEGIN
    SELECT source, arrived_at INTO visit_source, visit_arrived_at
    FROM visits WHERE id = NEW.visit_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'queue entry Visit does not exist' USING ERRCODE = '23503';
    END IF;
    IF visit_source IS DISTINCT FROM 'WALK_IN' THEN
        RAISE EXCEPTION 'only WALK_IN Visits may enter reception queue' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'INSERT' AND NEW.status IS DISTINCT FROM 'WAITING' THEN
        RAISE EXCEPTION 'new reception queue entries must start in WAITING' USING ERRCODE = '23514';
    END IF;
    IF NEW.arrived_at IS DISTINCT FROM visit_arrived_at THEN
        RAISE EXCEPTION 'queue entry arrival time must match Visit arrival time' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_walk_in_reception_queue_visit_guard
    BEFORE INSERT OR UPDATE OF visit_id, arrived_at, status, entered_at, left_at, exit_reason
    ON walk_in_reception_queue_entries
    FOR EACH ROW EXECUTE FUNCTION enforce_walk_in_reception_queue_visit();

CREATE FUNCTION protect_walk_in_reception_queue_entry_lifecycle() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.visit_id IS DISTINCT FROM OLD.visit_id
       OR NEW.arrived_at IS DISTINCT FROM OLD.arrived_at
       OR NEW.entered_at IS DISTINCT FROM OLD.entered_at THEN
        RAISE EXCEPTION 'reception queue entry identity and arrival fields are immutable' USING ERRCODE = '55000';
    END IF;
    IF OLD.status IS DISTINCT FROM 'WAITING' THEN
        RAISE EXCEPTION 'closed reception queue entries cannot be reopened or changed' USING ERRCODE = '55000';
    END IF;
    IF NEW.status IS DISTINCT FROM 'LEFT_RECEPTION' THEN
        RAISE EXCEPTION 'active reception queue entries may only transition to LEFT_RECEPTION' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_walk_in_reception_queue_entry_lifecycle
    BEFORE UPDATE ON walk_in_reception_queue_entries
    FOR EACH ROW EXECUTE FUNCTION protect_walk_in_reception_queue_entry_lifecycle();

CREATE FUNCTION protect_queued_visit_source_and_arrival() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM walk_in_reception_queue_entries q WHERE q.visit_id = OLD.id) THEN
        IF NEW.source IS DISTINCT FROM OLD.source THEN
            RAISE EXCEPTION 'Visit source cannot change after a reception queue entry exists' USING ERRCODE = '23514';
        END IF;
        IF NEW.arrived_at IS DISTINCT FROM OLD.arrived_at THEN
            RAISE EXCEPTION 'Visit arrival time cannot change after a reception queue entry exists' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_visits_protect_reception_queue_identity
    BEFORE UPDATE OF source, arrived_at ON visits
    FOR EACH ROW EXECUTE FUNCTION protect_queued_visit_source_and_arrival();

CREATE FUNCTION reject_walk_in_reception_queue_history_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'walk-in reception queue history is immutable' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER tr_walk_in_reception_queue_history_immutable
    BEFORE UPDATE OR DELETE ON walk_in_reception_queue_history
    FOR EACH ROW EXECUTE FUNCTION reject_walk_in_reception_queue_history_mutation();

CREATE TRIGGER tr_walk_in_reception_queue_history_no_truncate
    BEFORE TRUNCATE ON walk_in_reception_queue_history
    FOR EACH STATEMENT EXECUTE FUNCTION reject_walk_in_reception_queue_history_mutation();
