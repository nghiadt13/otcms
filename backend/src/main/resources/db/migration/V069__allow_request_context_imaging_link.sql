-- Technician upload context may establish the study/request association even when
-- DICOM patient and accession tags are absent or differ from the OTCMS request.
ALTER TABLE imaging_ingest_operations DROP CONSTRAINT ck_imaging_ops_verification;
ALTER TABLE imaging_ingest_operations ADD CONSTRAINT ck_imaging_ops_verification CHECK (verification_status IN (
    'NOT_EVALUATED', 'CONFIRMED_MATCH', 'REQUEST_CONTEXT_MATCH', 'MISMATCH', 'UNKNOWN'));

ALTER TABLE imaging_studies DROP CONSTRAINT ck_imaging_studies_verification;
ALTER TABLE imaging_studies ADD CONSTRAINT ck_imaging_studies_verification CHECK (
    verification_status IN ('CONFIRMED_MATCH', 'REQUEST_CONTEXT_MATCH'));

ALTER TABLE imaging_confirmation_outbox DROP CONSTRAINT ck_imaging_outbox_verification;
ALTER TABLE imaging_confirmation_outbox ADD CONSTRAINT ck_imaging_outbox_verification CHECK (
    verification_status IN ('CONFIRMED_MATCH', 'REQUEST_CONTEXT_MATCH'));
