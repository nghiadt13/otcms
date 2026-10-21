-- Patient-owned consent lifecycle. Historical revoked rows are retained.
CREATE TABLE patient_care_relationships (
    id UUID PRIMARY KEY,
    requester_account_id UUID NOT NULL REFERENCES accounts(id),
    dependent_patient_profile_id UUID NOT NULL REFERENCES patient_profiles(id),
    confirming_account_id UUID NOT NULL REFERENCES accounts(id),
    state VARCHAR(16) NOT NULL CHECK (state IN ('REQUESTED', 'CONFIRMED', 'REVOKED')),
    requested_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by_account_id UUID REFERENCES accounts(id),
    CONSTRAINT ck_care_relationship_distinct_accounts CHECK (requester_account_id <> confirming_account_id),
    CONSTRAINT ck_care_relationship_state_evidence CHECK (
        (state = 'REQUESTED' AND confirmed_at IS NULL AND revoked_at IS NULL AND revoked_by_account_id IS NULL)
        OR (state = 'CONFIRMED' AND confirmed_at IS NOT NULL AND revoked_at IS NULL AND revoked_by_account_id IS NULL)
        OR (state = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_by_account_id IS NOT NULL)
    ),
    CONSTRAINT ck_care_relationship_chronology CHECK (
        (confirmed_at IS NULL OR confirmed_at >= requested_at)
        AND (revoked_at IS NULL OR revoked_at >= COALESCE(confirmed_at, requested_at))
    )
);
CREATE UNIQUE INDEX uk_patient_care_relationship_live
    ON patient_care_relationships(requester_account_id, dependent_patient_profile_id)
    WHERE state IN ('REQUESTED', 'CONFIRMED');
CREATE INDEX ix_patient_care_relationship_dependent ON patient_care_relationships(dependent_patient_profile_id);

-- Protect relationship identity, lifecycle evidence and retained history even from accidental owner writes.
CREATE FUNCTION protect_patient_care_relationship_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Care relationship history must be retained';
    END IF;
    IF (NEW.id, NEW.requester_account_id, NEW.dependent_patient_profile_id, NEW.confirming_account_id, NEW.requested_at)
        IS DISTINCT FROM
       (OLD.id, OLD.requester_account_id, OLD.dependent_patient_profile_id, OLD.confirming_account_id, OLD.requested_at) THEN
        RAISE EXCEPTION 'Care relationship identity must be retained';
    END IF;
    IF OLD.state = 'REVOKED'
       OR (OLD.state = 'CONFIRMED' AND NEW.state <> 'REVOKED')
       OR (OLD.state = 'REQUESTED' AND NEW.state NOT IN ('CONFIRMED', 'REVOKED')) THEN
        RAISE EXCEPTION 'Invalid care relationship transition';
    END IF;
    IF OLD.state = 'CONFIRMED' AND NEW.confirmed_at IS DISTINCT FROM OLD.confirmed_at THEN
        RAISE EXCEPTION 'Confirmation evidence must be retained';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER protect_patient_care_relationship_history
    BEFORE UPDATE OR DELETE ON patient_care_relationships
    FOR EACH ROW EXECUTE FUNCTION protect_patient_care_relationship_history();
