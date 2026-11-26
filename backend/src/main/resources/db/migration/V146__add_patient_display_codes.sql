CREATE SEQUENCE patient_profile_code_seq AS BIGINT START WITH 1;

ALTER TABLE patient_profiles
    ADD COLUMN patient_code VARCHAR(24);

WITH numbered_profiles AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS sequence_number
    FROM patient_profiles
)
UPDATE patient_profiles profile
SET patient_code = 'BN-' || LPAD(numbered_profiles.sequence_number::TEXT, 6, '0')
FROM numbered_profiles
WHERE profile.id = numbered_profiles.id;

SELECT setval(
    'patient_profile_code_seq',
    COALESCE(MAX(SUBSTRING(patient_code FROM 4)::BIGINT), 1),
    COUNT(*) > 0
)
FROM patient_profiles;

ALTER SEQUENCE patient_profile_code_seq OWNED BY patient_profiles.patient_code;

ALTER TABLE patient_profiles
    ALTER COLUMN patient_code SET DEFAULT
        ('BN-' || LPAD(NEXTVAL('patient_profile_code_seq')::TEXT, 6, '0')),
    ALTER COLUMN patient_code SET NOT NULL,
    ADD CONSTRAINT uk_patient_profiles_patient_code UNIQUE (patient_code),
    ADD CONSTRAINT ck_patient_profiles_patient_code_format
        CHECK (patient_code ~ '^BN-[0-9]{6,}$');
