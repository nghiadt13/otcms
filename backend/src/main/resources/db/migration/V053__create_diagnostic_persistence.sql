CREATE TABLE diagnostic_requests (
    id uuid PRIMARY KEY,
    visit_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    ordering_doctor_id uuid NOT NULL,
    service_id uuid NOT NULL,
    room_id uuid NOT NULL,
    imaging_type varchar(32) NOT NULL,
    status varchar(32) NOT NULL,
    cancellation_cause varchar(64),
    cancellation_reason text,
    current_attempt_id uuid,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_diagnostic_requests_status CHECK (status IN (
        'PENDING', 'IN_PROGRESS', 'RESULT_AVAILABLE', 'REVIEWED', 'CANCELLED'
    )),
    CONSTRAINT ck_diagnostic_requests_version CHECK (version >= 0),
    CONSTRAINT ck_diagnostic_requests_imaging_type CHECK (length(trim(imaging_type)) > 0),
    CONSTRAINT ck_diagnostic_requests_attempt_state CHECK (
        (status IN ('PENDING', 'CANCELLED') AND current_attempt_id IS NULL)
        OR (status IN ('IN_PROGRESS', 'RESULT_AVAILABLE', 'REVIEWED') AND current_attempt_id IS NOT NULL)
    ),
    CONSTRAINT ck_diagnostic_requests_cancellation CHECK (
        (status = 'CANCELLED' AND cancellation_cause IS NOT NULL AND cancellation_reason IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancellation_cause IS NULL AND cancellation_reason IS NULL)
    )
);

CREATE INDEX ix_diagnostic_requests_visit_created
    ON diagnostic_requests (visit_id, created_at DESC, id);
CREATE INDEX ix_diagnostic_requests_patient_created
    ON diagnostic_requests (patient_id, created_at DESC, id);
CREATE INDEX ix_diagnostic_requests_status_created
    ON diagnostic_requests (status, created_at, id);

CREATE TABLE diagnostic_attempts (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL,
    attempt_number integer NOT NULL,
    first_use_event_id uuid NOT NULL,
    first_use_type varchar(32) NOT NULL,
    source_id varchar(128) NOT NULL,
    actor_account_id uuid,
    occurred_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    study_ref text,
    CONSTRAINT fk_diagnostic_attempts_request FOREIGN KEY (request_id)
        REFERENCES diagnostic_requests (id) ON DELETE RESTRICT,
    CONSTRAINT uq_diagnostic_attempts_request_number UNIQUE (request_id, attempt_number),
    CONSTRAINT uq_diagnostic_attempts_request_id UNIQUE (request_id, id),
    CONSTRAINT uq_diagnostic_attempts_first_use_event UNIQUE (first_use_event_id),
    CONSTRAINT ck_diagnostic_attempts_number CHECK (attempt_number > 0),
    CONSTRAINT ck_diagnostic_attempts_first_use_type CHECK (
        first_use_type IN ('ACQUISITION_STARTED', 'CONTRAST_ADMINISTERED')
    ),
    CONSTRAINT ck_diagnostic_attempts_source CHECK (length(trim(source_id)) > 0),
    CONSTRAINT ck_diagnostic_attempts_study_ref CHECK (study_ref IS NULL OR length(trim(study_ref)) > 0)
);

CREATE UNIQUE INDEX uq_diagnostic_attempts_one_study_per_request
    ON diagnostic_attempts (request_id) WHERE study_ref IS NOT NULL;
CREATE UNIQUE INDEX uq_diagnostic_attempts_study_ref
    ON diagnostic_attempts (study_ref) WHERE study_ref IS NOT NULL;

ALTER TABLE diagnostic_requests
    ADD CONSTRAINT fk_diagnostic_requests_current_attempt
    FOREIGN KEY (id, current_attempt_id)
    REFERENCES diagnostic_attempts (request_id, id)
    ON DELETE RESTRICT;

