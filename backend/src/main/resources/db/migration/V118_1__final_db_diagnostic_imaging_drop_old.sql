-- final-db phase 03b (V118.1): drop the tables, constraints and functions that V118 copied away from.
-- Runs after V118 (phase 03a). Same statements as the former section 5 of V118.

-- =====================================================================
-- 5. Drop old objects (order follows the foreign keys).
-- =====================================================================

-- 5.1 Tables whose rows were copied above, or that the phase drops (outbox, idempotency, receipts).
DROP TABLE diagnostic_outbox;
DROP TABLE diagnostic_idempotency_records;
DROP TABLE diagnostic_audit_events;
DROP TABLE diagnostic_history_access_audits;
DROP TABLE diagnostic_findings_versions;
DROP TABLE visit_imaging_result_receipts;
DROP TABLE imaging_study_instances;
DROP TABLE imaging_study_series;
DROP TABLE imaging_uid_claims;
DROP TABLE imaging_idempotency_records;
DROP TABLE imaging_confirmation_outbox;
DROP TABLE imaging_release_inbox;
DROP TABLE imaging_audit_events;

-- Operation id is the study id now; the operation table goes.
ALTER TABLE imaging_studies DROP COLUMN ingest_operation_id;
DROP TABLE imaging_ingest_operations;

-- The report's old (request_id, id) key was for the findings foreign key, which is gone.
ALTER TABLE diagnostic_reports DROP CONSTRAINT uq_diagnostic_reports_request_id;

-- DEVIATION c): diagnostic_requests and diagnostic_attempts reference each other. The spec order
-- (attempts before requests) fails, so the request side of the cycle is dropped first.
ALTER TABLE diagnostic_requests DROP CONSTRAINT fk_diagnostic_requests_current_attempt;
DROP TABLE diagnostic_attempts;
DROP TABLE diagnostic_requests;
DROP TABLE clinical_imaging_request_replays;
DROP TABLE clinical_ordering_rounds;

-- 5.2 Trigger functions of the dropped rules (their triggers left with the tables above).
DROP FUNCTION enforce_diagnostic_findings_scope();
DROP FUNCTION enforce_diagnostic_report_scope();
DROP FUNCTION enforce_diagnostic_request_study_state();
DROP FUNCTION prevent_completed_diagnostic_findings_mutation();
DROP FUNCTION prevent_used_diagnostic_report_mutation();
DROP FUNCTION touch_diagnostic_request_updated_at();
DROP FUNCTION reject_visit_imaging_result_receipt_mutation();
