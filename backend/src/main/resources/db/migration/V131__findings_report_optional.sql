-- UC-50 (R3): the PDF report of imaging findings is optional. A COMPLETED / released findings version no longer
-- needs a report; when a report is attached it must still be VALIDATED. Replaces the V118 rules ck_results_completed
-- and ck_results_released. No data change: every existing COMPLETED row already has a validated report.
-- The LAB branch keeps the V121 laboratory rule unchanged (lab results still need a validated report).
ALTER TABLE diagnostic_reports DROP CONSTRAINT ck_results_completed;
ALTER TABLE diagnostic_reports ADD CONSTRAINT ck_results_completed CHECK (state <> 'COMPLETED' OR (
    completed_at IS NOT NULL
    AND ((result_type = 'IMAGING' AND findings_text IS NOT NULL
            AND (report_id IS NULL OR validation_status = 'VALIDATED'))
        OR (result_type = 'LAB' AND report_id IS NOT NULL AND validation_status = 'VALIDATED'
            AND lab_sample_id IS NOT NULL AND lab_recorded_by_account_id IS NOT NULL
            AND lab_confirmed_by_account_id IS NOT NULL AND lab_confirmed_at IS NOT NULL
            AND lab_report_content_type IS NOT NULL AND lab_values IS NOT NULL AND jsonb_typeof(lab_values) = 'array'
            AND jsonb_array_length(lab_values) > 0))));

ALTER TABLE diagnostic_reports DROP CONSTRAINT ck_results_released;
ALTER TABLE diagnostic_reports ADD CONSTRAINT ck_results_released CHECK (released_at IS NULL OR (
    state = 'COMPLETED' AND (report_id IS NULL OR validation_status = 'VALIDATED')));
