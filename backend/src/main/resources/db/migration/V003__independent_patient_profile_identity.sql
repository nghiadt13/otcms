-- V001 is immutable. Existing account-linked profiles receive independent IDs;
-- account linkage remains nullable and unique for registered patients.
ALTER TABLE patient_profiles
    ADD COLUMN id UUID DEFAULT gen_random_uuid();

UPDATE patient_profiles
SET id = gen_random_uuid()
WHERE id IS NULL;

ALTER TABLE patient_profiles
    DROP CONSTRAINT patient_profiles_pkey;

ALTER TABLE patient_profiles
    ALTER COLUMN id SET NOT NULL,
    ALTER COLUMN account_id DROP NOT NULL;

ALTER TABLE patient_profiles
    ADD CONSTRAINT patient_profiles_pkey PRIMARY KEY (id),
    ADD CONSTRAINT uk_patient_profiles_account_id UNIQUE (account_id);
