-- final-db phase 03 (V118): clinical orders, imaging studies and diagnostic results.
-- Applies after V108 + V109. Copies are INSERT ... SELECT / UPDATE ... FROM, so an empty DB runs clean.
-- Sections: 1 preflight, 2 create/alter, 3 copy, 4 constraints and triggers on copied rows, 5 drop old objects.
-- Deviations from the phase file (each is also commented where it happens):
--   a) the two immutability triggers of diagnostic_reports are dropped before the copy: they block it;
--   b) constraints that check copied rows are added after the copy (section 4);
--   c) the diagnostic_requests -> diagnostic_attempts foreign key is dropped before the two tables (section 5).
-- Split (decision 13/11, phase 03a/03b): this file is sections 1-4 (expand + copy); section 5 (drops)
--   is V118_1__final_db_diagnostic_imaging_drop_old.sql, applied with phase 03b after the code stops
--   reading the old tables.

-- =====================================================================
-- 1. Preflight: abort before any change if a row cannot be represented.
-- =====================================================================
DO $$
BEGIN
    -- Every diagnostic request maps to one clinical order (the new request id).
    IF EXISTS (SELECT 1 FROM diagnostic_requests r
               WHERE r.service_order_id IS NULL
                  OR NOT EXISTS (SELECT 1 FROM clinical_service_orders o WHERE o.id = r.service_order_id)) THEN
        RAISE EXCEPTION 'V118: diagnostic request without an existing clinical service order';
    END IF;
    IF EXISTS (SELECT 1 FROM diagnostic_requests GROUP BY service_order_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'V118: more than one diagnostic request per clinical service order';
    END IF;
    -- A round lives on its orders now; a round without orders would lose its replay key.
    IF EXISTS (SELECT 1 FROM clinical_ordering_rounds r
               WHERE NOT EXISTS (SELECT 1 FROM clinical_service_orders o WHERE o.ordering_round_id = r.id)) THEN
        RAISE EXCEPTION 'V118: ordering round without service orders';
    END IF;
    -- The new model keeps one attempt per order: only the current attempt can be carried over.
    IF EXISTS (SELECT 1 FROM diagnostic_attempts a
               JOIN diagnostic_requests r ON r.id = a.request_id
               WHERE r.current_attempt_id IS DISTINCT FROM a.id) THEN
        RAISE EXCEPTION 'V118: non-current diagnostic attempt has no place in the new model';
    END IF;
    IF EXISTS (SELECT 1 FROM imaging_ingest_operations o
               WHERE NOT EXISTS (SELECT 1 FROM diagnostic_requests r WHERE r.id = o.request_id)) THEN
        RAISE EXCEPTION 'V118: imaging operation without diagnostic request';
    END IF;
    IF EXISTS (SELECT 1 FROM imaging_studies s
               JOIN imaging_ingest_operations o ON o.id = s.ingest_operation_id
               WHERE s.study_ref IS DISTINCT FROM o.study_ref) THEN
        RAISE EXCEPTION 'V118: imaging study and operation disagree on study_ref';
    END IF;
    -- A completed version releases the report of the same version number (checked in code, not in V108).
    IF EXISTS (SELECT 1 FROM diagnostic_findings_versions f
               WHERE f.required_pdf_report_id IS NOT NULL
                 AND NOT EXISTS (SELECT 1 FROM diagnostic_reports r
                                 WHERE r.id = f.required_pdf_report_id
                                   AND r.report_version = f.version_number)) THEN
        RAISE EXCEPTION 'V118: completed findings point at a report of another version';
    END IF;
END $$;

-- =====================================================================
-- 2. Create / alter. Nullable columns first; NOT NULL and row checks after the copy.
-- =====================================================================

-- 2.1 clinical_service_orders: one row per order; it also carries the round, the imaging request and its attempt.
ALTER TABLE clinical_service_orders DROP CONSTRAINT clinical_service_orders_ordering_round_id_fkey;

ALTER TABLE clinical_service_orders
    ADD COLUMN round_number               integer,
    ADD COLUMN ordered_at                 timestamptz,
    ADD COLUMN round_head                 boolean NOT NULL DEFAULT false,
    ADD COLUMN submit_idempotency_key     varchar(128),
    ADD COLUMN submit_request_fingerprint char(64),
    ADD COLUMN ordering_doctor_account_id uuid,
    ADD COLUMN room_id                    uuid,
    ADD COLUMN imaging_type               varchar(80),
    ADD COLUMN execution_status           varchar(32),
    ADD COLUMN cancellation_cause         varchar(64),
    ADD COLUMN cancellation_reason        text,
    ADD COLUMN attempt_id                 uuid,
    ADD COLUMN technician_account_id      uuid,
    ADD COLUMN first_use_type             varchar(32),
    ADD COLUMN first_use_event_id         uuid,
    ADD COLUMN first_use_at               timestamptz,
    ADD COLUMN first_use_source_id        varchar(128),
    ADD COLUMN study_ref                  text,
    ADD COLUMN review_idempotency_key     varchar(255),
    ADD COLUMN review_fingerprint         char(64),
    ADD COLUMN cancel_idempotency_key     varchar(255),
    ADD COLUMN cancel_fingerprint         char(64),
    ADD COLUMN transfer_idempotency_key   varchar(255),
    ADD COLUMN transfer_fingerprint       char(64),
    -- Optimistic-lock version of the imaging execution (the diagnostic API's version / expectedVersion, was
    -- diagnostic_requests.version). Separate from clinical's own version column so the two never collide.
    ADD COLUMN execution_version          bigint NOT NULL DEFAULT 0;

ALTER TABLE clinical_service_orders
    ADD CONSTRAINT ck_clinical_orders_round_number CHECK (round_number > 0),
    ADD CONSTRAINT ck_clinical_orders_round_head_keys CHECK (round_head OR (submit_idempotency_key IS NULL AND submit_request_fingerprint IS NULL)),
    ADD CONSTRAINT ck_clinical_orders_submit_fingerprint CHECK (submit_request_fingerprint IS NULL OR submit_request_fingerprint ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_clinical_orders_execution CHECK (execution_status IS NULL OR (execution_status IN ('PENDING', 'IN_PROGRESS', 'RESULT_AVAILABLE', 'REVIEWED', 'CANCELLED') AND service_type = 'IMAGING' AND imaging_type IS NOT NULL AND ordering_doctor_account_id IS NOT NULL)),
    ADD CONSTRAINT ck_clinical_orders_assigned_before_use CHECK (execution_status IS NULL OR room_id IS NOT NULL OR execution_status IN ('PENDING', 'CANCELLED')),
    ADD CONSTRAINT ck_clinical_orders_attempt_state CHECK (execution_status IS NULL OR (execution_status IN ('PENDING', 'CANCELLED') AND attempt_id IS NULL) OR (execution_status IN ('IN_PROGRESS', 'RESULT_AVAILABLE', 'REVIEWED') AND attempt_id IS NOT NULL)),
    ADD CONSTRAINT ck_clinical_orders_in_progress_no_study CHECK (execution_status IS DISTINCT FROM 'IN_PROGRESS' OR study_ref IS NULL),
    ADD CONSTRAINT ck_clinical_orders_result_has_study CHECK (execution_status NOT IN ('RESULT_AVAILABLE', 'REVIEWED') OR study_ref IS NOT NULL),
    ADD CONSTRAINT ck_clinical_orders_cancellation CHECK (CASE WHEN execution_status = 'CANCELLED' THEN cancellation_cause IN ('PATIENT_DECLINED', 'CLINIC_UNABLE') AND cancellation_reason IS NOT NULL ELSE cancellation_cause IS NULL AND cancellation_reason IS NULL END),
    ADD CONSTRAINT ck_clinical_orders_execution_version CHECK (execution_version >= 0),
    ADD CONSTRAINT uk_clinical_orders_id_visit UNIQUE (id, visit_id),
    ADD CONSTRAINT uk_clinical_orders_id_study UNIQUE (id, study_ref);

-- One head per round; one round number per visit (heads only); submit key per doctor.
CREATE UNIQUE INDEX uk_clinical_orders_round_head ON clinical_service_orders (ordering_round_id) WHERE round_head;
CREATE UNIQUE INDEX uk_clinical_orders_round_number ON clinical_service_orders (visit_id, round_number) WHERE round_head;
CREATE UNIQUE INDEX uk_clinical_orders_submit_key ON clinical_service_orders (requesting_doctor_id, submit_idempotency_key) WHERE submit_idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uk_clinical_orders_study_ref ON clinical_service_orders (study_ref) WHERE study_ref IS NOT NULL;
CREATE UNIQUE INDEX uk_clinical_orders_attempt_id ON clinical_service_orders (attempt_id) WHERE attempt_id IS NOT NULL;
CREATE UNIQUE INDEX uk_clinical_orders_first_use_event ON clinical_service_orders (first_use_event_id) WHERE first_use_event_id IS NOT NULL;
CREATE INDEX ix_clinical_orders_round_head_visit ON clinical_service_orders (visit_id, round_number DESC) WHERE round_head;
CREATE INDEX ix_clinical_orders_room_work ON clinical_service_orders (room_id, execution_status) WHERE execution_status IS NOT NULL;

-- 2.2 diagnostic_reports: changed in place. One row = one results version and/or its PDF report.
-- DEVIATION a): the two immutability triggers block the in-place copy, so they go before it.
DROP TRIGGER tr_diagnostic_reports_scope ON diagnostic_reports;
DROP TRIGGER tr_diagnostic_reports_used_immutable ON diagnostic_reports;
ALTER TABLE diagnostic_reports DROP CONSTRAINT fk_diagnostic_reports_request;
ALTER TABLE diagnostic_reports
    DROP CONSTRAINT ck_diagnostic_reports_release_requires_validation,
    DROP CONSTRAINT ck_diagnostic_reports_sha256,
    DROP CONSTRAINT ck_diagnostic_reports_study_ref,
    DROP CONSTRAINT ck_diagnostic_reports_validation_status,
    DROP CONSTRAINT ck_diagnostic_reports_validation_time,
    DROP CONSTRAINT ck_diagnostic_reports_version;
DROP INDEX ix_diagnostic_reports_request_uploaded;
ALTER TABLE diagnostic_reports RENAME COLUMN request_id TO service_order_id;
ALTER TABLE diagnostic_reports RENAME COLUMN storage_ref TO report_storage_ref;
ALTER TABLE diagnostic_reports RENAME COLUMN content_sha256 TO report_sha256;
ALTER TABLE diagnostic_reports RENAME COLUMN report_version TO version_number;
ALTER TABLE diagnostic_reports RENAME CONSTRAINT uq_diagnostic_reports_request_version TO uq_results_version;

-- uploaded_* stay (not in the brief): the PDF uploader and time, nullable for findings-only rows.
ALTER TABLE diagnostic_reports
    ALTER COLUMN study_ref DROP NOT NULL,
    ALTER COLUMN report_storage_ref DROP NOT NULL,
    ALTER COLUMN report_sha256 DROP NOT NULL,
    ALTER COLUMN validation_status DROP NOT NULL,
    ALTER COLUMN validation_status DROP DEFAULT,
    ALTER COLUMN uploaded_by_doctor_id DROP NOT NULL,
    ALTER COLUMN uploaded_at DROP NOT NULL,
    ALTER COLUMN uploaded_at DROP DEFAULT,
    ADD COLUMN result_type                varchar(16) NOT NULL DEFAULT 'IMAGING',
    ADD COLUMN state                      varchar(16) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN findings_text              text,
    ADD COLUMN correction_reason          text,
    ADD COLUMN lab_values                 jsonb,
    ADD COLUMN report_id                  uuid,
    ADD COLUMN occurred_at                timestamptz,
    ADD COLUMN recorded_by_doctor_id      uuid,
    ADD COLUMN completed_at               timestamptz,
    ADD COLUMN recorded_at                timestamptz NOT NULL DEFAULT clock_timestamp(),
    ADD COLUMN draft_idempotency_key      varchar(255),
    ADD COLUMN draft_fingerprint          char(64),
    ADD COLUMN correction_idempotency_key varchar(255),
    ADD COLUMN correction_fingerprint     char(64),
    ADD COLUMN upload_idempotency_key     varchar(255),
    ADD COLUMN upload_fingerprint         char(64),
    ADD COLUMN complete_idempotency_key   varchar(255),
    ADD COLUMN complete_fingerprint       char(64);

-- 2.3 imaging_studies: one row per upload operation; the operation id becomes the primary key.
ALTER TABLE imaging_studies ADD COLUMN id uuid;
UPDATE imaging_studies SET id = ingest_operation_id;

ALTER TABLE imaging_studies
    DROP CONSTRAINT ck_imaging_studies_verification,
    DROP CONSTRAINT ck_imaging_studies_counts,
    ALTER COLUMN series_count SET DEFAULT 0,
    ALTER COLUMN instance_count SET DEFAULT 0,
    ALTER COLUMN verification_status SET DEFAULT 'NOT_EVALUATED';

-- Foreign keys on study_ref must go before the primary key. Child tables are dropped in section 5.
ALTER TABLE imaging_study_series DROP CONSTRAINT imaging_study_series_study_ref_fkey;
ALTER TABLE imaging_uid_claims DROP CONSTRAINT imaging_uid_claims_study_ref_fkey;
ALTER TABLE imaging_confirmation_outbox DROP CONSTRAINT imaging_confirmation_outbox_study_ref_fkey;
ALTER TABLE imaging_audit_events DROP CONSTRAINT imaging_audit_events_study_ref_fkey;
ALTER TABLE imaging_viewer_grants DROP CONSTRAINT imaging_viewer_grants_study_ref_fkey;

ALTER TABLE imaging_studies DROP CONSTRAINT imaging_studies_pkey;
ALTER TABLE imaging_studies
    ALTER COLUMN id SET NOT NULL,
    ALTER COLUMN study_ref DROP NOT NULL,
    ALTER COLUMN archive_study_ref DROP NOT NULL,
    ALTER COLUMN study_instance_uid DROP NOT NULL,
    ALTER COLUMN modality DROP NOT NULL,
    ADD CONSTRAINT imaging_studies_pkey PRIMARY KEY (id),
    ADD CONSTRAINT imaging_studies_study_ref_key UNIQUE (study_ref);

-- New uploads are written only here (id = operation id), so the old link to imaging_ingest_operations must not
-- block them. The column and its copied values stay until V118_1 drops it with the old table.
ALTER TABLE imaging_studies
    DROP CONSTRAINT imaging_studies_ingest_operation_id_fkey,
    ALTER COLUMN ingest_operation_id DROP NOT NULL;

ALTER TABLE imaging_viewer_grants
    ADD CONSTRAINT imaging_viewer_grants_study_ref_fkey FOREIGN KEY (study_ref)
        REFERENCES imaging_studies (study_ref) ON DELETE RESTRICT;

-- Diagnostic request id is replaced by the clinical order id (requestId in the API).
ALTER TABLE imaging_studies RENAME COLUMN request_id TO service_order_id;

ALTER TABLE imaging_studies
    ADD COLUMN actor_account_id             uuid,
    ADD COLUMN state                        varchar(32),
    ADD COLUMN payload_sha256               char(64),
    ADD COLUMN payload_size_bytes           bigint NOT NULL DEFAULT 0,
    ADD COLUMN upload_idempotency_key       varchar(128),
    ADD COLUMN dicom_index                  jsonb,
    ADD COLUMN verification_evidence        jsonb NOT NULL DEFAULT '[]',
    ADD COLUMN safe_outcome_code            varchar(64),
    ADD COLUMN send_idempotency_key         varchar(128),
    ADD COLUMN send_fingerprint             char(64),
    ADD COLUMN confirmation_event_id        uuid,
    ADD COLUMN confirmation_attempt_count   integer NOT NULL DEFAULT 0,
    ADD COLUMN next_confirmation_attempt_at timestamptz,
    ADD COLUMN last_safe_error_code         varchar(64),
    ADD COLUMN acknowledged_at              timestamptz,
    ADD COLUMN release_event_id             uuid,
    ADD COLUMN release_payload_sha256       char(64),
    ADD COLUMN released_findings_id         uuid,
    ADD COLUMN released_report_id           uuid,
    ADD COLUMN released_report_sha256       char(64),
    ADD COLUMN findings_released_at         timestamptz,
    ADD COLUMN version                      bigint NOT NULL DEFAULT 0,
    ADD COLUMN updated_at                   timestamptz NOT NULL DEFAULT clock_timestamp();

-- 2.4 visits, invoices, invoice_items.
ALTER TABLE visits
    ADD COLUMN imaging_result_ready_at timestamptz,
    ADD COLUMN findings_released_at    timestamptz;

-- Decision 13/11: invoices.clinical_ordering_round_id stays as a column; only its foreign key goes.
ALTER TABLE invoices DROP CONSTRAINT invoices_clinical_ordering_round_id_fkey;

ALTER TABLE invoice_items
    ADD COLUMN service_order_id uuid,
    ADD CONSTRAINT invoice_items_service_order_id_fkey FOREIGN KEY (service_order_id)
        REFERENCES clinical_service_orders (id) ON DELETE RESTRICT;
-- One invoice line per order; the unique index also serves the foreign key.
CREATE UNIQUE INDEX uk_invoice_items_service_order ON invoice_items (service_order_id) WHERE service_order_id IS NOT NULL;

-- =====================================================================
-- 3. Copy existing rows into the new columns (old rows are read before section 5 drops them).
-- =====================================================================

-- 3.1 Rounds: number and time go on every order; the earliest order of a round is its head.
UPDATE clinical_service_orders o
SET round_number = r.round_number,
    ordered_at = r.ordered_at
FROM clinical_ordering_rounds r
WHERE r.id = o.ordering_round_id;

UPDATE clinical_service_orders o
SET round_head = (h.rn = 1)
FROM (SELECT id, row_number() OVER (PARTITION BY ordering_round_id ORDER BY created_at, id) AS rn
      FROM clinical_service_orders) h
WHERE h.id = o.id;

-- The submit replay of a round (one per round) goes on its head order.
UPDATE clinical_service_orders o
SET submit_idempotency_key = p.idempotency_key,
    submit_request_fingerprint = p.request_fingerprint
FROM clinical_imaging_request_replays p
WHERE p.ordering_round_id = o.ordering_round_id
  AND o.round_head;

-- Imaging request and its current attempt go on the order (requestId = order id from now on).
UPDATE clinical_service_orders o
SET room_id = dr.room_id,
    imaging_type = dr.imaging_type,
    execution_status = dr.status,
    cancellation_cause = dr.cancellation_cause,
    cancellation_reason = dr.cancellation_reason,
    ordering_doctor_account_id = dr.ordering_doctor_id,
    attempt_id = da.id,
    technician_account_id = da.actor_account_id,
    first_use_type = da.first_use_type,
    first_use_event_id = da.first_use_event_id,
    first_use_at = da.occurred_at,
    first_use_source_id = da.source_id,
    study_ref = da.study_ref,
    execution_version = dr.version
FROM diagnostic_requests dr
LEFT JOIN diagnostic_attempts da ON da.id = dr.current_attempt_id
WHERE dr.service_order_id = o.id;

-- 3.2 Results. A findings version and the PDF of the same order and version share one row:
-- the row id becomes the findings version id; the old report id moves to report_id.
UPDATE diagnostic_reports SET report_id = id;

-- Old request id -> clinical order id.
UPDATE diagnostic_reports rep
SET service_order_id = dr.service_order_id
FROM diagnostic_requests dr
WHERE dr.id = rep.service_order_id;

UPDATE diagnostic_reports rep
SET id = f.id,
    visit_id = f.visit_id,
    study_ref = f.study_ref,
    state = f.state,
    findings_text = f.findings_text,
    correction_reason = f.correction_reason,
    occurred_at = f.occurred_at,
    recorded_by_doctor_id = f.recorded_by_doctor_id,
    recorded_at = f.recorded_at,
    completed_at = f.completed_at
FROM diagnostic_findings_versions f
JOIN diagnostic_requests dr ON dr.id = f.request_id
WHERE rep.service_order_id = dr.service_order_id
  AND rep.version_number = f.version_number;

-- Findings versions without a report become rows of their own (DRAFT or COMPLETED).
INSERT INTO diagnostic_reports (id, service_order_id, visit_id, study_ref, version_number, findings_text,
                                state, correction_reason, occurred_at, recorded_by_doctor_id, recorded_at, completed_at)
SELECT f.id, dr.service_order_id, f.visit_id, f.study_ref, f.version_number, f.findings_text,
       f.state, f.correction_reason, f.occurred_at, f.recorded_by_doctor_id, f.recorded_at, f.completed_at
FROM diagnostic_findings_versions f
JOIN diagnostic_requests dr ON dr.id = f.request_id
WHERE NOT EXISTS (SELECT 1 FROM diagnostic_reports r WHERE r.id = f.id);

-- Report-only rows (id still equals report_id): recorded_* come from the upload.
UPDATE diagnostic_reports
SET recorded_by_doctor_id = uploaded_by_doctor_id,
    recorded_at = uploaded_at
WHERE id = report_id;

-- 3.3 Imaging studies: operations are the source; the old study rows are updated in place.
UPDATE imaging_studies s
SET service_order_id = dr.service_order_id,
    attempt_id = o.attempt_id,
    visit_id = o.visit_id,
    patient_id = o.patient_id,
    actor_account_id = o.actor_account_id,
    study_ref = o.study_ref,
    study_instance_uid = o.study_instance_uid,
    modality = o.modality,
    series_count = o.series_count,
    instance_count = o.instance_count,
    archive_study_ref = o.archive_study_ref,
    verification_status = o.verification_status,
    verification_evidence = o.verification_evidence,
    state = o.state,
    payload_sha256 = o.payload_sha256,
    payload_size_bytes = o.payload_size_bytes,
    safe_outcome_code = o.safe_outcome_code,
    version = o.version,
    created_at = o.created_at,
    updated_at = o.updated_at
FROM imaging_ingest_operations o
JOIN diagnostic_requests dr ON dr.id = o.request_id
WHERE o.id = s.id;

-- Operations that never got a study row get one: one row per upload operation.
INSERT INTO imaging_studies (id, ingest_operation_id, service_order_id, attempt_id, visit_id, patient_id,
                             actor_account_id, study_ref, study_instance_uid, modality, series_count, instance_count,
                             archive_study_ref, verification_status, verification_evidence, state, payload_sha256,
                             payload_size_bytes, safe_outcome_code, version, created_at, updated_at)
SELECT o.id, o.id, dr.service_order_id, o.attempt_id, o.visit_id, o.patient_id,
       o.actor_account_id, o.study_ref, o.study_instance_uid, o.modality, o.series_count, o.instance_count,
       o.archive_study_ref, o.verification_status, o.verification_evidence, o.state, o.payload_sha256,
       o.payload_size_bytes, o.safe_outcome_code, o.version, o.created_at, o.updated_at
FROM imaging_ingest_operations o
JOIN diagnostic_requests dr ON dr.id = o.request_id
WHERE NOT EXISTS (SELECT 1 FROM imaging_studies s WHERE s.id = o.id);

-- UPLOAD key (one per operation) and SEND key/fingerprint (one per operation) from the idempotency records.
-- The SEND fingerprint is the record's payload hash, as the SEND code writes it.
UPDATE imaging_studies s
SET upload_idempotency_key = u.idempotency_key
FROM (SELECT DISTINCT ON (target_operation_id) target_operation_id, idempotency_key
      FROM imaging_idempotency_records
      WHERE operation_type = 'UPLOAD'
      ORDER BY target_operation_id, created_at, id) u
WHERE u.target_operation_id = s.id;

UPDATE imaging_studies s
SET send_idempotency_key = v.idempotency_key,
    send_fingerprint = v.payload_sha256
FROM (SELECT DISTINCT ON (target_operation_id) target_operation_id, idempotency_key, payload_sha256
      FROM imaging_idempotency_records
      WHERE operation_type = 'SEND'
      ORDER BY target_operation_id, created_at, id) v
WHERE v.target_operation_id = s.id;

-- Confirmation queue: the outbox row of the SEND (one per operation).
UPDATE imaging_studies s
SET confirmation_event_id = ob.event_id,
    confirmation_attempt_count = ob.attempt_count,
    next_confirmation_attempt_at = ob.next_attempt_at,
    last_safe_error_code = ob.last_safe_error_code,
    acknowledged_at = ob.acknowledged_at
FROM imaging_confirmation_outbox ob
WHERE ob.operation_id = s.id;

-- Release: the accepted release event of each study. CONFLICT and RECONCILIATION_REQUIRED rows are not carried over.
UPDATE imaging_studies s
SET release_event_id = ib.event_id,
    release_payload_sha256 = ib.payload_sha256,
    released_findings_id = ib.findings_version_id,
    released_report_id = ib.report_id,
    released_report_sha256 = ib.report_sha256,
    findings_released_at = ib.received_at
FROM (SELECT DISTINCT ON (study_ref) study_ref, event_id, payload_sha256, findings_version_id,
             report_id, report_sha256, received_at
      FROM imaging_release_inbox
      WHERE outcome = 'ACCEPTED'
      ORDER BY study_ref, received_at, event_id) ib
WHERE ib.study_ref = s.study_ref;

-- Series and instances become one jsonb index per study; a study without series keeps NULL.
UPDATE imaging_studies s
SET dicom_index = x.idx
FROM (SELECT ser.study_ref,
             jsonb_build_object('series', jsonb_agg(jsonb_build_object(
                 'uid', ser.series_instance_uid,
                 'orthanc_ref', ser.archive_series_ref,
                 'instances', COALESCE(inst.instances, '[]'::jsonb))
                 ORDER BY ser.series_instance_uid)) AS idx
      FROM imaging_study_series ser
      LEFT JOIN (SELECT series_id,
                        jsonb_agg(jsonb_build_object(
                            'sop_uid', sop_instance_uid,
                            'orthanc_ref', archive_instance_ref,
                            'frames', frame_count)
                            ORDER BY sop_instance_uid) AS instances
                 FROM imaging_study_instances
                 GROUP BY series_id) inst ON inst.series_id = ser.id
      GROUP BY ser.study_ref) x
WHERE x.study_ref = s.study_ref;

-- 3.4 Visit timestamps: first receipt of each kind; findings fall back to the accepted releases.
UPDATE visits v
SET imaging_result_ready_at = (SELECT min(r.received_at) FROM visit_imaging_result_receipts r
                               WHERE r.visit_id = v.id AND r.event_type = 'IMAGING_RESULT_READY'),
    findings_released_at = COALESCE(
        (SELECT min(r.received_at) FROM visit_imaging_result_receipts r
          WHERE r.visit_id = v.id AND r.event_type = 'FINDINGS_RELEASE'),
        (SELECT min(b.received_at) FROM imaging_release_inbox b
          WHERE b.visit_id = v.id AND b.outcome = 'ACCEPTED'));

-- 3.5 Audit history -> audit_logs (phase 01 mapping). Source ids are kept, so activity log ids do not change.
-- Diagnostic request ids become the order id, as the API now reports them. actor_type stays NULL (no legacy value).
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, metadata, correlation_id, outcome)
SELECT e.id,
       COALESCE(e.occurred_at, e.recorded_at),
       'diagnostic_audit_events',
       'DIAGNOSTIC',
       e.action || ':' || e.outcome,
       'DIAGNOSTIC_REQUEST',
       CASE WHEN e.request_id IS NULL THEN e.visit_id ELSE r.service_order_id END,
       (CASE WHEN e.request_id IS NULL THEN e.visit_id ELSE r.service_order_id END)::text,
       true,
       e.actor_account_id,
       e.metadata || jsonb_strip_nulls(jsonb_build_object('systemSourceId', e.system_source_id)),
       e.correlation_id,
       e.outcome
