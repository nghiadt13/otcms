-- Name captured at patient self-registration. patient_profiles cannot be created at that
-- step (phone, date of birth and gender are required there), so the name lives on the account
-- until a profile exists. Nullable: staff, demo and Google-created accounts do not set it.
ALTER TABLE accounts ADD COLUMN full_name VARCHAR(100);
