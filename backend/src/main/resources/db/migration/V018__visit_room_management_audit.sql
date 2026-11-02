CREATE TABLE visit_room_management_audits (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES visit_rooms(id) ON DELETE RESTRICT,
    actor_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    action VARCHAR(16) NOT NULL,
    before_state JSONB,
    after_state JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    CONSTRAINT ck_visit_room_management_audit_action
        CHECK (action IN ('CREATED', 'UPDATED', 'ACTIVATED', 'DEACTIVATED')),
    CONSTRAINT ck_visit_room_management_audit_correlation
        CHECK (length(trim(correlation_id)) BETWEEN 1 AND 128),
    CONSTRAINT ck_visit_room_management_audit_state
        CHECK ((action = 'CREATED' AND before_state IS NULL)
            OR (action <> 'CREATED' AND before_state IS NOT NULL))
);

CREATE INDEX ix_visit_room_management_audits_room_time
    ON visit_room_management_audits(room_id, occurred_at, id);
CREATE INDEX ix_visit_room_management_audits_actor_time
    ON visit_room_management_audits(actor_account_id, occurred_at, id);

CREATE FUNCTION reject_visit_room_management_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'visit room management audit rows are immutable' USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_visit_room_management_audits_immutable
    BEFORE UPDATE OR DELETE ON visit_room_management_audits
    FOR EACH ROW EXECUTE FUNCTION reject_visit_room_management_audit_mutation();

CREATE TRIGGER tr_visit_room_management_audits_no_truncate
    BEFORE TRUNCATE ON visit_room_management_audits
    FOR EACH STATEMENT EXECUTE FUNCTION reject_visit_room_management_audit_mutation();
