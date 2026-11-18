-- Store the physician's clinical note on the clinical service order, not on the immutable result report.
ALTER TABLE clinical_service_orders
    ADD COLUMN doctor_result_assessment text,
    ADD COLUMN doctor_result_assessed_by_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    ADD COLUMN doctor_result_assessed_at timestamptz,
    ADD CONSTRAINT ck_clinical_order_result_assessment CHECK (
        (doctor_result_assessment IS NULL AND doctor_result_assessed_by_account_id IS NULL AND doctor_result_assessed_at IS NULL)
        OR (doctor_result_assessment IS NOT NULL
            AND length(btrim(doctor_result_assessment)) BETWEEN 1 AND 4000
            AND doctor_result_assessed_by_account_id IS NOT NULL
            AND doctor_result_assessed_at IS NOT NULL)
    );

-- Preserve any assessment that may have been recorded before the report immutability trigger blocked it.
WITH latest_assessment AS (
    SELECT DISTINCT ON (service_order_id)
           service_order_id, lab_doctor_assessment, lab_doctor_assessed_by_account_id, lab_doctor_assessed_at
      FROM diagnostic_reports
     WHERE result_type = 'LAB' AND lab_doctor_assessment IS NOT NULL
     ORDER BY service_order_id, version_number DESC
)
UPDATE clinical_service_orders order_row
   SET doctor_result_assessment = latest_assessment.lab_doctor_assessment,
       doctor_result_assessed_by_account_id = latest_assessment.lab_doctor_assessed_by_account_id,
       doctor_result_assessed_at = latest_assessment.lab_doctor_assessed_at
  FROM latest_assessment
 WHERE order_row.id = latest_assessment.service_order_id
   AND order_row.doctor_result_assessment IS NULL;

ALTER TABLE diagnostic_reports
    DROP CONSTRAINT ck_lab_doctor_assessment,
    DROP COLUMN lab_doctor_assessment,
    DROP COLUMN lab_doctor_assessed_by_account_id,
    DROP COLUMN lab_doctor_assessed_at;

-- Restore the original immutability guard; physician notes no longer update diagnostic_reports.
CREATE OR REPLACE FUNCTION prevent_result_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.state = 'COMPLETED' OR OLD.released_at IS NOT NULL THEN
      RAISE EXCEPTION 'released or completed results are immutable';
    END IF;
    RETURN OLD;
  END IF;
  IF OLD.released_at IS NOT NULL THEN
    RAISE EXCEPTION 'released or completed results are immutable';
  END IF;
  IF OLD.state = 'COMPLETED' AND (to_jsonb(OLD) - 'released_at') IS DISTINCT FROM (to_jsonb(NEW) - 'released_at') THEN
    RAISE EXCEPTION 'completed diagnostic findings versions are immutable';
  END IF;
  RETURN NEW;
END $$;
