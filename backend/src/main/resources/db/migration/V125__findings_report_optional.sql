-- UC-50 (R3): the PDF report of imaging findings is optional. A COMPLETED / released findings version no longer
-- needs a report; when a report is attached it must still be VALIDATED. Replaces the V118 rules ck_results_completed
-- and ck_results_released. No data change: every existing COMPLETED row already has a validated report.
ALTER TABLE diagnostic_reports DROP CONSTRAINT ck_results_completed;
ALTER TABLE diagnostic_reports ADD CONSTRAINT ck_results_completed CHECK (state <> 'COMPLETED' OR (
    findings_text IS NOT NULL AND completed_at IS NOT NULL
    AND (report_id IS NULL OR validation_status = 'VALIDATED')));

ALTER TABLE diagnostic_reports DROP CONSTRAINT ck_results_released;
ALTER TABLE diagnostic_reports ADD CONSTRAINT ck_results_released CHECK (released_at IS NULL OR (
    state = 'COMPLETED' AND (report_id IS NULL OR validation_status = 'VALIDATED')));
