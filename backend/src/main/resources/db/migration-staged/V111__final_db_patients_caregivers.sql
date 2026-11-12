-- final-db phase 06: patient_profiles absorbs patient_medical_histories; verification_codes absorbs
-- care_link_otp_challenges (purpose CARE_LINK) and dependent_add_requests (purpose DEPENDENT_ADD).
-- Runs after V110 (verification_codes exists). Order: add columns and constraints -> copy -> drop old tables.

-- 1. patient_profiles: medical history columns. medical_history_version is separate from version,
--    which is the optimistic lock of the profile itself.
ALTER TABLE patient_profiles
    ADD COLUMN conditions varchar(2000) NOT NULL DEFAULT '',
    ADD COLUMN allergies varchar(2000) NOT NULL DEFAULT '',
    ADD COLUMN procedures varchar(2000) NOT NULL DEFAULT '',
    ADD COLUMN injuries varchar(2000) NOT NULL DEFAULT '',
    ADD COLUMN orthopedic_history varchar(2000) NOT NULL DEFAULT '',
    ADD COLUMN medical_history_updated_at timestamptz,
    ADD COLUMN medical_history_updated_by uuid,
    ADD COLUMN medical_history_version bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_patient_profiles_medical_history_version CHECK (medical_history_version >= 0),
    ADD CONSTRAINT ck_patient_profiles_medical_history_state CHECK (
        ((medical_history_updated_at IS NULL) = (medical_history_version = 0))
        AND ((medical_history_updated_at IS NULL) = (medical_history_updated_by IS NULL))),
    ADD CONSTRAINT fk_patient_profiles_medical_history_updated_by
        FOREIGN KEY (medical_history_updated_by) REFERENCES accounts(id) ON DELETE RESTRICT;
CREATE INDEX ix_patient_profiles_medical_history_updated_by ON patient_profiles (medical_history_updated_by);

-- Copy the existing histories. Only the history columns change; patient_profiles.version is untouched.
UPDATE patient_profiles p
SET conditions = h.conditions,
    allergies = h.allergies,
    procedures = h.procedures,
    injuries = h.injuries,
    orthopedic_history = h.orthopedic_history,
    medical_history_updated_at = h.updated_at,
    medical_history_updated_by = h.updated_by_account_id,
    medical_history_version = h.version
FROM patient_medical_histories h
WHERE h.patient_profile_id = p.id;

-- 2. verification_codes: payload (DEPENDENT_ADD only) and the CARE_LINK and DEPENDENT_ADD rows.
ALTER TABLE verification_codes
    ADD CONSTRAINT ck_verification_codes_payload_purpose CHECK (payload IS NULL OR purpose = 'DEPENDENT_ADD'),
    ADD CONSTRAINT ck_verification_codes_payload_open CHECK (payload IS NULL OR consumed_at IS NULL);
CREATE INDEX ix_verification_codes_payload_expiry ON verification_codes (expires_at) WHERE payload IS NOT NULL;

INSERT INTO verification_codes (id, account_id, purpose, code_hash, link_id, failed_attempts,
                                expires_at, consumed_at, superseded_at, created_at)
SELECT id, recipient_account_id, 'CARE_LINK', otp_hash, link_id, failed_attempts,
       expires_at, consumed_at, superseded_at, created_at
FROM care_link_otp_challenges;

-- Payload stays until the code is used or replaced. It is not cleared at expiry, so resend still works.
-- If one requester has two open rows, the INSERT fails on the V110 unique index (phase 06 pre-check).
INSERT INTO verification_codes (id, account_id, purpose, code_hash, payload, failed_attempts,
                                expires_at, consumed_at, superseded_at, created_at)
SELECT id, requester_account_id, 'DEPENDENT_ADD', otp_hash,
       CASE WHEN consumed_at IS NULL AND superseded_at IS NULL THEN
            jsonb_build_object('fullName', full_name, 'dateOfBirth', date_of_birth, 'gender', gender,
                               'nationalId', national_id, 'identityDocumentType', identity_document_type,
                               'relationship', relationship)
       END,
       failed_attempts, expires_at, consumed_at, superseded_at, created_at
FROM dependent_add_requests;

-- 3. Triggers and functions: none is attached to the tables dropped below (checked on V108).

-- 4. Drop the old tables.
DROP TABLE patient_medical_histories;
DROP TABLE care_link_otp_challenges;
DROP TABLE dependent_add_requests;