CREATE TABLE diagnostic_reports (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL,
    visit_id uuid NOT NULL,
    study_ref text NOT NULL,
    report_version integer NOT NULL,
    storage_ref text NOT NULL,
    content_sha256 varchar(64) NOT NULL,
    validation_status varchar(16) NOT NULL DEFAULT 'PENDING',
    uploaded_by_doctor_id uuid NOT NULL,
    uploaded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    validated_at timestamptz,
    released_at timestamptz,
    CONSTRAINT fk_diagnostic_reports_request FOREIGN KEY (request_id)
        REFERENCES diagnostic_requests (id) ON DELETE RESTRICT,
    CONSTRAINT uq_diagnostic_reports_request_version UNIQUE (request_id, report_version),
    CONSTRAINT uq_diagnostic_reports_request_id UNIQUE (request_id, id),
    CONSTRAINT uq_diagnostic_reports_storage_ref UNIQUE (storage_ref),
    CONSTRAINT ck_diagnostic_reports_version CHECK (report_version > 0),
    CONSTRAINT ck_diagnostic_reports_study_ref CHECK (length(trim(study_ref)) > 0),
    CONSTRAINT ck_diagnostic_reports_sha256 CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_diagnostic_reports_validation_status CHECK (
        validation_status IN ('PENDING', 'VALIDATED', 'INVALID')
    ),
    CONSTRAINT ck_diagnostic_reports_validation_time CHECK (
        (validation_status = 'PENDING' AND validated_at IS NULL)
        OR (validation_status IN ('VALIDATED', 'INVALID') AND validated_at IS NOT NULL)
    ),
    CONSTRAINT ck_diagnostic_reports_release_requires_validation CHECK (
        released_at IS NULL OR validation_status = 'VALIDATED'
    )
);

CREATE INDEX ix_diagnostic_reports_request_uploaded
    ON diagnostic_reports (request_id, uploaded_at DESC, id);

