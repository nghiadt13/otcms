CREATE TABLE visit_audits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    visit_id UUID NOT NULL REFERENCES clinic_visits(id) ON DELETE RESTRICT,
    action VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    CONSTRAINT ck_visit_audits_action CHECK (action = 'WALK_IN_CREATED'),
    CONSTRAINT ck_visit_audits_correlation CHECK (length(trim(correlation_id)) BETWEEN 1 AND 128)
);

CREATE INDEX ix_visit_audits_visit_time ON visit_audits (visit_id, occurred_at, id);
CREATE INDEX ix_visit_audits_actor_time ON visit_audits (actor_account_id, occurred_at DESC, id);
CREATE INDEX ix_visit_audits_correlation ON visit_audits (correlation_id);

CREATE FUNCTION reject_append_only_visit_audit_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'visit audit rows are immutable' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER tr_visit_audits_immutable
    BEFORE UPDATE OR DELETE ON visit_audits
    FOR EACH ROW EXECUTE FUNCTION reject_append_only_visit_audit_mutation();

CREATE TRIGGER tr_visit_audits_no_truncate
    BEFORE TRUNCATE ON visit_audits
    FOR EACH STATEMENT EXECUTE FUNCTION reject_append_only_visit_audit_mutation();
