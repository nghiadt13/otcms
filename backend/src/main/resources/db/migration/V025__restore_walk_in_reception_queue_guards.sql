-- Some databases applied an earlier V023 queue guard. Reconcile that schema
-- without changing V023 or V024, and refuse to strengthen inconsistent data.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM walk_in_reception_queue_entries q
        LEFT JOIN visits v ON v.id = q.visit_id
        WHERE v.id IS NULL OR v.source IS DISTINCT FROM 'WALK_IN'
           OR q.arrived_at IS DISTINCT FROM v.arrived_at
           OR NOT EXISTS (
               SELECT 1 FROM walk_in_reception_queue_history h
               WHERE h.queue_entry_id = q.id AND h.from_status IS NULL AND h.to_status = 'WAITING'
           )
           OR (q.status = 'LEFT_RECEPTION' AND NOT EXISTS (
               SELECT 1 FROM walk_in_reception_queue_history h
               WHERE h.queue_entry_id = q.id AND h.from_status = 'WAITING'
                 AND h.to_status = 'LEFT_RECEPTION' AND h.reason = q.exit_reason
           ))
    ) THEN
        RAISE EXCEPTION 'existing reception queue data require review before guard reconciliation'
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_walk_in_reception_queue_visit() RETURNS trigger
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

DROP TRIGGER IF EXISTS tr_walk_in_reception_queue_visit_guard ON walk_in_reception_queue_entries;
CREATE TRIGGER tr_walk_in_reception_queue_visit_guard
    BEFORE INSERT OR UPDATE OF visit_id, arrived_at, status, entered_at, left_at, exit_reason
    ON walk_in_reception_queue_entries
    FOR EACH ROW EXECUTE FUNCTION enforce_walk_in_reception_queue_visit();

CREATE OR REPLACE FUNCTION protect_walk_in_reception_queue_entry_lifecycle() RETURNS trigger
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

DROP TRIGGER IF EXISTS tr_walk_in_reception_queue_entry_lifecycle ON walk_in_reception_queue_entries;
CREATE TRIGGER tr_walk_in_reception_queue_entry_lifecycle
    BEFORE UPDATE ON walk_in_reception_queue_entries
    FOR EACH ROW EXECUTE FUNCTION protect_walk_in_reception_queue_entry_lifecycle();
