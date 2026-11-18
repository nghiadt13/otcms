-- One sample code per laboratory order and collection attempt; prior attempts remain traceable.
CREATE TABLE laboratory_samples (
    id uuid PRIMARY KEY,
    service_order_id uuid NOT NULL REFERENCES clinical_service_orders(id) ON DELETE RESTRICT,
    attempt_number integer NOT NULL CHECK (attempt_number > 0),
    sample_code varchar(128) NOT NULL UNIQUE CHECK (length(btrim(sample_code)) > 0),
    verified_by_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    verified_at timestamptz NOT NULL,
    collected_by_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    collected_at timestamptz NOT NULL,
    partner_name varchar(255),
    sent_by_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    sent_at timestamptz,
    rejected_by_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    rejected_at timestamptz,
    rejection_reason text,
    rejection_source text,
    responsible_party varchar(16),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_lab_sample_attempt UNIQUE (service_order_id, attempt_number),
    CONSTRAINT uq_lab_sample_order_id UNIQUE (service_order_id, id),
    CONSTRAINT ck_lab_sample_collection_time CHECK (collected_at >= verified_at),
    CONSTRAINT ck_lab_sample_sent CHECK (
        (sent_at IS NULL AND sent_by_account_id IS NULL AND partner_name IS NULL)
        OR (sent_at IS NOT NULL AND sent_by_account_id IS NOT NULL
            AND partner_name IS NOT NULL AND length(btrim(partner_name)) > 0 AND sent_at >= collected_at)),
    CONSTRAINT ck_lab_sample_rejected CHECK (
        (rejected_at IS NULL AND rejected_by_account_id IS NULL AND rejection_reason IS NULL AND rejection_source IS NULL AND responsible_party IS NULL)
        OR (rejected_at IS NOT NULL AND rejected_by_account_id IS NOT NULL AND rejected_at >= collected_at
            AND rejection_reason IS NOT NULL AND length(btrim(rejection_reason)) > 0
            AND rejection_source IS NOT NULL AND length(btrim(rejection_source)) > 0)),
    CONSTRAINT ck_lab_sample_responsible_party CHECK (responsible_party IS NULL OR responsible_party IN ('CLINIC', 'PARTNER'))
);

-- Imaging execution state remains governed by the existing imaging-only constraints.
ALTER TABLE clinical_service_orders
    ADD COLUMN lab_status varchar(24),
    ADD COLUMN lab_review_required boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT ck_clinical_lab_status CHECK (lab_status IS NULL OR
        (service_type = 'LABORATORY' AND lab_status IN ('ASSIGNED', 'COLLECTED', 'SENT', 'REJECTED', 'RESULT_AVAILABLE'))),
    ADD CONSTRAINT ck_clinical_lab_review CHECK (NOT lab_review_required OR service_type = 'LABORATORY');

ALTER TABLE diagnostic_reports
    ADD COLUMN lab_sample_id uuid,
    ADD COLUMN lab_recorded_by_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    ADD COLUMN lab_confirmed_by_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    ADD COLUMN lab_confirmed_at timestamptz,
    ADD COLUMN lab_correction_source text,
    ADD COLUMN lab_report_content_type varchar(64),
    ADD CONSTRAINT fk_result_lab_sample FOREIGN KEY (service_order_id, lab_sample_id)
        REFERENCES laboratory_samples(service_order_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_result_lab_content_type CHECK (lab_report_content_type IS NULL OR
        (result_type = 'LAB' AND lab_report_content_type IN ('application/pdf', 'image/jpeg', 'image/png')));

-- Preserve imaging requirements; a completed lab result has values, a validated report and a confirmer.
ALTER TABLE diagnostic_reports DROP CONSTRAINT ck_results_completed;
ALTER TABLE diagnostic_reports ADD CONSTRAINT ck_results_completed CHECK (state <> 'COMPLETED' OR (
    completed_at IS NOT NULL AND report_id IS NOT NULL AND validation_status = 'VALIDATED'
    AND ((result_type = 'IMAGING' AND findings_text IS NOT NULL)
        OR (result_type = 'LAB' AND lab_sample_id IS NOT NULL AND lab_recorded_by_account_id IS NOT NULL
            AND lab_confirmed_by_account_id IS NOT NULL AND lab_confirmed_at IS NOT NULL
            AND lab_report_content_type IS NOT NULL AND lab_values IS NOT NULL AND jsonb_typeof(lab_values) = 'array'
            AND jsonb_array_length(lab_values) > 0))));
