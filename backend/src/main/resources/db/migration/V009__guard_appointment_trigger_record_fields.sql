CREATE OR REPLACE FUNCTION enforce_appointment_visit_link() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    target_appointment_id UUID;
    target_visit_id UUID;
    appointment_status VARCHAR(16);
    appointment_visit_id UUID;
    visit_appointment_id UUID;
    visit_source VARCHAR(16);
    has_initial_reception BOOLEAN;
BEGIN
    IF TG_TABLE_NAME = 'appointments' THEN
        target_appointment_id := NEW.id;
    ELSE
        target_visit_id := NEW.id;
        target_appointment_id := NEW.originating_appointment_id;
        IF target_appointment_id IS NULL THEN
            RETURN NULL;
        END IF;
        SELECT a.visit_id INTO appointment_visit_id
        FROM appointments a WHERE a.id = target_appointment_id;
        IF appointment_visit_id IS DISTINCT FROM target_visit_id THEN
            RAISE EXCEPTION 'visit must be the appointment linked visit' USING ERRCODE = '23514';
        END IF;
    END IF;

    SELECT a.status, a.visit_id INTO appointment_status, appointment_visit_id
    FROM appointments a WHERE a.id = target_appointment_id;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    IF TG_TABLE_NAME = 'appointments' THEN
        IF NEW.booking_source = 'DOCTOR_FOLLOW_UP' AND NEW.source_appointment_id IS NOT NULL THEN
            SELECT v.originating_appointment_id INTO visit_appointment_id
            FROM clinic_visits v WHERE v.id = NEW.source_visit_id;
            IF visit_appointment_id IS DISTINCT FROM NEW.source_appointment_id THEN
                RAISE EXCEPTION 'follow-up source appointment must match the source visit' USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;

    IF appointment_visit_id IS NOT NULL THEN
        SELECT v.originating_appointment_id, v.source INTO visit_appointment_id, visit_source
        FROM clinic_visits v WHERE v.id = appointment_visit_id;
        IF visit_appointment_id IS DISTINCT FROM target_appointment_id OR visit_source IS DISTINCT FROM 'SCHEDULED' THEN
            RAISE EXCEPTION 'appointment visit link is inconsistent' USING ERRCODE = '23514';
        END IF;
        SELECT EXISTS (
            SELECT 1 FROM visit_initial_receptions r
            WHERE r.visit_id = appointment_visit_id AND r.reception_type = 'APPOINTMENT_CHECK_IN'
        ) INTO has_initial_reception;
        IF NOT has_initial_reception THEN
            RAISE EXCEPTION 'linked appointment visit requires initial reception evidence' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF appointment_status IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED') AND appointment_visit_id IS NULL THEN
        RAISE EXCEPTION 'checked-in appointment requires a linked visit' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
