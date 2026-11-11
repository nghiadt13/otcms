-- Multiple upload operations may safely reference the same canonical archive study
-- after FEAT-004 verifies that the study still belongs to the same request attempt.
ALTER TABLE imaging_ingest_operations
    DROP CONSTRAINT imaging_ingest_operations_study_ref_key;

CREATE INDEX ix_imaging_ingest_operation_study_ref
    ON imaging_ingest_operations(study_ref)
    WHERE study_ref IS NOT NULL;
