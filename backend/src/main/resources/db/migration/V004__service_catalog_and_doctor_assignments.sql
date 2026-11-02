CREATE TABLE clinic_services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(160) NOT NULL,
    description TEXT,
    duration_minutes INTEGER NOT NULL,
    buffer_minutes INTEGER NOT NULL DEFAULT 0,
    online_bookable BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_clinic_services_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_clinic_services_buffer CHECK (buffer_minutes >= 0)
);

CREATE INDEX ix_clinic_services_active_online ON clinic_services (active, online_bookable, name);

CREATE TABLE doctor_service_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_profile_id UUID NOT NULL,
    service_id UUID NOT NULL REFERENCES clinic_services(id) ON DELETE RESTRICT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_doctor_service_assignment UNIQUE (doctor_profile_id, service_id)
);

CREATE INDEX ix_doctor_service_active_service ON doctor_service_assignments (service_id, doctor_profile_id)
    WHERE active;
