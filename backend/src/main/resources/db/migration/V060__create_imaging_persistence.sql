-- FEAT-004 stores workflow metadata and opaque archive references only.
-- DICOM bytes, patient identifiers, accession values and free-text DICOM tags stay out of PostgreSQL.
CREATE TABLE imaging_ingest_operations (
    id UUID PRIMARY KEY,
    study_ref VARCHAR(128) UNIQUE,
    request_id UUID NOT NULL,
    attempt_id UUID NOT NULL,
    visit_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    actor_account_id UUID NOT NULL,
    operation_type VARCHAR(16) NOT NULL,
    state VARCHAR(32) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    payload_size_bytes BIGINT NOT NULL DEFAULT 0,
    study_instance_uid VARCHAR(64),
    modality VARCHAR(16),
    series_count INTEGER NOT NULL DEFAULT 0,
    instance_count INTEGER NOT NULL DEFAULT 0,
    verification_status VARCHAR(24) NOT NULL DEFAULT 'NOT_EVALUATED',
    verification_evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    archive_study_ref TEXT,
    safe_outcome_code VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_imaging_ops_type CHECK (operation_type IN ('UPLOAD', 'SEND')),
    CONSTRAINT ck_imaging_ops_state CHECK (state IN (
        'PROCESSING', 'ARCHIVED_NOT_SENT', 'CONFIRMATION_PENDING', 'LINKED',
        'QUARANTINED', 'FAILED', 'RECONCILIATION_REQUIRED')),
    CONSTRAINT ck_imaging_ops_sha256 CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_imaging_ops_sizes CHECK (payload_size_bytes >= 0 AND series_count >= 0 AND instance_count >= 0),
    CONSTRAINT ck_imaging_ops_verification CHECK (verification_status IN (
        'NOT_EVALUATED', 'CONFIRMED_MATCH', 'MISMATCH', 'UNKNOWN')),
    CONSTRAINT ck_imaging_ops_version CHECK (version >= 0),
    CONSTRAINT ck_imaging_ops_safe_outcome CHECK (
        safe_outcome_code IS NULL OR safe_outcome_code ~ '^[A-Z][A-Z0-9_]{0,63}$')
);
CREATE INDEX ix_imaging_ops_owner_created
    ON imaging_ingest_operations(request_id, attempt_id, created_at DESC, id);
CREATE INDEX ix_imaging_ops_state_updated
    ON imaging_ingest_operations(state, updated_at, id);
CREATE INDEX ix_imaging_ops_reconciliation
    ON imaging_ingest_operations(updated_at, id) WHERE state = 'RECONCILIATION_REQUIRED';

CREATE TABLE imaging_idempotency_records (
    id UUID PRIMARY KEY,
    actor_account_id UUID NOT NULL,
    request_id UUID NOT NULL,
    attempt_id UUID NOT NULL,
    operation_type VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    target_operation_id UUID NOT NULL REFERENCES imaging_ingest_operations(id) ON DELETE RESTRICT,
    safe_outcome_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uk_imaging_idempotency_scope UNIQUE
        (actor_account_id, request_id, attempt_id, operation_type, idempotency_key),
    CONSTRAINT ck_imaging_idempotency_type CHECK (operation_type IN ('UPLOAD', 'SEND')),
    CONSTRAINT ck_imaging_idempotency_key CHECK (idempotency_key ~ '^[!-~]{1,128}$'),
    CONSTRAINT ck_imaging_idempotency_sha256 CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_imaging_idempotency_outcome CHECK (
        safe_outcome_code IS NULL OR safe_outcome_code ~ '^[A-Z][A-Z0-9_]{0,63}$')
);
CREATE INDEX ix_imaging_idempotency_operation ON imaging_idempotency_records(target_operation_id);

