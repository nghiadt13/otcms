ALTER TABLE imaging_reconciliation_cases
    ADD COLUMN safe_evidence_sha256 CHAR(64);

ALTER TABLE imaging_reconciliation_cases
    ADD CONSTRAINT ck_imaging_recon_evidence_sha256
        CHECK (safe_evidence_sha256 IS NULL OR safe_evidence_sha256 ~ '^[0-9a-f]{64}$');
