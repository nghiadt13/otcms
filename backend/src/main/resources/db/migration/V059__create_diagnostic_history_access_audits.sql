CREATE TABLE diagnostic_history_access_audits (
    id uuid PRIMARY KEY,
    actor_account_id uuid NOT NULL,
    scope_type varchar(16) NOT NULL,
    scope_id uuid NOT NULL,
    page_number integer NOT NULL,
    page_size integer NOT NULL,
    returned_count integer NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_diagnostic_history_audit_scope
        CHECK (scope_type IN ('VISIT', 'PATIENT')),
    CONSTRAINT ck_diagnostic_history_audit_page
        CHECK (page_number >= 0 AND page_size BETWEEN 1 AND 100),
    CONSTRAINT ck_diagnostic_history_audit_returned_count
        CHECK (returned_count >= 0)
);

CREATE INDEX ix_diagnostic_history_audit_scope_recorded
    ON diagnostic_history_access_audits (scope_type, scope_id, recorded_at DESC, id);
CREATE INDEX ix_diagnostic_history_audit_actor_recorded
    ON diagnostic_history_access_audits (actor_account_id, recorded_at DESC, id);
