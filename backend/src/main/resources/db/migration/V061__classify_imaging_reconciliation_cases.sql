ALTER TABLE imaging_reconciliation_cases
    ADD COLUMN classification VARCHAR(24);

UPDATE imaging_reconciliation_cases
   SET classification = CASE
       WHEN safe_reason_code ~ '(CONFLICT|MISMATCH|DUPLICATE|REJECTED)' THEN 'CONFLICT'
       WHEN safe_reason_code ~ '(UNAVAILABLE|RETRY)' THEN 'RETRYABLE'
       ELSE 'ORPHANED'
   END;

ALTER TABLE imaging_reconciliation_cases
    ALTER COLUMN classification SET NOT NULL,
    ALTER COLUMN classification SET DEFAULT 'ORPHANED',
    ADD CONSTRAINT ck_imaging_recon_classification
        CHECK (classification IN ('LINKED', 'RETRYABLE', 'ORPHANED', 'CONFLICT'));

CREATE INDEX ix_imaging_recon_classification_queue
    ON imaging_reconciliation_cases(classification, state, updated_at, id);
