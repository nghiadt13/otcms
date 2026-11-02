ALTER TABLE clinic_visits
    ADD CONSTRAINT uk_clinic_visits_id_patient UNIQUE (id, patient_profile_id);

CREATE TABLE appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_profile_id UUID NOT NULL REFERENCES patient_profiles(id) ON DELETE RESTRICT,
    service_id UUID NOT NULL REFERENCES clinic_services(id) ON DELETE RESTRICT,
    doctor_profile_id UUID NOT NULL REFERENCES doctor_profiles(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED',
    booking_source VARCHAR(24) NOT NULL,
    purpose VARCHAR(1000) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL,
    buffer_minutes INTEGER NOT NULL DEFAULT 0,
    visit_id UUID,
    source_visit_id UUID,
    source_appointment_id UUID,
    ordering_doctor_profile_id UUID REFERENCES doctor_profiles(id) ON DELETE RESTRICT,
    created_by_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_appointments_status CHECK (status IN (
        'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'
    )),
    CONSTRAINT ck_appointments_booking_source CHECK (booking_source IN (
        'PATIENT_ONLINE', 'RECEPTIONIST_ASSISTED', 'DOCTOR_FOLLOW_UP'
    )),
    CONSTRAINT ck_appointments_source_evidence CHECK (
        (booking_source = 'DOCTOR_FOLLOW_UP' AND source_visit_id IS NOT NULL AND ordering_doctor_profile_id IS NOT NULL)
        OR (booking_source <> 'DOCTOR_FOLLOW_UP' AND source_visit_id IS NULL AND source_appointment_id IS NULL AND ordering_doctor_profile_id IS NULL)
    ),
    CONSTRAINT ck_appointments_values CHECK (
        length(trim(purpose)) BETWEEN 1 AND 1000
        AND duration_minutes > 0
        AND buffer_minutes >= 0
        AND end_time = start_time + make_interval(mins => duration_minutes)
        AND version >= 0
    ),
    CONSTRAINT ck_appointments_visit_status_link CHECK (
        (status NOT IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED') OR visit_id IS NOT NULL)
        AND (status NOT IN ('CONFIRMED', 'NO_SHOW') OR visit_id IS NULL)
    ),
    CONSTRAINT fk_appointments_doctor_service_assignment FOREIGN KEY (doctor_profile_id, service_id)
        REFERENCES doctor_service_assignments(doctor_profile_id, service_id) ON DELETE RESTRICT,
    CONSTRAINT uk_appointments_id_patient UNIQUE (id, patient_profile_id),
    CONSTRAINT uk_appointments_visit UNIQUE (visit_id),
    CONSTRAINT uk_appointments_id_visit UNIQUE (id, visit_id),
    CONSTRAINT uk_appointments_id_source_visit UNIQUE (id, source_visit_id),
    CONSTRAINT fk_appointments_visit_patient FOREIGN KEY (visit_id, patient_profile_id)
        REFERENCES clinic_visits(id, patient_profile_id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_source_visit_patient FOREIGN KEY (source_visit_id, patient_profile_id)
        REFERENCES clinic_visits(id, patient_profile_id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointments_source_appointment_patient FOREIGN KEY (source_appointment_id, patient_profile_id)
        REFERENCES appointments(id, patient_profile_id) ON DELETE RESTRICT
);

CREATE INDEX ix_appointments_patient_start ON appointments (patient_profile_id, start_time DESC, id);
CREATE INDEX ix_appointments_doctor_start ON appointments (doctor_profile_id, start_time, id);
CREATE INDEX ix_appointments_status_start ON appointments (status, start_time, id);
CREATE INDEX ix_appointments_source_visit ON appointments (source_visit_id) WHERE source_visit_id IS NOT NULL;
CREATE INDEX ix_appointments_source_appointment ON appointments (source_appointment_id) WHERE source_appointment_id IS NOT NULL;

ALTER TABLE clinic_visits
    ADD CONSTRAINT fk_clinic_visits_originating_appointment_patient
        FOREIGN KEY (originating_appointment_id, patient_profile_id)
        REFERENCES appointments(id, patient_profile_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_clinic_visits_prior_appointment_patient
        FOREIGN KEY (prior_appointment_id, patient_profile_id)
        REFERENCES appointments(id, patient_profile_id) ON DELETE RESTRICT;

ALTER TABLE recorded_appointment_arrivals
    ADD CONSTRAINT fk_recorded_arrivals_appointment_patient
        FOREIGN KEY (appointment_id, patient_profile_id)
        REFERENCES appointments(id, patient_profile_id) ON DELETE RESTRICT;

CREATE TABLE appointment_audits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id UUID NOT NULL REFERENCES appointments(id) ON DELETE RESTRICT,
    actor_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    actor_type VARCHAR(16) NOT NULL,
    action VARCHAR(32) NOT NULL,
    old_status VARCHAR(16),
    new_status VARCHAR(16) NOT NULL,
    old_doctor_profile_id UUID REFERENCES doctor_profiles(id) ON DELETE RESTRICT,
    new_doctor_profile_id UUID NOT NULL REFERENCES doctor_profiles(id) ON DELETE RESTRICT,
    old_start_time TIMESTAMPTZ,
    new_start_time TIMESTAMPTZ NOT NULL,
    old_end_time TIMESTAMPTZ,
    new_end_time TIMESTAMPTZ NOT NULL,
    reason VARCHAR(1000),
    linked_visit_id UUID,
    source_visit_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    CONSTRAINT ck_appointment_audits_actor CHECK (actor_type IN ('PATIENT', 'RECEPTIONIST', 'DOCTOR', 'ADMIN')),
    CONSTRAINT ck_appointment_audits_action CHECK (action IN (
        'CREATED', 'RESCHEDULED', 'CANCELLED', 'STATUS_CHANGED', 'DOCTOR_REASSIGNED', 'CONFLICT_RESOLVED'
    )),
    CONSTRAINT ck_appointment_audits_status CHECK (
        old_status IS NULL OR old_status IN ('CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW')
    ),
    CONSTRAINT ck_appointment_audits_new_status CHECK (
        new_status IN ('CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW')
    ),
    CONSTRAINT ck_appointment_audits_interval CHECK (
        new_end_time > new_start_time
        AND (
            (old_start_time IS NULL AND old_end_time IS NULL)
            OR (old_start_time IS NOT NULL AND old_end_time IS NOT NULL AND old_end_time > old_start_time)
        )
    ),
    CONSTRAINT ck_appointment_audits_old_state CHECK (
        (old_status IS NULL AND old_doctor_profile_id IS NULL AND old_start_time IS NULL AND old_end_time IS NULL)
        OR (old_status IS NOT NULL AND old_doctor_profile_id IS NOT NULL AND old_start_time IS NOT NULL AND old_end_time IS NOT NULL)
    ),
    CONSTRAINT ck_appointment_audits_reason CHECK (reason IS NULL OR length(trim(reason)) BETWEEN 1 AND 1000),
    CONSTRAINT ck_appointment_audits_correlation CHECK (length(trim(correlation_id)) BETWEEN 1 AND 128),
    CONSTRAINT fk_appointment_audits_linked_visit FOREIGN KEY (appointment_id, linked_visit_id)
        REFERENCES appointments(id, visit_id) ON DELETE RESTRICT,
    CONSTRAINT fk_appointment_audits_source_visit FOREIGN KEY (appointment_id, source_visit_id)
        REFERENCES appointments(id, source_visit_id) ON DELETE RESTRICT
);

CREATE INDEX ix_appointment_audits_appointment_time ON appointment_audits (appointment_id, occurred_at, id);
CREATE INDEX ix_appointment_audits_actor_time ON appointment_audits (actor_account_id, occurred_at DESC, id);
CREATE INDEX ix_appointment_audits_correlation ON appointment_audits (correlation_id);

CREATE FUNCTION reject_append_only_appointment_row_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'appointment audit and replay rows are immutable' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER tr_appointment_audits_immutable
    BEFORE UPDATE OR DELETE ON appointment_audits
    FOR EACH ROW EXECUTE FUNCTION reject_append_only_appointment_row_mutation();

CREATE TABLE appointment_command_replays (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    appointment_id UUID NOT NULL REFERENCES appointments(id) ON DELETE RESTRICT,
    command VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    result_visit_id UUID NOT NULL REFERENCES clinic_visits(id) ON DELETE RESTRICT,
    result_checked_in_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_appointment_command_replay UNIQUE (actor_account_id, appointment_id, command, idempotency_key),
    CONSTRAINT fk_appointment_replay_appointment_visit FOREIGN KEY (appointment_id, result_visit_id)
        REFERENCES appointments(id, visit_id) ON DELETE RESTRICT,
    CONSTRAINT ck_appointment_command_replay_command CHECK (command = 'CHECK_IN'),
    CONSTRAINT ck_appointment_command_replay_key CHECK (length(trim(idempotency_key)) BETWEEN 1 AND 128),
    CONSTRAINT ck_appointment_command_replay_fingerprint CHECK (request_fingerprint ~ '^[0-9a-fA-F]{64}$')
);

CREATE INDEX ix_appointment_command_replays_appointment ON appointment_command_replays (appointment_id, created_at, id);

CREATE TRIGGER tr_appointment_command_replays_immutable
    BEFORE UPDATE OR DELETE ON appointment_command_replays
    FOR EACH ROW EXECUTE FUNCTION reject_append_only_appointment_row_mutation();

CREATE FUNCTION enforce_appointment_visit_link() RETURNS trigger
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
        FROM clinic_visits v WHERE v.id = NEW.source_visit_id;
        IF visit_appointment_id IS DISTINCT FROM NEW.source_appointment_id THEN
            RAISE EXCEPTION 'follow-up source appointment must match the source visit' USING ERRCODE = '23514';
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

CREATE CONSTRAINT TRIGGER ct_appointments_visit_link
    AFTER INSERT OR UPDATE ON appointments
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION enforce_appointment_visit_link();

CREATE CONSTRAINT TRIGGER ct_clinic_visits_appointment_link
    AFTER INSERT OR UPDATE ON clinic_visits
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION enforce_appointment_visit_link();

CREATE FUNCTION enforce_initial_reception_type() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    source_type VARCHAR(16);
    appointment_id UUID;
    visit_prior_appointment_id UUID;
BEGIN
    SELECT v.source, v.originating_appointment_id, v.prior_appointment_id
    INTO source_type, appointment_id, visit_prior_appointment_id
    FROM clinic_visits v WHERE v.id = COALESCE(NEW.visit_id, OLD.visit_id);
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

CREATE CONSTRAINT TRIGGER ct_visit_initial_reception_type
    AFTER INSERT OR UPDATE ON visit_initial_receptions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION enforce_initial_reception_type();
