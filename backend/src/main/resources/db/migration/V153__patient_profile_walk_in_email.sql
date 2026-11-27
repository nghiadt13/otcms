-- UC-10 / UC-11 / BR-15: optional email & email owner on walk-in patient profiles
-- for automatic linkage when the patient registers and verifies a Patient account.
ALTER TABLE patient_profiles
    ADD COLUMN email VARCHAR(255),
    ADD COLUMN email_owner_type VARCHAR(16);

ALTER TABLE patient_profiles
    ADD CONSTRAINT ck_patient_profiles_email_pair CHECK (
        (email IS NULL AND email_owner_type IS NULL)
        OR (email IS NOT NULL AND email_owner_type IS NOT NULL AND email_owner_type IN ('PATIENT', 'RELATIVE'))
    ),
    ADD CONSTRAINT ck_patient_profiles_email_normalized CHECK (
        email IS NULL OR email = lower(btrim(email))
    );

CREATE INDEX ix_patient_profiles_unlinked_own_email
    ON patient_profiles (email)
    WHERE account_id IS NULL AND email IS NOT NULL AND email_owner_type = 'PATIENT';
