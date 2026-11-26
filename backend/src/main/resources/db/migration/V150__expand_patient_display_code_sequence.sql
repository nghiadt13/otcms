CREATE OR REPLACE FUNCTION next_patient_profile_code() RETURNS VARCHAR(24)
LANGUAGE plpgsql
VOLATILE
AS $$
DECLARE
    code_number BIGINT := NEXTVAL('patient_profile_code_seq');
BEGIN
    IF code_number < 1000000 THEN
        RETURN 'BN-' || LPAD(code_number::TEXT, 6, '0');
    END IF;

    RETURN 'BN-' || code_number::TEXT;
END;
$$;

ALTER TABLE patient_profiles
    ALTER COLUMN patient_code SET DEFAULT next_patient_profile_code();
