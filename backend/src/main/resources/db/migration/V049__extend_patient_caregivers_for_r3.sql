-- R3 accountless dependents may have no contact phone. Existing API validation is retained.
ALTER TABLE patient_profiles
    ALTER COLUMN phone_canonical DROP NOT NULL,
    ALTER COLUMN phone_owner_type DROP NOT NULL,
    ADD CONSTRAINT ck_patient_profiles_contact_pair CHECK (
        account_id IS NULL OR (phone_canonical IS NOT NULL AND phone_owner_type IS NOT NULL)
    );

ALTER TABLE patient_caregivers
    ALTER COLUMN confirming_account_id DROP NOT NULL,
    ADD COLUMN relationship VARCHAR(16),
    ADD COLUMN verification_method VARCHAR(24),
    ADD COLUMN pending_reason VARCHAR(16),
    ADD COLUMN verified_by_account_id UUID REFERENCES accounts(id),
    ADD COLUMN verified_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_care_r3_relationship CHECK (relationship IN ('PARENT', 'CHILD', 'SPOUSE', 'SIBLING')),
    ADD CONSTRAINT ck_care_r3_method CHECK (verification_method IN ('EMAIL_OTP', 'COUNTER_CHECK', 'CREATED_WITHOUT_OTP')),
    ADD CONSTRAINT ck_care_r3_pending CHECK (pending_reason IN ('OTP', 'COUNTER')),
    ADD CONSTRAINT ck_care_r3_evidence CHECK (relationship IS NULL OR (
        (verified_at IS NULL OR verified_at >= requested_at)
        AND (verification_method IS DISTINCT FROM 'EMAIL_OTP' OR confirming_account_id IS NOT NULL)
        AND (
            (state = 'REQUESTED' AND pending_reason IS NOT NULL
                AND verification_method IS NULL AND verified_by_account_id IS NULL AND verified_at IS NULL)
            OR (state = 'CONFIRMED' AND pending_reason IS NULL AND verification_method IS NOT NULL
                AND verified_by_account_id IS NOT NULL AND verified_at IS NOT NULL
                AND confirmed_at IS NOT NULL AND verified_at = confirmed_at)
            OR (state = 'REVOKED' AND pending_reason IS NULL AND (
                (confirmed_at IS NULL AND verification_method IS NULL AND verified_by_account_id IS NULL AND verified_at IS NULL)
                OR (confirmed_at IS NOT NULL AND verification_method IS NOT NULL AND verified_by_account_id IS NOT NULL
                    AND verified_at IS NOT NULL AND verified_at = confirmed_at)
            ))
        )
    ));

-- Legacy rows stay legacy; never invent relationship or verification evidence.
-- Retain the original history trigger, including its prohibition on no-op state updates.
CREATE FUNCTION protect_patient_caregiver_r3_metadata() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.relationship IS NULL THEN
            RAISE EXCEPTION 'New care links require R3 metadata' USING ERRCODE = '23514';
        END IF;
    ELSE
        IF NEW.relationship IS DISTINCT FROM OLD.relationship THEN
            RAISE EXCEPTION 'Care relationship must be retained' USING ERRCODE = '23514';
        END IF;
        IF (OLD.verified_at IS NOT NULL OR OLD.relationship IS NULL)
            AND (NEW.verification_method, NEW.verified_by_account_id, NEW.verified_at)
                IS DISTINCT FROM (OLD.verification_method, OLD.verified_by_account_id, OLD.verified_at) THEN
            RAISE EXCEPTION 'Verification history must be retained' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER protect_patient_caregiver_r3_metadata
    BEFORE INSERT OR UPDATE ON patient_caregivers
    FOR EACH ROW EXECUTE FUNCTION protect_patient_caregiver_r3_metadata();

CREATE TABLE care_link_otp_challenges (
    id UUID PRIMARY KEY,
    link_id UUID NOT NULL REFERENCES patient_caregivers(id),
    recipient_account_id UUID NOT NULL REFERENCES accounts(id),
    otp_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts BETWEEN 0 AND 5),
    consumed_at TIMESTAMPTZ,
    superseded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > created_at),
    CHECK (consumed_at IS NULL OR consumed_at >= created_at),
    CHECK (superseded_at IS NULL OR superseded_at >= created_at)
);
CREATE UNIQUE INDEX uk_care_link_otp_current ON care_link_otp_challenges(link_id)
    WHERE consumed_at IS NULL AND superseded_at IS NULL;
CREATE INDEX ix_care_link_otp_link_created ON care_link_otp_challenges(link_id, created_at DESC);
CREATE INDEX ix_care_link_otp_recipient_created ON care_link_otp_challenges(recipient_account_id, created_at DESC);
CREATE INDEX ix_care_counter_pending ON patient_caregivers(requested_at, id)
    WHERE state = 'REQUESTED' AND pending_reason = 'COUNTER';
