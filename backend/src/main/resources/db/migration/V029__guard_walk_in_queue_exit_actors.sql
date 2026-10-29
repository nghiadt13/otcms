-- Queue transitions are recorded by the transition trigger. Match the required
-- active actor role to the operation represented by the transition reason.
CREATE OR REPLACE FUNCTION record_walk_in_reception_queue_transition() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    actor_text TEXT;
    actor_id UUID;
    expected_actor_type VARCHAR(24);
    correlation TEXT;
BEGIN
    actor_text := NULLIF(current_setting('otcms.reception_actor_id', true), '');
    IF actor_text IS NULL THEN
        RAISE EXCEPTION 'reception queue transition requires an actor'
            USING ERRCODE = '23514';
    END IF;
    actor_id := actor_text::UUID;

    IF TG_OP = 'INSERT' THEN
        expected_actor_type := 'RECEPTIONIST';
    ELSIF OLD.status = 'WAITING' AND NEW.status = 'LEFT_RECEPTION' THEN
        expected_actor_type := CASE NEW.exit_reason
            WHEN 'CARE_STARTED' THEN 'DOCTOR'
            WHEN 'VISIT_CLOSED' THEN 'DOCTOR'
            WHEN 'CANCELLED' THEN 'RECEPTIONIST'
            ELSE NULL
        END;
        IF expected_actor_type IS NULL THEN
            RAISE EXCEPTION 'unsupported reception queue exit reason'
                USING ERRCODE = '23514';
        END IF;
    ELSE
        RAISE EXCEPTION 'invalid reception queue transition' USING ERRCODE = '23514';
    END IF;

    PERFORM 1 FROM accounts
    WHERE id = actor_id AND account_type = expected_actor_type AND status = 'ACTIVE'
    FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'queue transition requires an active % actor', expected_actor_type
            USING ERRCODE = '23514';
    END IF;

    correlation := NULLIF(current_setting('otcms.reception_correlation_id', true), '');
    IF correlation IS NOT NULL AND (length(correlation) > 128 OR length(trim(correlation)) = 0) THEN
        RAISE EXCEPTION 'invalid reception queue correlation ID'
            USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'INSERT' THEN
        INSERT INTO walk_in_reception_queue_history
            (queue_entry_id, from_status, to_status, actor_account_id, occurred_at, correlation_id)
        VALUES (NEW.id, NULL, 'WAITING', actor_id, clock_timestamp(), correlation);
    ELSE
        INSERT INTO walk_in_reception_queue_history
            (queue_entry_id, from_status, to_status, reason, actor_account_id, occurred_at, correlation_id)
        VALUES (NEW.id, 'WAITING', 'LEFT_RECEPTION', NEW.exit_reason, actor_id, clock_timestamp(), correlation);
    END IF;
    RETURN NULL;
END;
$$;

ALTER TABLE visit_audits DROP CONSTRAINT ck_visit_audits_action;
ALTER TABLE visit_audits ADD CONSTRAINT ck_visit_audits_action
    CHECK (action IN ('WALK_IN_CREATED', 'WALK_IN_CANCELLED', 'WALK_IN_CLOSED'));
