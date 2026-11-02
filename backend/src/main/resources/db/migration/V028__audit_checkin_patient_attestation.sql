-- Preserve historical audit rows as unknown while requiring identity attestation
-- on every new scheduled check-in audit.
ALTER TABLE appointment_audits
    ADD COLUMN verified_patient_profile_id UUID,
    ADD CONSTRAINT fk_appointment_audits_verified_patient
        FOREIGN KEY (appointment_id, verified_patient_profile_id)
        REFERENCES appointments (id, patient_profile_id) ON DELETE RESTRICT
        NOT VALID,
    ADD CONSTRAINT ck_appointment_audits_checkin_attestation
        CHECK (
            CASE
                WHEN action = 'STATUS_CHANGED'
                     AND old_status = 'CONFIRMED'
                     AND new_status = 'CHECKED_IN'
                THEN verified_patient_profile_id IS NOT NULL AND actor_type = 'RECEPTIONIST'
                ELSE verified_patient_profile_id IS NULL
            END
        ) NOT VALID;
