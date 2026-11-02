ALTER TABLE appointment_arrivals
    RENAME COLUMN receptionist_account_id TO actor_account_id;

ALTER TABLE appointment_arrivals
    ALTER COLUMN actor_account_id DROP NOT NULL,
    ADD COLUMN source VARCHAR(24),
    ADD COLUMN reason VARCHAR(500);

UPDATE appointment_arrivals
SET source = 'LEGACY_RECEPTIONIST'
WHERE source IS NULL;

ALTER TABLE appointment_arrivals
    ALTER COLUMN source SET NOT NULL,
    ADD CONSTRAINT ck_appointment_arrivals_source
        CHECK (source IN ('PATIENT_QR', 'RECEPTIONIST', 'LEGACY_RECEPTIONIST')),
    ADD CONSTRAINT ck_appointment_arrivals_actor_reason
        CHECK (
            (source = 'PATIENT_QR' AND actor_account_id IS NOT NULL AND reason IS NULL)
            OR (source = 'RECEPTIONIST' AND actor_account_id IS NOT NULL AND reason IS NOT NULL
                AND length(trim(reason)) BETWEEN 1 AND 500)
            OR (source = 'LEGACY_RECEPTIONIST' AND actor_account_id IS NOT NULL AND reason IS NULL)
        );

CREATE INDEX ix_appointment_arrivals_arrived
    ON appointment_arrivals (arrived_at, appointment_id);
