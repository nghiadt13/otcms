-- Rename 11 clinical_* / imaging_* tables to short names (plan 261122-2307, phase 1).
-- Pure RENAME: columns, data, foreign keys and indexes follow the table; constraint and index names keep their old text.
-- clinical_medical_document_payloads is not renamed (removed in the storage phase).
ALTER TABLE clinical_examinations RENAME TO examinations;
ALTER TABLE clinical_examination_diagnoses RENAME TO examination_diagnoses;
ALTER TABLE clinical_prescriptions RENAME TO prescriptions;
ALTER TABLE clinical_prescription_items RENAME TO prescription_items;
ALTER TABLE clinical_medical_documents RENAME TO medical_documents;
ALTER TABLE clinical_ordering_rounds RENAME TO service_order_rounds;
ALTER TABLE clinical_service_orders RENAME TO service_orders;
ALTER TABLE imaging_order_executions RENAME TO imaging_executions;
ALTER TABLE imaging_study_releases RENAME TO study_releases;
ALTER TABLE imaging_viewer_grants RENAME TO study_viewer_grants;
ALTER TABLE imaging_reconciliation_cases RENAME TO study_reconciliation_cases;