CREATE TABLE diagnostic_findings_versions (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL,
    visit_id uuid NOT NULL,
    study_ref text NOT NULL,
    version_number integer NOT NULL,
    findings_text text NOT NULL,
    recorded_by_doctor_id uuid NOT NULL,
    occurred_at timestamptz,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    state varchar(16) NOT NULL DEFAULT 'DRAFT',
    required_pdf_report_id uuid,
    completed_at timestamptz,
    CONSTRAINT fk_diagnostic_findings_request FOREIGN KEY (request_id)
        REFERENCES diagnostic_requests (id) ON DELETE RESTRICT,
    CONSTRAINT fk_diagnostic_findings_report FOREIGN KEY (request_id, required_pdf_report_id)
        REFERENCES diagnostic_reports (request_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_diagnostic_findings_request_version UNIQUE (request_id, version_number),
    CONSTRAINT ck_diagnostic_findings_version CHECK (version_number > 0),
    CONSTRAINT ck_diagnostic_findings_text CHECK (length(trim(findings_text)) > 0),
    CONSTRAINT ck_diagnostic_findings_study_ref CHECK (length(trim(study_ref)) > 0),
    CONSTRAINT ck_diagnostic_findings_state CHECK (state IN ('DRAFT', 'COMPLETED')),
    CONSTRAINT ck_diagnostic_findings_completion CHECK (
        (state = 'DRAFT' AND completed_at IS NULL AND required_pdf_report_id IS NULL)
        OR (state = 'COMPLETED' AND completed_at IS NOT NULL AND required_pdf_report_id IS NOT NULL)
    )
);

CREATE INDEX ix_diagnostic_findings_request_version
    ON diagnostic_findings_versions (request_id, version_number DESC);

CREATE FUNCTION prevent_completed_diagnostic_findings_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.state = 'COMPLETED' THEN
        RAISE EXCEPTION 'completed diagnostic findings versions are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_diagnostic_findings_completed_immutable
    BEFORE UPDATE OR DELETE ON diagnostic_findings_versions
    FOR EACH ROW EXECUTE FUNCTION prevent_completed_diagnostic_findings_mutation();

CREATE TABLE diagnostic_idempotency_records (
    id uuid PRIMARY KEY,
    actor_account_id uuid,
    system_source_id varchar(128),
    visit_id uuid NOT NULL,
    request_id uuid,
    operation varchar(64) NOT NULL,
    idempotency_key varchar(255) NOT NULL,
    payload_fingerprint varchar(64) NOT NULL,
    result_outcome varchar(48) NOT NULL,
    result_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT fk_diagnostic_idempotency_request FOREIGN KEY (request_id)
        REFERENCES diagnostic_requests (id) ON DELETE RESTRICT,
    CONSTRAINT ck_diagnostic_idempotency_actor CHECK (
        (actor_account_id IS NOT NULL AND system_source_id IS NULL)
        OR (actor_account_id IS NULL AND system_source_id IS NOT NULL)
    ),
    CONSTRAINT ck_diagnostic_idempotency_source CHECK (
        system_source_id IS NULL OR length(trim(system_source_id)) > 0
    ),
    CONSTRAINT ck_diagnostic_idempotency_key CHECK (length(trim(idempotency_key)) > 0),
    CONSTRAINT ck_diagnostic_idempotency_fingerprint CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX uq_diagnostic_idempotency_actor_operation
    ON diagnostic_idempotency_records (
        actor_account_id, visit_id, coalesce(request_id, '00000000-0000-0000-0000-000000000000'::uuid),
        operation, idempotency_key
    ) WHERE actor_account_id IS NOT NULL;
CREATE UNIQUE INDEX uq_diagnostic_idempotency_system_operation
    ON diagnostic_idempotency_records (
        system_source_id, visit_id, coalesce(request_id, '00000000-0000-0000-0000-000000000000'::uuid),
        operation, idempotency_key
    ) WHERE system_source_id IS NOT NULL;

CREATE TABLE diagnostic_audit_events (
    id uuid PRIMARY KEY,
    event_id uuid,
    actor_account_id uuid,
    system_source_id varchar(128),
    request_id uuid,
    visit_id uuid NOT NULL,
    action varchar(64) NOT NULL,
    outcome varchar(48) NOT NULL,
    occurred_at timestamptz,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    correlation_id varchar(128) NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT fk_diagnostic_audit_request FOREIGN KEY (request_id)
        REFERENCES diagnostic_requests (id) ON DELETE RESTRICT,
    CONSTRAINT uq_diagnostic_audit_event UNIQUE (event_id),
    CONSTRAINT ck_diagnostic_audit_actor CHECK (
        (actor_account_id IS NOT NULL AND system_source_id IS NULL)
        OR (actor_account_id IS NULL AND system_source_id IS NOT NULL)
    ),
    CONSTRAINT ck_diagnostic_audit_source CHECK (
        system_source_id IS NULL OR length(trim(system_source_id)) > 0
    ),
    CONSTRAINT ck_diagnostic_audit_correlation CHECK (length(trim(correlation_id)) > 0)
);

CREATE INDEX ix_diagnostic_audit_request_recorded
    ON diagnostic_audit_events (request_id, recorded_at DESC, id);
CREATE INDEX ix_diagnostic_audit_visit_recorded
    ON diagnostic_audit_events (visit_id, recorded_at DESC, id);

CREATE TABLE diagnostic_outbox (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL,
    request_id uuid NOT NULL,
    visit_id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    delivered_at timestamptz,
    last_error text,
    CONSTRAINT fk_diagnostic_outbox_request FOREIGN KEY (request_id)
        REFERENCES diagnostic_requests (id) ON DELETE RESTRICT,
    CONSTRAINT uq_diagnostic_outbox_event UNIQUE (event_id),
    CONSTRAINT ck_diagnostic_outbox_status CHECK (status IN ('PENDING', 'DELIVERED', 'DEAD_LETTER')),
    CONSTRAINT ck_diagnostic_outbox_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_diagnostic_outbox_delivery_time CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (status <> 'DELIVERED' AND delivered_at IS NULL)
    )
);

CREATE UNIQUE INDEX uq_diagnostic_outbox_first_use_billing_per_request
    ON diagnostic_outbox (request_id)
    WHERE event_type = 'FIRST_USE_BILLING_OBLIGATION';
CREATE INDEX ix_diagnostic_outbox_pending_delivery
    ON diagnostic_outbox (next_attempt_at, created_at, id)
    WHERE status = 'PENDING';

CREATE FUNCTION touch_diagnostic_request_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_diagnostic_requests_updated_at
    BEFORE UPDATE ON diagnostic_requests
    FOR EACH ROW EXECUTE FUNCTION touch_diagnostic_request_updated_at();
