CREATE TABLE patient_medical_histories (
    patient_profile_id UUID PRIMARY KEY REFERENCES patient_profiles(id) ON DELETE RESTRICT,
    conditions VARCHAR(2000) NOT NULL DEFAULT '',
    allergies VARCHAR(2000) NOT NULL DEFAULT '',
    procedures VARCHAR(2000) NOT NULL DEFAULT '',
    injuries VARCHAR(2000) NOT NULL DEFAULT '',
    orthopedic_history VARCHAR(2000) NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT ck_patient_medical_histories_version CHECK (version >= 1)
);

CREATE INDEX ix_patient_medical_histories_updated_by
    ON patient_medical_histories(updated_by_account_id);
