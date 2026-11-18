ALTER TABLE clinical_medical_documents
    ADD COLUMN status varchar(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REPLACED')),
    ADD COLUMN source varchar(16) NOT NULL DEFAULT 'UPLOADED' CHECK (source IN ('GENERATED', 'UPLOADED'));

-- Old data may hold several reports per visit: keep the newest ACTIVE, mark earlier ones REPLACED so the unique index can be built.
UPDATE clinical_medical_documents d
SET status = 'REPLACED'
WHERE d.document_type = 'CLINIC_EXAMINATION_REPORT'
  AND EXISTS (
      SELECT 1 FROM clinical_medical_documents n
      WHERE n.visit_id = d.visit_id
        AND n.document_type = 'CLINIC_EXAMINATION_REPORT'
        AND (n.created_at, n.id) > (d.created_at, d.id));

CREATE UNIQUE INDEX uq_clinical_active_result_sheet_visit
    ON clinical_medical_documents (visit_id)
    WHERE document_type = 'CLINIC_EXAMINATION_REPORT' AND status = 'ACTIVE';
