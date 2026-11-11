-- UC08 also resets credentials of existing email-based Administrators without a username.
-- UC07 still requires username at its request and service boundaries.
ALTER TABLE accounts DROP CONSTRAINT ck_accounts_issued_admin_username;
