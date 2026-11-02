-- Preserve V045 data. Reject oversized legacy identifiers before narrowing the column.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM patient_profiles WHERE char_length(citizen_id) > 20) THEN
        RAISE EXCEPTION 'KAN-24 migration cannot narrow citizen_id: legacy values exceed 20 characters; reconcile them before retrying'
            USING ERRCODE = '22001';
    END IF;
END;
$$;

ALTER TABLE patient_profiles ALTER COLUMN address TYPE TEXT;
ALTER TABLE patient_profiles RENAME COLUMN citizen_id TO national_id;
ALTER TABLE patient_profiles
    ALTER COLUMN national_id TYPE VARCHAR(20),
    ADD COLUMN identity_document_type VARCHAR(16),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_patient_profiles_identity_document_type
        CHECK (identity_document_type IN ('CCCD', 'PERSONAL_ID', 'PASSPORT'));

CREATE INDEX ix_patient_profiles_national_id ON patient_profiles (national_id);
