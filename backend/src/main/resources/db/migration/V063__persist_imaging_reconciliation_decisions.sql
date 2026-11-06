ALTER TABLE imaging_reconciliation_cases
    ADD COLUMN resolution_decision VARCHAR(40);

UPDATE imaging_reconciliation_cases
   SET resolution_decision = 'EXTERNAL_RESOLUTION_CONFIRMED'
 WHERE state = 'RESOLVED';

ALTER TABLE imaging_reconciliation_cases
    ADD CONSTRAINT ck_imaging_recon_resolution_decision
        CHECK (resolution_decision IS NULL OR resolution_decision IN (
            'KEEP_QUARANTINED', 'EXTERNAL_RESOLUTION_CONFIRMED', 'ESCALATE'));
