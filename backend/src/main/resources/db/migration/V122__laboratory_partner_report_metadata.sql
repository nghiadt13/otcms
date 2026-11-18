ALTER TABLE diagnostic_reports
    ADD COLUMN lab_partner_report_code varchar(128),
    ADD COLUMN lab_report_issuer varchar(255),
    ADD COLUMN lab_report_received_at timestamptz,
    ADD COLUMN lab_report_uploaded_by_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT;
