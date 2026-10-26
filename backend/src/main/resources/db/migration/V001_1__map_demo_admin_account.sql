-- Target-specific bridge for the approved Supabase demo account.
-- V001 only permits PATIENT/STAFF; V002 retires STAFF. Keep this migration
-- between V001 and V002 so the one approved account can be mapped safely.
DO $$
DECLARE
    target_account_id CONSTANT UUID := '9b414dac-9266-42f0-976c-821a41793d68';
    target_exists BOOLEAN;
    changed_rows INTEGER;
BEGIN
    SELECT EXISTS (SELECT 1 FROM accounts WHERE id = target_account_id)
      INTO target_exists;

    -- Local clean schemas have no demo account; V002 will establish the final
    -- canonical constraint as usual.
    IF NOT target_exists THEN
        RETURN;
    END IF;

    ALTER TABLE accounts DROP CONSTRAINT ck_accounts_type;
    ALTER TABLE accounts
        ADD CONSTRAINT ck_accounts_type
        CHECK (account_type IN ('PATIENT', 'STAFF', 'ADMIN'));

    UPDATE accounts
       SET account_type = 'ADMIN'
     WHERE id = target_account_id
       AND account_type = 'STAFF';
    GET DIAGNOSTICS changed_rows = ROW_COUNT;

    IF changed_rows <> 1 THEN
        RAISE EXCEPTION 'Approved demo account must exist with STAFF role before mapping; changed rows: %', changed_rows;
    END IF;

    IF EXISTS (SELECT 1 FROM accounts WHERE account_type = 'STAFF') THEN
        RAISE EXCEPTION 'Unexpected STAFF accounts remain after mapping approved demo account';
    END IF;
END;
$$;
