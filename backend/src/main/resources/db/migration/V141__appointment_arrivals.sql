-- V141: tách nhóm đến/check-in khỏi appointments sang appointment_arrivals (1 - 0..1).
-- Dòng nguồn: appointments có arrived_at hoặc checkin_checked_in_at.
-- enforce_appointment_visit_link() không đọc các cột này (đã kiểm tra); visits.arrived_at là cột riêng của visits, không đụng.

CREATE TABLE appointment_arrivals (
    appointment_id           uuid        PRIMARY KEY REFERENCES appointments (id) ON DELETE CASCADE,
    arrived_at               timestamptz,
    arrival_source           varchar(24),
    arrival_recorded_at      timestamptz,
    arrival_recorded_by      uuid        REFERENCES accounts (id) ON DELETE RESTRICT,
    arrival_reason           varchar(500),
    checkin_actor_account_id uuid        REFERENCES accounts (id) ON DELETE RESTRICT,
    checkin_checked_in_at    timestamptz,
    CONSTRAINT ck_appointment_arrivals_all_or_none CHECK (
        (arrived_at IS NULL AND arrival_source IS NULL AND arrival_recorded_at IS NULL AND arrival_recorded_by IS NULL)
        OR (arrived_at IS NOT NULL AND arrival_source IS NOT NULL AND arrival_recorded_at IS NOT NULL AND arrival_recorded_by IS NOT NULL)),
    CONSTRAINT ck_appointment_arrivals_source CHECK (
        arrival_source IS NULL OR arrival_source IN ('PATIENT_QR', 'RECEPTIONIST', 'LEGACY_RECEPTIONIST')),
    CONSTRAINT ck_appointment_arrivals_reason CHECK (
        (arrived_at IS NULL AND arrival_source IS NULL AND arrival_reason IS NULL)
        OR (arrived_at IS NOT NULL AND arrival_source IN ('PATIENT_QR', 'LEGACY_RECEPTIONIST') AND arrival_reason IS NULL)
        OR (arrived_at IS NOT NULL AND arrival_source = 'RECEPTIONIST' AND arrival_reason IS NOT NULL
            AND length(btrim(arrival_reason)) BETWEEN 1 AND 500)),
    CONSTRAINT ck_appointment_arrivals_checkin_all_or_none CHECK (
        (checkin_actor_account_id IS NULL) = (checkin_checked_in_at IS NULL))
);

INSERT INTO appointment_arrivals (appointment_id, arrived_at, arrival_source, arrival_recorded_at, arrival_recorded_by,
    arrival_reason, checkin_actor_account_id, checkin_checked_in_at)
SELECT id, arrived_at, arrival_source, arrival_recorded_at, arrival_recorded_by, arrival_reason,
       checkin_actor_account_id, checkin_checked_in_at
FROM appointments
WHERE arrived_at IS NOT NULL OR checkin_checked_in_at IS NOT NULL;

DO $$
DECLARE expected bigint; actual bigint;
BEGIN
    SELECT count(*) INTO expected FROM appointments WHERE arrived_at IS NOT NULL OR checkin_checked_in_at IS NOT NULL;
    SELECT count(*) INTO actual FROM appointment_arrivals;
    IF expected <> actual THEN RAISE EXCEPTION 'V141 copy check: % arrivals expected, % copied', expected, actual; END IF;
END $$;

CREATE INDEX ix_appointment_arrivals_arrived_at ON appointment_arrivals (arrived_at, appointment_id) WHERE arrived_at IS NOT NULL;
CREATE INDEX ix_appointment_arrivals_recorded_by ON appointment_arrivals (arrival_recorded_by) WHERE arrival_recorded_by IS NOT NULL;
CREATE INDEX ix_appointment_arrivals_checkin_actor ON appointment_arrivals (checkin_actor_account_id) WHERE checkin_actor_account_id IS NOT NULL;

-- Gỡ constraint/index/FK cũ rồi drop cột.
DROP INDEX ix_appointments_arrived_at;
DROP INDEX ix_appointments_arrival_recorded_by;
ALTER TABLE appointments
    DROP CONSTRAINT ck_appointments_arrival_all_or_none,
    DROP CONSTRAINT ck_appointments_arrival_reason,
    DROP CONSTRAINT ck_appointments_arrival_source,
    DROP CONSTRAINT ck_appointments_checkin_all_or_none,
    DROP CONSTRAINT fk_appointments_arrival_recorded_by,
    DROP CONSTRAINT fk_appointments_checkin_actor;
ALTER TABLE appointments
    DROP COLUMN arrived_at,
    DROP COLUMN arrival_source,
    DROP COLUMN arrival_recorded_at,
    DROP COLUMN arrival_recorded_by,
    DROP COLUMN arrival_reason,
    DROP COLUMN checkin_actor_account_id,
    DROP COLUMN checkin_checked_in_at;
