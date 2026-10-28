ALTER TABLE visit_care_assignments
    ADD COLUMN step_code VARCHAR(64),
    ADD COLUMN expected_duration_minutes INTEGER,
    ADD COLUMN actual_ended_at TIMESTAMPTZ;

ALTER TABLE visit_care_assignments
    ADD CONSTRAINT ck_visit_care_expected_duration
        CHECK (expected_duration_minutes IS NULL OR expected_duration_minutes > 0),
    ADD CONSTRAINT ck_visit_care_actual_end
        CHECK (actual_ended_at IS NULL OR actual_ended_at >= starts_at);

ALTER TABLE visit_care_assignments
    ADD CONSTRAINT uq_visit_care_assignments_id_visit UNIQUE (id, visit_id);

CREATE TABLE visit_care_assignment_audits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    visit_id UUID NOT NULL REFERENCES clinic_visits(id) ON DELETE RESTRICT,
    assignment_id UUID,
    actor_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    action VARCHAR(16) NOT NULL,
    step_code VARCHAR(64) NOT NULL,
    old_expected_end TIMESTAMPTZ,
    new_expected_end TIMESTAMPTZ,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    CONSTRAINT fk_visit_care_assignment_audit_assignment
        FOREIGN KEY (assignment_id, visit_id)
        REFERENCES visit_care_assignments(id, visit_id) ON DELETE RESTRICT,
    CONSTRAINT ck_visit_care_assignment_audit_action
        CHECK (action IN ('STARTED', 'EXTENDED', 'COMPLETED', 'CAPACITY_CONFLICT')),
    CONSTRAINT ck_visit_care_assignment_audit_assignment
        CHECK ((action = 'CAPACITY_CONFLICT') OR assignment_id IS NOT NULL),
    CONSTRAINT ck_visit_care_assignment_audit_step
        CHECK (length(trim(step_code)) BETWEEN 1 AND 64),
    CONSTRAINT ck_visit_care_assignment_audit_correlation
        CHECK (length(trim(correlation_id)) BETWEEN 1 AND 128)
);

CREATE UNIQUE INDEX uq_visit_care_assignment_conflict_correlation
    ON visit_care_assignment_audits (visit_id, actor_account_id, correlation_id)
    WHERE action = 'CAPACITY_CONFLICT';

CREATE INDEX ix_visit_care_assignment_audits_visit_time
    ON visit_care_assignment_audits (visit_id, occurred_at, id);
CREATE INDEX ix_visit_care_assignment_audits_actor_time
    ON visit_care_assignment_audits (actor_account_id, occurred_at DESC, id);
CREATE INDEX ix_visit_care_assignment_audits_correlation
    ON visit_care_assignment_audits (correlation_id);

CREATE FUNCTION reject_visit_care_assignment_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'visit care assignment audit is append-only' USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_visit_care_assignment_audits_immutable
    BEFORE UPDATE OR DELETE ON visit_care_assignment_audits
    FOR EACH ROW EXECUTE FUNCTION reject_visit_care_assignment_audit_mutation();

CREATE TRIGGER tr_visit_care_assignment_audits_no_truncate
    BEFORE TRUNCATE ON visit_care_assignment_audits
    FOR EACH STATEMENT EXECUTE FUNCTION reject_visit_care_assignment_audit_mutation();
