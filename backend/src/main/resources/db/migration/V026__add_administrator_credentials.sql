ALTER TABLE accounts ADD COLUMN username TEXT;
ALTER TABLE accounts ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE accounts ADD CONSTRAINT uk_accounts_username UNIQUE (username);
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_username_not_blank
    CHECK (username IS NULL OR username ~ '[^[:space:]]');
-- Existing email-based staff/owner/admin accounts remain valid with username NULL.
-- Only newly issued Administrator credentials require a username; Patients do not.
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_issued_admin_username
    CHECK (account_type <> 'ADMIN' OR NOT must_change_password OR username IS NOT NULL);

-- Username-based Administrator accounts do not require a fabricated email address.
ALTER TABLE accounts ALTER COLUMN normalized_email DROP NOT NULL;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_email_required
    CHECK (normalized_email IS NOT NULL OR (account_type = 'ADMIN' AND username IS NOT NULL));
