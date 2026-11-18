ALTER TABLE diagnostic_reports
    ADD COLUMN lab_doctor_assessment text,
    ADD COLUMN lab_doctor_assessed_by_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    ADD COLUMN lab_doctor_assessed_at timestamptz,
    ADD CONSTRAINT ck_lab_doctor_assessment CHECK (
        (lab_doctor_assessment IS NULL AND lab_doctor_assessed_by_account_id IS NULL AND lab_doctor_assessed_at IS NULL)
        OR (result_type = 'LAB' AND state = 'COMPLETED'
            AND lab_doctor_assessment IS NOT NULL AND length(btrim(lab_doctor_assessment)) BETWEEN 1 AND 4000
            AND lab_doctor_assessed_by_account_id IS NOT NULL AND lab_doctor_assessed_at IS NOT NULL)
    );
