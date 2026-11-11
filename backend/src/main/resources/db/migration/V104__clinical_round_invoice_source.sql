ALTER TABLE invoices ADD COLUMN clinical_ordering_round_id UUID
    REFERENCES clinical_ordering_rounds(id) ON DELETE RESTRICT;
ALTER TABLE invoices DROP CONSTRAINT ck_invoices_source_type;
ALTER TABLE invoices DROP CONSTRAINT ck_invoices_source;
ALTER TABLE invoices ADD CONSTRAINT ck_invoices_source_type
    CHECK (source_type IN ('VISIT', 'APPOINTMENT', 'DIAGNOSTIC', 'CLINICAL_ROUND'));
ALTER TABLE invoices ADD CONSTRAINT ck_invoices_source CHECK (
    (source_type = 'VISIT' AND visit_id IS NOT NULL AND appointment_id IS NULL AND clinical_ordering_round_id IS NULL)
    OR (source_type = 'DIAGNOSTIC' AND visit_id IS NOT NULL AND appointment_id IS NULL AND clinical_ordering_round_id IS NULL)
    OR (source_type = 'APPOINTMENT' AND appointment_id IS NOT NULL AND visit_id IS NULL AND clinical_ordering_round_id IS NULL)
    OR (source_type = 'CLINICAL_ROUND' AND clinical_ordering_round_id IS NOT NULL AND visit_id IS NULL AND appointment_id IS NULL)
);
CREATE UNIQUE INDEX uk_invoices_clinical_round_source ON invoices(clinical_ordering_round_id)
    WHERE source_type = 'CLINICAL_ROUND';
-- Preserve Clinical service names (up to 255 characters) without truncating invoice snapshots.
ALTER TABLE invoice_items ALTER COLUMN description_snapshot TYPE VARCHAR(255);