CREATE TABLE imaging_studies (
    study_ref VARCHAR(128) PRIMARY KEY,
    ingest_operation_id UUID NOT NULL UNIQUE REFERENCES imaging_ingest_operations(id) ON DELETE RESTRICT,
    request_id UUID NOT NULL,
    attempt_id UUID NOT NULL,
    visit_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    archive_study_ref TEXT NOT NULL UNIQUE,
    study_instance_uid VARCHAR(64) NOT NULL UNIQUE,
    modality VARCHAR(16) NOT NULL,
    series_count INTEGER NOT NULL,
    instance_count INTEGER NOT NULL,
    verification_status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_imaging_studies_counts CHECK (series_count > 0 AND instance_count > 0),
    CONSTRAINT ck_imaging_studies_verification CHECK (verification_status = 'CONFIRMED_MATCH')
);
CREATE INDEX ix_imaging_studies_owner ON imaging_studies(request_id, attempt_id, created_at DESC);

CREATE TABLE imaging_study_series (
    id UUID PRIMARY KEY,
    study_ref VARCHAR(128) NOT NULL REFERENCES imaging_studies(study_ref) ON DELETE RESTRICT,
    series_instance_uid VARCHAR(64) NOT NULL,
    archive_series_ref TEXT NOT NULL UNIQUE,
    instance_count INTEGER NOT NULL,
    CONSTRAINT uk_imaging_series_uid UNIQUE (study_ref, series_instance_uid),
    CONSTRAINT ck_imaging_series_count CHECK (instance_count > 0)
);
CREATE INDEX ix_imaging_series_study ON imaging_study_series(study_ref);

CREATE TABLE imaging_study_instances (
    id UUID PRIMARY KEY,
    series_id UUID NOT NULL REFERENCES imaging_study_series(id) ON DELETE RESTRICT,
    sop_instance_uid VARCHAR(64) NOT NULL,
    archive_instance_ref TEXT NOT NULL UNIQUE,
    frame_count INTEGER,
    CONSTRAINT uk_imaging_instance_uid UNIQUE (series_id, sop_instance_uid),
    CONSTRAINT ck_imaging_instance_frames CHECK (frame_count IS NULL OR frame_count > 0)
);
CREATE INDEX ix_imaging_instances_series ON imaging_study_instances(series_id);

CREATE TABLE imaging_uid_claims (
    uid_type VARCHAR(8) NOT NULL,
    uid_value VARCHAR(64) NOT NULL,
    study_ref VARCHAR(128) NOT NULL REFERENCES imaging_studies(study_ref) ON DELETE RESTRICT,
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_imaging_uid_claims PRIMARY KEY (uid_type, uid_value),
    CONSTRAINT ck_imaging_uid_claim_type CHECK (uid_type IN ('STUDY', 'SERIES', 'SOP')),
    CONSTRAINT ck_imaging_uid_claim_value CHECK (uid_value ~ '^[0-9]+(\.[0-9]+)*$')
);
CREATE INDEX ix_imaging_uid_claim_study ON imaging_uid_claims(study_ref);

CREATE TABLE imaging_confirmation_outbox (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    operation_id UUID NOT NULL UNIQUE REFERENCES imaging_ingest_operations(id) ON DELETE RESTRICT,
    study_ref VARCHAR(128) NOT NULL REFERENCES imaging_studies(study_ref) ON DELETE RESTRICT,
    request_id UUID NOT NULL,
    attempt_id UUID NOT NULL,
    visit_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    verification_status VARCHAR(24) NOT NULL,
    verification_evidence JSONB NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    delivered_at TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    last_safe_error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_imaging_outbox_verification CHECK (verification_status = 'CONFIRMED_MATCH'),
    CONSTRAINT ck_imaging_outbox_sha256 CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_imaging_outbox_status CHECK (status IN ('PENDING', 'DELIVERED', 'ACKNOWLEDGED', 'REJECTED')),
    CONSTRAINT ck_imaging_outbox_attempt_count CHECK (attempt_count BETWEEN 0 AND 8)
);
CREATE INDEX ix_imaging_outbox_pending ON imaging_confirmation_outbox(next_attempt_at, created_at, id)
    WHERE status = 'PENDING';

