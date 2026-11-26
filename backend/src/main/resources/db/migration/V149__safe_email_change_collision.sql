CREATE OR REPLACE FUNCTION update_verified_account_email(
    p_account_id UUID,
    p_email TEXT,
    p_verified_at TIMESTAMPTZ
) RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
    violated_constraint TEXT;
BEGIN
    UPDATE accounts
    SET normalized_email = p_email,
        email_verified_at = p_verified_at,
        updated_at = p_verified_at
    WHERE id = p_account_id;

    IF FOUND THEN
        RETURN 'UPDATED';
    END IF;
    RETURN 'NOT_FOUND';
EXCEPTION
    WHEN unique_violation THEN
        GET STACKED DIAGNOSTICS violated_constraint = CONSTRAINT_NAME;
        IF violated_constraint = 'uk_accounts_normalized_email' THEN
            RETURN 'COLLISION';
        END IF;
        RAISE;
END;
$$;
