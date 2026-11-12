-- final-db phase 01: one append-only audit table for every module, activity log view over it,
-- and the coordination lock moves from the transaction_guard row to pg_advisory_xact_lock.
-- Old *_audits tables stay until their owning phase moves its writer code (see plan.md section 2).

CREATE TABLE audit_logs (
    id                       uuid         NOT NULL DEFAULT gen_random_uuid(),
    occurred_at              timestamptz  NOT NULL DEFAULT now(),
    source                   varchar(64)  NOT NULL,
    category                 varchar(32)  NOT NULL,
    action                   varchar(64)  NOT NULL,
    record_type              varchar(48)  NOT NULL,
    entity_id                uuid,
    record_label             text,
    correction_reason        text,
    visible_in_activity_log  boolean      NOT NULL DEFAULT true,
    actor_account_id         uuid,
    actor_type               varchar(32),
    before_state             jsonb,
    after_state              jsonb,
    metadata                 jsonb,
    correlation_id           varchar(128),
    client_ip                varchar(64),
    user_agent               text,
    identifier_hmac          varchar(128),
    outcome                  varchar(32),
    dedup_key                varchar(200),
    CONSTRAINT audit_logs_pkey PRIMARY KEY (id),
    CONSTRAINT uk_audit_logs_dedup_key UNIQUE (dedup_key)
);

COMMENT ON TABLE audit_logs IS 'Append-only audit trail for all modules (final-db). No FKs: entries must outlive the audited rows.';
COMMENT ON COLUMN audit_logs.source IS 'Name of the legacy audit table this row replaces; prefix of the activity log id.';

-- Login / registration rate limits count recent rows by source, action, outcome and hashed identifier.
CREATE INDEX ix_audit_logs_rate_limit ON audit_logs (source, action, outcome, identifier_hmac, occurred_at);
CREATE INDEX ix_audit_logs_activity ON audit_logs (visible_in_activity_log, occurred_at DESC);
CREATE INDEX ix_audit_logs_record ON audit_logs (record_type, entity_id);
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_account_id);

CREATE FUNCTION reject_audit_log_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN RAISE EXCEPTION 'audit log rows are immutable' USING ERRCODE='55000'; END;
$$;

CREATE TRIGGER tr_audit_logs_immutable BEFORE DELETE OR UPDATE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();
CREATE TRIGGER tr_audit_logs_no_truncate BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_log_mutation();

-- Activity log keeps its column contract; JdbcActivityLogRepository is unchanged.
CREATE OR REPLACE VIEW activity_log_entries AS
SELECT (CASE a.source
            WHEN 'staff_resource_audits' THEN 'staff'
            WHEN 'room_management_audits' THEN 'room'
            WHEN 'service_audits' THEN 'service'
            WHEN 'content_audits' THEN 'content'
            WHEN 'account_management_audits' THEN 'account'
            WHEN 'appointment_audits' THEN 'appointment'
            WHEN 'visit_audits' THEN 'visit'
            WHEN 'visit_assignment_audits' THEN 'care'
            WHEN 'room_reservation_audits' THEN 'room_reservation'
            WHEN 'walk_in_reception_queue_history' THEN 'queue'
            WHEN 'diagnostic_audit_events' THEN 'diagnostic'
            WHEN 'diagnostic_history_access_audits' THEN 'diagnostic_access'
            ELSE 'log'
        END) || ':' || a.id::text   AS id,
       a.occurred_at,
       a.actor_account_id,
       a.category::text             AS category,
       a.action::text               AS action,
       a.record_type::text          AS record_type,
       a.entity_id                  AS record_id,
       a.record_label,
       a.correction_reason
FROM audit_logs a
WHERE a.visible_in_activity_log;

DROP TRIGGER tr_integration_coordination_guard_immutable ON transaction_guard;
DROP TRIGGER tr_integration_coordination_guard_no_truncate ON transaction_guard;
DROP FUNCTION prevent_integration_coordination_guard_mutation();
DROP TABLE transaction_guard;