FROM diagnostic_audit_events e
LEFT JOIN diagnostic_requests r ON r.id = e.request_id;

INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, metadata)
SELECT h.id,
       h.recorded_at,
       'diagnostic_history_access_audits',
       'DIAGNOSTIC',
       'VIEW_HISTORY',
       h.scope_type,
       h.scope_id,
       h.scope_id::text,
       true,
       h.actor_account_id,
       jsonb_build_object('pageNumber', h.page_number, 'pageSize', h.page_size, 'returnedCount', h.returned_count)
FROM diagnostic_history_access_audits h;

-- Imaging audit is not shown in the activity log. study_ref and the old correlation id move into metadata.
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, record_label,
                        visible_in_activity_log, actor_account_id, metadata, outcome)
SELECT a.id,
       a.occurred_at,
       'imaging_audit_events',
       'IMAGING',
       a.action,
       'IMAGING_STUDY',
       NULL,
       false,
       a.actor_account_id,
       a.safe_metadata || jsonb_strip_nulls(jsonb_build_object(
           'studyRef', a.study_ref,
           'operationId', a.operation_id,
           'correlationRef', a.correlation_id,
           'serviceIdentity', a.service_identity)),
       a.outcome
FROM imaging_audit_events a;

-- =====================================================================
-- 4. Constraints, triggers and indexes on the copied rows.
-- =====================================================================

