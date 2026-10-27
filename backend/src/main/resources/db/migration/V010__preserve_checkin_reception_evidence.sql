CREATE FUNCTION enforce_checked_in_appointment_reception_for_visit(target_visit_id UUID) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    linked_appointment_id UUID;
    linked_appointment_status VARCHAR(16);
    linked_appointment_visit_id UUID;
    has_check_in_reception BOOLEAN;
BEGIN
    SELECT v.originating_appointment_id INTO linked_appointment_id
    FROM clinic_visits v WHERE v.id = target_visit_id;
    IF linked_appointment_id IS NULL THEN
        RETURN;
    END IF;

    SELECT a.status, a.visit_id INTO linked_appointment_status, linked_appointment_visit_id
    FROM appointments a WHERE a.id = linked_appointment_id;
    IF linked_appointment_status IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED')
       AND linked_appointment_visit_id = target_visit_id THEN
        SELECT EXISTS (
            SELECT 1 FROM visit_initial_receptions r
            WHERE r.visit_id = target_visit_id AND r.reception_type = 'APPOINTMENT_CHECK_IN'
        ) INTO has_check_in_reception;
        IF NOT has_check_in_reception THEN
            RAISE EXCEPTION 'checked-in appointment requires initial appointment reception evidence' USING ERRCODE = '23514';
        END IF;
    END IF;
END;
$$;

CREATE FUNCTION enforce_checked_in_appointment_reception_evidence() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    linked_appointment_id UUID;
    linked_appointment_status VARCHAR(16);
    linked_appointment_visit_id UUID;
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM enforce_checked_in_appointment_reception_for_visit(OLD.visit_id);
        RETURN NULL;
    END IF;

    PERFORM enforce_checked_in_appointment_reception_for_visit(OLD.visit_id);
    IF NEW.visit_id IS DISTINCT FROM OLD.visit_id THEN
        PERFORM enforce_checked_in_appointment_reception_for_visit(NEW.visit_id);
    ELSE
        SELECT v.originating_appointment_id INTO linked_appointment_id
        FROM clinic_visits v WHERE v.id = NEW.visit_id;
        IF linked_appointment_id IS NOT NULL THEN
            SELECT a.status, a.visit_id INTO linked_appointment_status, linked_appointment_visit_id
            FROM appointments a WHERE a.id = linked_appointment_id;
            IF linked_appointment_status IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED')
               AND linked_appointment_visit_id = NEW.visit_id
               AND NEW.reception_type IS DISTINCT FROM 'APPOINTMENT_CHECK_IN' THEN
                RAISE EXCEPTION 'checked-in appointment requires initial appointment reception evidence' USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_preserve_checked_in_appointment_reception
    AFTER DELETE OR UPDATE ON visit_initial_receptions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION enforce_checked_in_appointment_reception_evidence();
