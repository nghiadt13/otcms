CREATE TABLE clinic_visits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_profile_id UUID NOT NULL REFERENCES patient_profiles(id) ON DELETE RESTRICT,
    source VARCHAR(16) NOT NULL,
    originating_appointment_id UUID,
    prior_appointment_id UUID,
    arrived_at TIMESTAMPTZ NOT NULL,
    checked_in_at TIMESTAMPTZ NOT NULL,
    created_by_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_clinic_visits_source CHECK (source IN ('SCHEDULED', 'WALK_IN')),
    CONSTRAINT ck_clinic_visits_source_link CHECK (
        (source = 'SCHEDULED' AND originating_appointment_id IS NOT NULL AND prior_appointment_id IS NULL)
        OR (source = 'WALK_IN' AND originating_appointment_id IS NULL)
    ),
    CONSTRAINT ck_clinic_visits_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_clinic_visits_check_in_time CHECK (checked_in_at >= arrived_at)
);

CREATE UNIQUE INDEX uk_clinic_visits_originating_appointment
    ON clinic_visits (originating_appointment_id)
    WHERE originating_appointment_id IS NOT NULL;

CREATE INDEX ix_clinic_visits_patient_created ON clinic_visits (patient_profile_id, created_at DESC, id);

CREATE TABLE recorded_appointment_arrivals (
    appointment_id UUID PRIMARY KEY,
    patient_profile_id UUID NOT NULL REFERENCES patient_profiles(id) ON DELETE RESTRICT,
    receptionist_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    arrived_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE visit_initial_receptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    visit_id UUID NOT NULL UNIQUE REFERENCES clinic_visits(id) ON DELETE RESTRICT,
    receptionist_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    reception_type VARCHAR(24) NOT NULL,
    arrived_at TIMESTAMPTZ NOT NULL,
    checked_in_at TIMESTAMPTZ NOT NULL,
    prior_appointment_id UUID,
    CONSTRAINT ck_visit_initial_reception_type CHECK (reception_type IN ('APPOINTMENT_CHECK_IN', 'WALK_IN'))
);

CREATE TABLE walk_in_reception_replays (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receptionist_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    visit_id UUID NOT NULL UNIQUE REFERENCES clinic_visits(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_walk_in_reception_replay UNIQUE (receptionist_account_id, idempotency_key),
    CONSTRAINT ck_walk_in_replay_key CHECK (length(trim(idempotency_key)) BETWEEN 1 AND 128)
);

CREATE TABLE visit_care_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    visit_id UUID NOT NULL REFERENCES clinic_visits(id) ON DELETE RESTRICT,
    doctor_profile_id UUID NOT NULL REFERENCES doctor_profiles(id) ON DELETE RESTRICT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at TIMESTAMPTZ,
    CONSTRAINT ck_visit_care_assignment_interval CHECK (ends_at > starts_at),
    CONSTRAINT ck_visit_care_assignment_status CHECK (status IN ('RESERVED', 'IN_PROGRESS', 'COMPLETED', 'RELEASED')),
    CONSTRAINT ck_visit_care_assignment_release CHECK ((status = 'RELEASED') = (released_at IS NOT NULL))
);

CREATE INDEX ix_visit_care_assignments_doctor_active
    ON visit_care_assignments (doctor_profile_id, starts_at, ends_at)
    WHERE status IN ('RESERVED', 'IN_PROGRESS');
CREATE INDEX ix_visit_care_assignments_visit ON visit_care_assignments (visit_id, assigned_at, id);

CREATE TABLE visit_clinical_examinations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    visit_id UUID NOT NULL REFERENCES clinic_visits(id) ON DELETE RESTRICT,
    doctor_profile_id UUID NOT NULL REFERENCES doctor_profiles(id) ON DELETE RESTRICT,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    follow_up_indicated BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_visit_exam_doctor UNIQUE (visit_id, doctor_profile_id),
    CONSTRAINT ck_visit_exam_completion CHECK (completed = (completed_at IS NOT NULL)),
    CONSTRAINT ck_visit_exam_follow_up CHECK (NOT follow_up_indicated OR completed)
);

CREATE INDEX ix_visit_clinical_exams_follow_up
    ON visit_clinical_examinations (visit_id, doctor_profile_id)
    WHERE completed AND follow_up_indicated;
