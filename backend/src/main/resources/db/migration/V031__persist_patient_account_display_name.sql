ALTER TABLE accounts
    ADD COLUMN display_name VARCHAR(100);

UPDATE accounts account
SET display_name = profile.full_name
FROM patient_profiles profile
WHERE profile.account_id = account.id
  AND account.account_type = 'PATIENT';

ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_display_name
        CHECK (display_name IS NULL OR length(trim(display_name)) BETWEEN 2 AND 100);
