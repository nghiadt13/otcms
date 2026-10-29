-- Rename existing tables without changing their data or foreign key relationships.
ALTER TABLE external_identities RENAME TO account_identities;
ALTER TABLE consent_evidence RENAME TO account_consents;
ALTER TABLE email_verification_challenges RENAME TO email_verifications;
ALTER TABLE password_reset_challenges RENAME TO password_resets;
ALTER TABLE auth_sessions RENAME TO sessions;
ALTER TABLE authentication_audits RENAME TO auth_audits;
ALTER TABLE doctor_service_assignments RENAME TO doctor_services;
ALTER TABLE doctor_work_shifts RENAME TO doctor_shifts;
ALTER TABLE doctor_operational_time_off RENAME TO doctor_time_off;
ALTER TABLE appointment_command_replays RENAME TO appointment_checkin_requests;
ALTER TABLE clinic_visits RENAME TO visits;
ALTER TABLE recorded_appointment_arrivals RENAME TO appointment_arrivals;
ALTER TABLE visit_initial_receptions RENAME TO visit_check_ins;
ALTER TABLE walk_in_reception_replays RENAME TO walk_in_requests;
ALTER TABLE visit_care_assignments RENAME TO visit_assignments;
ALTER TABLE visit_clinical_examinations RENAME TO visit_examinations;
ALTER TABLE visit_care_assignment_audits RENAME TO visit_assignment_audits;
ALTER TABLE patient_care_relationships RENAME TO patient_caregivers;
ALTER TABLE visit_rooms RENAME TO rooms;
ALTER TABLE visit_room_capabilities RENAME TO room_capabilities;
ALTER TABLE visit_room_reservations RENAME TO room_reservations;
ALTER TABLE visit_room_reservation_audits RENAME TO room_reservation_audits;
ALTER TABLE visit_room_management_audits RENAME TO room_management_audits;
ALTER TABLE integration_coordination_guard RENAME TO transaction_guard;
ALTER TABLE staff_resource_management_audits RENAME TO staff_resource_audits;
ALTER TABLE service_catalog_management_audits RENAME TO service_audits;

-- PL/pgSQL bodies store table names as text, so refresh functions that query renamed tables.
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

    IF TG_TABLE_NAME = 'appointments'
       AND NEW.booking_source = 'DOCTOR_FOLLOW_UP'
       AND NEW.source_appointment_id IS NOT NULL THEN
        SELECT v.originating_appointment_id INTO visit_appointment_id
        FROM visits v WHERE v.id = NEW.source_visit_id;
        IF visit_appointment_id IS DISTINCT FROM NEW.source_appointment_id THEN
            RAISE EXCEPTION 'follow-up source appointment must match the source visit' USING ERRCODE = '23514';
        END IF;
    END IF;

    IF appointment_visit_id IS NOT NULL THEN
        SELECT v.originating_appointment_id, v.source INTO visit_appointment_id, visit_source
        FROM visits v WHERE v.id = appointment_visit_id;
        IF visit_appointment_id IS DISTINCT FROM target_appointment_id OR visit_source IS DISTINCT FROM 'SCHEDULED' THEN
            RAISE EXCEPTION 'appointment visit link is inconsistent' USING ERRCODE = '23514';
        END IF;
        SELECT EXISTS (
            SELECT 1 FROM visit_check_ins r
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

CREATE OR REPLACE FUNCTION enforce_initial_reception_type() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    source_type VARCHAR(16);
    appointment_id UUID;
    visit_prior_appointment_id UUID;
BEGIN
    SELECT v.source, v.originating_appointment_id, v.prior_appointment_id
    INTO source_type, appointment_id, visit_prior_appointment_id
    FROM visits v WHERE v.id = COALESCE(NEW.visit_id, OLD.visit_id);
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;
    IF (NEW.reception_type = 'APPOINTMENT_CHECK_IN' AND (source_type <> 'SCHEDULED' OR appointment_id IS NULL))
       OR (NEW.reception_type = 'WALK_IN' AND source_type <> 'WALK_IN') THEN
        RAISE EXCEPTION 'initial reception type does not match visit source' USING ERRCODE = '23514';
    END IF;
    IF NEW.prior_appointment_id IS DISTINCT FROM visit_prior_appointment_id THEN
        RAISE EXCEPTION 'initial reception prior appointment must match visit reference' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_checked_in_appointment_reception_for_visit(target_visit_id UUID) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    linked_appointment_id UUID;
    linked_appointment_status VARCHAR(16);
    linked_appointment_visit_id UUID;
    has_check_in_reception BOOLEAN;
BEGIN
    SELECT v.originating_appointment_id INTO linked_appointment_id
    FROM visits v WHERE v.id = target_visit_id;
    IF linked_appointment_id IS NULL THEN
        RETURN;
    END IF;
    SELECT a.status, a.visit_id INTO linked_appointment_status, linked_appointment_visit_id
    FROM appointments a WHERE a.id = linked_appointment_id;
    IF linked_appointment_status IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED')
       AND linked_appointment_visit_id = target_visit_id THEN
        SELECT EXISTS (
            SELECT 1 FROM visit_check_ins r
            WHERE r.visit_id = target_visit_id AND r.reception_type = 'APPOINTMENT_CHECK_IN'
        ) INTO has_check_in_reception;
        IF NOT has_check_in_reception THEN
            RAISE EXCEPTION 'checked-in appointment requires initial appointment reception evidence' USING ERRCODE = '23514';
        END IF;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_checked_in_appointment_reception_evidence() RETURNS trigger
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
        FROM visits v WHERE v.id = NEW.visit_id;
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
