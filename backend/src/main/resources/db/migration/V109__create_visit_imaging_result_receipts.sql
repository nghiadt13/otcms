-- Visit-owned durable result acknowledgement; no Diagnostic payload or queue model duplication.
CREATE TABLE visit_imaging_result_receipts (
    event_id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    visit_id UUID NOT NULL REFERENCES visits(id) ON DELETE RESTRICT,
    patient_profile_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL CHECK (event_type IN ('IMAGING_RESULT_READY', 'FINDINGS_RELEASE')),
    payload_sha256 VARCHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    received_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_visit_imaging_result_receipts_visit ON visit_imaging_result_receipts (visit_id, received_at, event_id);

CREATE FUNCTION reject_visit_imaging_result_receipt_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Visit imaging result receipts are immutable' USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER tr_visit_imaging_result_receipts_immutable BEFORE UPDATE OR DELETE
    ON visit_imaging_result_receipts FOR EACH ROW EXECUTE FUNCTION reject_visit_imaging_result_receipt_mutation();
CREATE TRIGGER tr_visit_imaging_result_receipts_no_truncate BEFORE TRUNCATE
    ON visit_imaging_result_receipts FOR EACH STATEMENT EXECUTE FUNCTION reject_visit_imaging_result_receipt_mutation();
