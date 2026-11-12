-- final-db phase 04: appointment_arrivals and appointment_checkin_requests fold into appointments
-- (arrival_* and checkin_* columns); appointment_audits moves to audit_logs (source = 'appointment_audits').
-- Order: columns, constraints (before the copy, so a bad copied row aborts this file), copy, drop triggers
-- and functions, drop tables. Triggers, the visit link check and the EXCLUDE constraint stay as they are.

-- First-arrival record and first check-in replay: all nullable, grouped by the all-or-none CHECKs below.
ALTER TABLE appointments
    ADD COLUMN arrived_at                   timestamptz  NULL,
    ADD COLUMN arrival_source               varchar(24)  NULL,
    ADD COLUMN arrival_recorded_at          timestamptz  NULL,
    ADD COLUMN arrival_recorded_by          uuid         NULL,
    ADD COLUMN arrival_reason               varchar(500) NULL,
    ADD COLUMN checkin_idempotency_key      varchar(128) NULL,
    ADD COLUMN checkin_request_fingerprint  varchar(64)  NULL,
    ADD COLUMN checkin_actor_account_id     uuid         NULL,
    ADD COLUMN checkin_checked_in_at        timestamptz  NULL;

ALTER TABLE appointments
    ADD CONSTRAINT ck_appointments_arrival_all_or_none CHECK (
        (arrived_at IS NULL AND arrival_source IS NULL AND arrival_recorded_at IS NULL AND arrival_recorded_by IS NULL)
        OR (arrived_at IS NOT NULL AND arrival_source IS NOT NULL AND arrival_recorded_at IS NOT NULL AND arrival_recorded_by IS NOT NULL)
    ),
    ADD CONSTRAINT ck_appointments_arrival_source CHECK (
        arrival_source IS NULL OR arrival_source IN ('PATIENT_QR', 'RECEPTIONIST', 'LEGACY_RECEPTIONIST')
    ),
    -- Same rule as V108 ck_appointment_arrivals_actor_reason: only RECEPTIONIST arrivals carry a reason.
    ADD CONSTRAINT ck_appointments_arrival_reason CHECK (
        (arrived_at IS NULL AND arrival_source IS NULL AND arrival_reason IS NULL)
        OR (arrived_at IS NOT NULL AND arrival_source IN ('PATIENT_QR', 'LEGACY_RECEPTIONIST') AND arrival_reason IS NULL)
        OR (arrived_at IS NOT NULL AND arrival_source = 'RECEPTIONIST' AND arrival_reason IS NOT NULL
            AND length(trim(both from arrival_reason)) BETWEEN 1 AND 500)
    ),
    ADD CONSTRAINT ck_appointments_checkin_all_or_none CHECK (
        (checkin_idempotency_key IS NULL AND checkin_request_fingerprint IS NULL
            AND checkin_actor_account_id IS NULL AND checkin_checked_in_at IS NULL)
        OR (checkin_idempotency_key IS NOT NULL AND checkin_request_fingerprint IS NOT NULL
            AND checkin_actor_account_id IS NOT NULL AND checkin_checked_in_at IS NOT NULL)
    ),
    ADD CONSTRAINT ck_appointments_checkin_key CHECK (
        checkin_idempotency_key IS NULL OR length(trim(both from checkin_idempotency_key)) BETWEEN 1 AND 128
    ),
    ADD CONSTRAINT ck_appointments_checkin_fingerprint CHECK (
        checkin_request_fingerprint IS NULL OR checkin_request_fingerprint ~ '^[0-9a-fA-F]{64}$'
    ),
    -- Scope is (actor, key), as decided 13/11. The leading column also serves the FK to accounts below.
    ADD CONSTRAINT uk_appointments_checkin_actor_key UNIQUE (checkin_actor_account_id, checkin_idempotency_key),
    ADD CONSTRAINT fk_appointments_arrival_recorded_by FOREIGN KEY (arrival_recorded_by)
        REFERENCES accounts(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_appointments_checkin_actor FOREIGN KEY (checkin_actor_account_id)
        REFERENCES accounts(id) ON DELETE RESTRICT;

CREATE INDEX ix_appointments_arrived_at ON appointments (arrived_at, id) WHERE arrived_at IS NOT NULL;
CREATE INDEX ix_appointments_arrival_recorded_by ON appointments (arrival_recorded_by) WHERE arrival_recorded_by IS NOT NULL;

-- Copy demo rows. Arrivals are one per appointment; check-in replays keep the earliest CHECK_IN per appointment.
UPDATE appointments a
SET arrived_at = r.arrived_at,
    arrival_source = r.source,
    arrival_recorded_at = r.recorded_at,
    arrival_recorded_by = r.actor_account_id,
    arrival_reason = r.reason
FROM appointment_arrivals r
WHERE r.appointment_id = a.id;

UPDATE appointments a
SET checkin_idempotency_key = c.idempotency_key,
    checkin_request_fingerprint = c.request_fingerprint,
    checkin_actor_account_id = c.actor_account_id,
    checkin_checked_in_at = c.result_checked_in_at
FROM (
    SELECT DISTINCT ON (appointment_id) appointment_id, idempotency_key, request_fingerprint,
           actor_account_id, result_checked_in_at
    FROM appointment_checkin_requests
    WHERE command = 'CHECK_IN'
    ORDER BY appointment_id, created_at, id
) c
WHERE c.appointment_id = a.id;

-- Audit rows keep their id. State maps follow phase 04 section 4.4 (before is NULL for CREATED; null keys dropped).
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, actor_type, before_state, after_state,
                        metadata, correlation_id)
SELECT a.id, a.occurred_at, 'appointment_audits', 'APPOINTMENT', a.action, 'APPOINTMENT',
       a.appointment_id, a.appointment_id::text, true, a.actor_account_id, a.actor_type,
       CASE WHEN a.old_status IS NOT NULL THEN
           jsonb_build_object('status', a.old_status, 'doctorProfileId', a.old_doctor_profile_id,
                              'startTime', a.old_start_time, 'endTime', a.old_end_time)
       END,
       jsonb_strip_nulls(jsonb_build_object('status', a.new_status, 'doctorProfileId', a.new_doctor_profile_id,
                              'startTime', a.new_start_time, 'endTime', a.new_end_time,
                              'visitId', a.linked_visit_id, 'sourceVisitId', a.source_visit_id)),
       jsonb_strip_nulls(jsonb_build_object('reason', a.reason,
                              'verifiedPatientProfileId', a.verified_patient_profile_id)),
       a.correlation_id
FROM appointment_audits a;

-- Drop the append-only triggers first (they use the shared function), then the function, then the tables.
-- ck_appointment_audits_checkin_attestation and the old replay unique key go with their tables.
DROP TRIGGER tr_appointment_audits_immutable ON appointment_audits;
DROP TRIGGER tr_appointment_command_replays_immutable ON appointment_checkin_requests;
DROP FUNCTION reject_append_only_appointment_row_mutation();

DROP TABLE appointment_checkin_requests;
DROP TABLE appointment_arrivals;
DROP TABLE appointment_audits;
