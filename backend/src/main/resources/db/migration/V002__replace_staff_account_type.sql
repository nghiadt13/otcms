DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM accounts WHERE account_type = 'STAFF') THEN
        RAISE EXCEPTION 'Cannot retire legacy STAFF account type: map every existing account before applying V002';
    END IF;
END;
$$;

ALTER TABLE accounts DROP CONSTRAINT ck_accounts_type;
ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_type
    CHECK (account_type IN ('PATIENT', 'RECEPTIONIST', 'DOCTOR', 'OWNER', 'ADMIN'));
