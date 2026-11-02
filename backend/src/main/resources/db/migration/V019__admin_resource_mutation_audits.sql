CREATE TABLE staff_resource_management_audits (
    id UUID PRIMARY KEY,
    doctor_profile_id UUID NOT NULL REFERENCES doctor_profiles(id) ON DELETE RESTRICT,
    actor_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    resource_type VARCHAR(24) NOT NULL,
    resource_id UUID NOT NULL,
    action VARCHAR(16) NOT NULL,
    before_state JSONB,
    after_state JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    CONSTRAINT ck_staff_resource_audit_type CHECK (resource_type IN ('DOCTOR_PROFILE', 'WORK_SHIFT', 'TIME_OFF')),
    CONSTRAINT ck_staff_resource_audit_action CHECK (action IN ('CREATED', 'UPDATED', 'DELETED')),
    CONSTRAINT ck_staff_resource_audit_correlation CHECK (length(trim(correlation_id)) BETWEEN 1 AND 128)
);
CREATE INDEX ix_staff_resource_audits_doctor_time ON staff_resource_management_audits(doctor_profile_id, occurred_at, id);
CREATE FUNCTION reject_staff_resource_audit_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'staff resource audit rows are immutable' USING ERRCODE = '55000'; END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER tr_staff_resource_audits_immutable BEFORE UPDATE OR DELETE ON staff_resource_management_audits
FOR EACH ROW EXECUTE FUNCTION reject_staff_resource_audit_mutation();
CREATE TRIGGER tr_staff_resource_audits_no_truncate BEFORE TRUNCATE ON staff_resource_management_audits
FOR EACH STATEMENT EXECUTE FUNCTION reject_staff_resource_audit_mutation();

CREATE TABLE service_catalog_management_audits (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES clinic_services(id) ON DELETE RESTRICT,
    actor_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    resource_type VARCHAR(24) NOT NULL,
    resource_id UUID NOT NULL,
    action VARCHAR(16) NOT NULL,
    before_state JSONB,
    after_state JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    CONSTRAINT ck_service_catalog_audit_type CHECK (resource_type IN ('CLINIC_SERVICE', 'DOCTOR_ASSIGNMENT')),
    CONSTRAINT ck_service_catalog_audit_action CHECK (action IN ('CREATED', 'UPDATED', 'DELETED', 'DEACTIVATED')),
    CONSTRAINT ck_service_catalog_audit_correlation CHECK (length(trim(correlation_id)) BETWEEN 1 AND 128)
);
CREATE INDEX ix_service_catalog_audits_service_time ON service_catalog_management_audits(service_id, occurred_at, id);
CREATE FUNCTION reject_service_catalog_audit_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'service catalog audit rows are immutable' USING ERRCODE = '55000'; END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER tr_service_catalog_audits_immutable BEFORE UPDATE OR DELETE ON service_catalog_management_audits
FOR EACH ROW EXECUTE FUNCTION reject_service_catalog_audit_mutation();
CREATE TRIGGER tr_service_catalog_audits_no_truncate BEFORE TRUNCATE ON service_catalog_management_audits
FOR EACH STATEMENT EXECUTE FUNCTION reject_service_catalog_audit_mutation();