CREATE TABLE imaging_release_inbox (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    request_id UUID NOT NULL,
    visit_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    study_ref VARCHAR(128) NOT NULL,
    findings_version_id UUID NOT NULL,
    report_id UUID NOT NULL,
    report_sha256 CHAR(64) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    acknowledged_at TIMESTAMPTZ,
    CONSTRAINT ck_imaging_release_report_sha256 CHECK (report_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_imaging_release_payload_sha256 CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_imaging_release_outcome CHECK (outcome IN ('ACCEPTED', 'CONFLICT', 'RECONCILIATION_REQUIRED'))
);

CREATE TABLE imaging_viewer_grants (
    id UUID PRIMARY KEY,
    token_sha256 CHAR(64) NOT NULL UNIQUE,
    actor_account_id UUID NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    study_ref VARCHAR(128) NOT NULL REFERENCES imaging_studies(study_ref) ON DELETE RESTRICT,
    authorized_version_ref VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_imaging_grant_sha256 CHECK (token_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_imaging_grant_purpose CHECK (purpose IN ('DOCTOR_REVIEW', 'PATIENT_VIEW')),
    CONSTRAINT ck_imaging_grant_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_imaging_grant_revocation CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);
CREATE INDEX ix_imaging_grant_owner_expiry
    ON imaging_viewer_grants(study_ref, actor_account_id, expires_at);
CREATE INDEX ix_imaging_grant_active ON imaging_viewer_grants(expires_at, id)
    WHERE revoked_at IS NULL;

CREATE TABLE imaging_audit_events (
    id UUID PRIMARY KEY,
    actor_account_id UUID,
    service_identity VARCHAR(128),
    action VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    operation_id UUID REFERENCES imaging_ingest_operations(id) ON DELETE RESTRICT,
    study_ref VARCHAR(128) REFERENCES imaging_studies(study_ref) ON DELETE RESTRICT,
    request_id UUID,
    attempt_id UUID,
    visit_id UUID,
    patient_id UUID,
    correlation_id VARCHAR(128) NOT NULL,
    safe_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_imaging_audit_actor CHECK ((actor_account_id IS NOT NULL) <> (service_identity IS NOT NULL)),
    CONSTRAINT ck_imaging_audit_correlation CHECK (length(trim(correlation_id)) BETWEEN 1 AND 128)
);
CREATE INDEX ix_imaging_audit_request ON imaging_audit_events(request_id, recorded_at DESC, id);
CREATE INDEX ix_imaging_audit_study ON imaging_audit_events(study_ref, recorded_at DESC, id);
CREATE INDEX ix_imaging_audit_recorded ON imaging_audit_events(recorded_at, id);

CREATE TABLE imaging_reconciliation_cases (
    id UUID PRIMARY KEY,
    operation_id UUID REFERENCES imaging_ingest_operations(id) ON DELETE RESTRICT,
    event_id UUID,
    category VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    safe_reason_code VARCHAR(64) NOT NULL,
    assigned_owner_queue VARCHAR(64) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    resolved_at TIMESTAMPTZ,
    resolution_ref VARCHAR(128),
    CONSTRAINT ck_imaging_recon_identity CHECK ((operation_id IS NOT NULL) <> (event_id IS NOT NULL)),
    CONSTRAINT ck_imaging_recon_state CHECK (state IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    CONSTRAINT ck_imaging_recon_reason CHECK (safe_reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    CONSTRAINT ck_imaging_recon_resolution CHECK (
        (state = 'RESOLVED' AND resolved_at IS NOT NULL) OR (state <> 'RESOLVED' AND resolved_at IS NULL))
);
CREATE UNIQUE INDEX uk_imaging_recon_open_source
    ON imaging_reconciliation_cases(COALESCE(operation_id::text, event_id::text), category)
    WHERE state <> 'RESOLVED';
CREATE INDEX ix_imaging_recon_queue ON imaging_reconciliation_cases(state, updated_at, id);
