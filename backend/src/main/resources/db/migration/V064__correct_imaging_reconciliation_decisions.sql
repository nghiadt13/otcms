ALTER TABLE imaging_reconciliation_cases
    DROP CONSTRAINT ck_imaging_recon_resolution_decision;

-- V063 ran before an operator decision endpoint existed; do not infer decisions for historic cases.
UPDATE imaging_reconciliation_cases
   SET resolution_decision = NULL
 WHERE state = 'RESOLVED'
   AND resolution_decision = 'EXTERNAL_RESOLUTION_CONFIRMED';

ALTER TABLE imaging_reconciliation_cases
    ADD CONSTRAINT ck_imaging_recon_resolution_decision
        CHECK (resolution_decision IS NULL OR resolution_decision IN (
            'KEEP_QUARANTINED', 'EXTERNAL_RESOLUTION_CONFIRMED', 'START_REVIEW'));