-- 4.1 NOT NULL that the copy has now satisfied.
ALTER TABLE clinical_service_orders
    ALTER COLUMN round_number SET NOT NULL,
    ALTER COLUMN ordered_at SET NOT NULL;
ALTER TABLE imaging_studies
    ALTER COLUMN actor_account_id SET NOT NULL,
    ALTER COLUMN state SET NOT NULL,
    ALTER COLUMN payload_sha256 SET NOT NULL,
    ALTER COLUMN upload_idempotency_key SET NOT NULL;

-- 4.2 diagnostic_reports rules (replace the 6 V108 checks and the triggers of section 2.2).
-- Old constraints were dropped in 2.2; these are the new rules from the phase file.
ALTER TABLE diagnostic_reports
    ADD CONSTRAINT ck_results_state CHECK (state IN ('DRAFT', 'COMPLETED')),
    ADD CONSTRAINT ck_results_type CHECK (result_type IN ('IMAGING', 'LAB')),
    ADD CONSTRAINT ck_results_version CHECK (version_number > 0),
    ADD CONSTRAINT ck_results_findings_text CHECK (findings_text IS NULL OR length(btrim(findings_text)) > 0),
    ADD CONSTRAINT ck_results_study_ref CHECK (study_ref IS NULL OR length(btrim(study_ref)) > 0),
    ADD CONSTRAINT ck_results_imaging_study CHECK (result_type = 'LAB' OR study_ref IS NOT NULL),
    ADD CONSTRAINT ck_results_report_all_or_none CHECK (
        (report_id IS NULL) = (report_storage_ref IS NULL)
        AND (report_id IS NULL) = (report_sha256 IS NULL)
        AND (report_id IS NULL) = (validation_status IS NULL)),
    ADD CONSTRAINT ck_results_sha256 CHECK (report_sha256 IS NULL OR report_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_results_validation CHECK (validation_status IS NULL OR (
        validation_status IN ('PENDING', 'VALIDATED', 'INVALID')
        AND ((validation_status = 'PENDING') = (validated_at IS NULL)))),
    ADD CONSTRAINT ck_results_completed CHECK (state <> 'COMPLETED' OR (
        findings_text IS NOT NULL AND completed_at IS NOT NULL
        AND report_id IS NOT NULL AND validation_status = 'VALIDATED')),
    ADD CONSTRAINT ck_results_released CHECK (released_at IS NULL OR (state = 'COMPLETED' AND validation_status = 'VALIDATED')),
    ADD CONSTRAINT uq_results_report_id UNIQUE (report_id),
    ADD CONSTRAINT fk_results_order_visit FOREIGN KEY (service_order_id, visit_id)
        REFERENCES clinical_service_orders (id, visit_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_results_order_study FOREIGN KEY (service_order_id, study_ref)
        REFERENCES clinical_service_orders (id, study_ref) ON DELETE RESTRICT;
-- MATCH SIMPLE: LAB rows (study_ref NULL) are not checked against the order's study.

CREATE UNIQUE INDEX uq_results_draft_key ON diagnostic_reports (service_order_id, draft_idempotency_key) WHERE draft_idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uq_results_correction_key ON diagnostic_reports (service_order_id, correction_idempotency_key) WHERE correction_idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uq_results_upload_key ON diagnostic_reports (service_order_id, upload_idempotency_key) WHERE upload_idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uq_results_complete_key ON diagnostic_reports (service_order_id, complete_idempotency_key) WHERE complete_idempotency_key IS NOT NULL;
CREATE INDEX ix_results_visit ON diagnostic_reports (visit_id);

-- A released or completed result is immutable. Its one allowed change is the release itself (DRAFT -> COMPLETED
-- with released_at in the same UPDATE, or released_at set on a COMPLETED row).
CREATE FUNCTION prevent_result_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.state = 'COMPLETED' OR OLD.released_at IS NOT NULL THEN
      RAISE EXCEPTION 'released or completed results are immutable';
    END IF;
    RETURN OLD;
  END IF;
  IF OLD.released_at IS NOT NULL THEN
    RAISE EXCEPTION 'released or completed results are immutable';
  END IF;
  IF OLD.state = 'COMPLETED' AND (to_jsonb(OLD) - 'released_at') IS DISTINCT FROM (to_jsonb(NEW) - 'released_at') THEN
    RAISE EXCEPTION 'completed diagnostic findings versions are immutable';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tr_diagnostic_reports_immutable BEFORE UPDATE OR DELETE ON diagnostic_reports
  FOR EACH ROW EXECUTE FUNCTION prevent_result_mutation();

-- 4.3 imaging_studies rules (replace the V108 checks; the status moves to state, see decision Q8).
ALTER TABLE imaging_studies
    ADD CONSTRAINT ck_imaging_studies_state CHECK (state IN ('PROCESSING', 'ARCHIVED_NOT_SENT', 'CONFIRMATION_PENDING', 'LINKED', 'QUARANTINED', 'FAILED', 'RECONCILIATION_REQUIRED')),
    ADD CONSTRAINT ck_imaging_studies_verification CHECK (verification_status IN ('NOT_EVALUATED', 'CONFIRMED_MATCH', 'MISMATCH', 'UNKNOWN')),
    ADD CONSTRAINT ck_imaging_studies_sha256 CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'
        AND (send_fingerprint IS NULL OR send_fingerprint ~ '^[0-9a-f]{64}$')
        AND (release_payload_sha256 IS NULL OR release_payload_sha256 ~ '^[0-9a-f]{64}$')
        AND (released_report_sha256 IS NULL OR released_report_sha256 ~ '^[0-9a-f]{64}$')),
    ADD CONSTRAINT ck_imaging_studies_sizes CHECK (payload_size_bytes >= 0 AND series_count >= 0 AND instance_count >= 0),
    ADD CONSTRAINT ck_imaging_studies_version CHECK (version >= 0),
    ADD CONSTRAINT ck_imaging_studies_confirmation_attempts CHECK (confirmation_attempt_count BETWEEN 0 AND 8),
    ADD CONSTRAINT ck_imaging_studies_safe_outcome CHECK (safe_outcome_code IS NULL OR safe_outcome_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    ADD CONSTRAINT ck_imaging_studies_linked CHECK (state <> 'LINKED' OR (study_ref IS NOT NULL
        AND archive_study_ref IS NOT NULL AND study_instance_uid IS NOT NULL AND acknowledged_at IS NOT NULL)),
    ADD CONSTRAINT ck_imaging_studies_findings_released CHECK (findings_released_at IS NULL OR state = 'LINKED'),
    ADD CONSTRAINT uk_imaging_studies_upload_scope UNIQUE (actor_account_id, service_order_id, attempt_id, upload_idempotency_key),
    ADD CONSTRAINT uk_imaging_studies_confirmation_event UNIQUE (confirmation_event_id),
    ADD CONSTRAINT uk_imaging_studies_release_event UNIQUE (release_event_id),
    ADD CONSTRAINT fk_imaging_studies_service_order FOREIGN KEY (service_order_id)
        REFERENCES clinical_service_orders (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_imaging_studies_visit FOREIGN KEY (visit_id)
        REFERENCES visits (id) ON DELETE RESTRICT;

CREATE INDEX ix_imaging_studies_service_order ON imaging_studies (service_order_id);
CREATE INDEX ix_imaging_studies_visit ON imaging_studies (visit_id);
CREATE INDEX ix_imaging_studies_confirm_due ON imaging_studies (next_confirmation_attempt_at) WHERE state = 'CONFIRMATION_PENDING';
CREATE INDEX ix_imaging_studies_reconciliation ON imaging_studies (updated_at, id) WHERE state = 'RECONCILIATION_REQUIRED';
CREATE INDEX ix_imaging_studies_dicom_index ON imaging_studies USING gin (dicom_index jsonb_path_ops);

-- Reconciliation cases keep their table; their operation id is now the study id (same value).
ALTER TABLE imaging_reconciliation_cases DROP CONSTRAINT imaging_reconciliation_cases_operation_id_fkey;
ALTER TABLE imaging_reconciliation_cases
    ADD CONSTRAINT imaging_reconciliation_cases_operation_id_fkey FOREIGN KEY (operation_id)
        REFERENCES imaging_studies (id) ON DELETE RESTRICT;

-- 4.4 Clinical order updated_at is maintained by the database, as the diagnostic request was.
CREATE FUNCTION touch_service_order_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = clock_timestamp(); RETURN NEW; END $$;
CREATE TRIGGER tr_clinical_orders_updated_at BEFORE UPDATE ON clinical_service_orders
  FOR EACH ROW EXECUTE FUNCTION touch_service_order_updated_at();

