-- UC-48 (R3) has no partner step: the sample is collected, the technician enters the analyzer values,
-- approves them and the doctor reads the result. Removes the partner directory created by V123-V124,
-- the SENT status, the shipment columns, the partner report code/issuer and the rejection responsibility column.
-- V121-V133 are not edited.

-- 1. Orders already shipped to a partner (SENT) continue as COLLECTED; the sample itself is kept.
UPDATE clinical_service_orders SET lab_status = 'COLLECTED' WHERE service_type = 'LABORATORY' AND lab_status = 'SENT';

-- 2. Laboratory status without SENT (replaces ck_clinical_lab_status from V121).
ALTER TABLE clinical_service_orders DROP CONSTRAINT ck_clinical_lab_status;
ALTER TABLE clinical_service_orders ADD CONSTRAINT ck_clinical_lab_status CHECK (lab_status IS NULL OR
    (service_type = 'LABORATORY' AND lab_status IN ('ASSIGNED', 'COLLECTED', 'REJECTED', 'RESULT_AVAILABLE')));

-- 3. laboratory_samples: drop the partner and shipment columns and the rejection responsibility column.
--    ck_lab_sample_rejected is rebuilt without responsible_party.
ALTER TABLE laboratory_samples DROP CONSTRAINT ck_lab_sample_sent;
ALTER TABLE laboratory_samples DROP CONSTRAINT fk_laboratory_samples_partner;
ALTER TABLE laboratory_samples DROP CONSTRAINT ck_lab_sample_rejected;
ALTER TABLE laboratory_samples DROP CONSTRAINT ck_lab_sample_responsible_party;
ALTER TABLE laboratory_samples
    DROP COLUMN partner_id,
    DROP COLUMN partner_name,
    DROP COLUMN sent_by_account_id,
    DROP COLUMN sent_at,
    DROP COLUMN responsible_party;
ALTER TABLE laboratory_samples ADD CONSTRAINT ck_lab_sample_rejected CHECK (
    (rejected_at IS NULL AND rejected_by_account_id IS NULL AND rejection_reason IS NULL AND rejection_source IS NULL)
    OR (rejected_at IS NOT NULL AND rejected_by_account_id IS NOT NULL AND rejected_at >= collected_at
        AND rejection_reason IS NOT NULL AND length(btrim(rejection_reason)) > 0
        AND rejection_source IS NOT NULL AND length(btrim(rejection_source)) > 0));

-- 4. diagnostic_reports: the partner report code and issuer are no longer recorded.
--    No check constraint references them (ck_results_completed, ck_results_released and ck_result_lab_content_type
--    use lab_sample_id, report_id, validation_status and lab_report_content_type only).
ALTER TABLE diagnostic_reports DROP COLUMN lab_partner_report_code, DROP COLUMN lab_report_issuer;

-- 5. Partner directory: no code path reads it after this migration (child table first).
DROP TABLE partner_category_assignments;
DROP TABLE partner_categories;
DROP TABLE partners;
