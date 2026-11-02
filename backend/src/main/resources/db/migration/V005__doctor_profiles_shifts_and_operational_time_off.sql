CREATE TABLE doctor_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL UNIQUE REFERENCES accounts(id) ON DELETE RESTRICT,
    full_name VARCHAR(160) NOT NULL,
    photo_url VARCHAR(2048),
    work_contact VARCHAR(254),
    specialties TEXT NOT NULL DEFAULT '',
    qualifications TEXT NOT NULL DEFAULT '',
    biography TEXT NOT NULL DEFAULT '',
    consultation_information TEXT NOT NULL DEFAULT '',
    publicly_visible BOOLEAN NOT NULL DEFAULT FALSE,
    accepting_new_bookings BOOLEAN NOT NULL DEFAULT FALSE,
    in_active_directory BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_doctor_profile_name CHECK (length(trim(full_name)) > 0)
);

CREATE INDEX ix_doctor_profiles_public_directory
    ON doctor_profiles (full_name, id)
    WHERE publicly_visible AND in_active_directory;

CREATE TABLE doctor_work_shifts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_profile_id UUID NOT NULL REFERENCES doctor_profiles(id) ON DELETE RESTRICT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_doctor_work_shift_interval CHECK (ends_at > starts_at),
    CONSTRAINT ex_doctor_work_shift_no_overlap EXCLUDE USING gist (
        doctor_profile_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    )
);

CREATE INDEX ix_doctor_work_shifts_doctor_start
    ON doctor_work_shifts (doctor_profile_id, starts_at, ends_at);

CREATE TABLE doctor_operational_time_off (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_profile_id UUID NOT NULL REFERENCES doctor_profiles(id) ON DELETE RESTRICT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_doctor_operational_time_off_interval CHECK (ends_at > starts_at)
);

CREATE INDEX ix_doctor_operational_time_off_doctor_interval
    ON doctor_operational_time_off (doctor_profile_id, starts_at, ends_at);
