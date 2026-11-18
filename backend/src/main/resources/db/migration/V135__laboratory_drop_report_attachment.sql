-- UC-48 (R3): a laboratory result is the set of indicator values on the form; the partner report file is no longer
-- uploaded, stored or downloaded, so the technician's approval alone releases a LAB row. ck_results_completed is rebuilt
-- from the V131 rule without the LAB report requirement; the IMAGING branch is unchanged. ck_results_released (V131)
-- already accepts a released row without a report (report_id IS NULL), so it stays. Existing LAB rows keep any old
-- report columns (report_id, report_storage_ref, report_sha256, validation_status): no data change.
ALTER TABLE diagnostic_reports DROP CONSTRAINT ck_results_completed;
ALTER TABLE diagnostic_reports ADD CONSTRAINT ck_results_completed CHECK (state <> 'COMPLETED' OR (
    completed_at IS NOT NULL
    AND ((result_type = 'IMAGING' AND findings_text IS NOT NULL
            AND (report_id IS NULL OR validation_status = 'VALIDATED'))
        OR (result_type = 'LAB' AND lab_sample_id IS NOT NULL AND lab_recorded_by_account_id IS NOT NULL
            AND lab_confirmed_by_account_id IS NOT NULL AND lab_confirmed_at IS NOT NULL
            AND lab_values IS NOT NULL AND jsonb_typeof(lab_values) = 'array'
            AND jsonb_array_length(lab_values) > 0))));

ALTER TABLE diagnostic_reports DROP CONSTRAINT ck_result_lab_content_type;

ALTER TABLE diagnostic_reports
    DROP COLUMN lab_report_content_type,
    DROP COLUMN lab_report_received_at,
    DROP COLUMN lab_report_uploaded_by_account_id;
