-- Billing line items refer to Clinical orders through an owner-service contract, not a cross-feature FK.
ALTER TABLE invoices DROP CONSTRAINT ck_invoices_source_type;
ALTER TABLE invoices ADD CONSTRAINT ck_invoices_source_type
    CHECK (source_type IN ('VISIT', 'APPOINTMENT', 'DIAGNOSTIC'));
ALTER TABLE invoices DROP CONSTRAINT ck_invoices_source;
ALTER TABLE invoices ADD CONSTRAINT ck_invoices_source CHECK (
    (source_type IN ('VISIT', 'DIAGNOSTIC') AND visit_id IS NOT NULL AND appointment_id IS NULL)
    OR (source_type = 'APPOINTMENT' AND appointment_id IS NOT NULL AND visit_id IS NULL)
);
ALTER TABLE invoices ALTER COLUMN status TYPE varchar(24);
ALTER TABLE invoices DROP CONSTRAINT ck_invoices_status;
ALTER TABLE invoices ADD CONSTRAINT ck_invoices_status
    CHECK (status IN ('OPEN', 'PAID', 'PARTIALLY_REFUNDED', 'REFUNDED', 'VOID'));
CREATE UNIQUE INDEX uk_invoices_diagnostic_visit ON invoices(visit_id) WHERE source_type = 'DIAGNOSTIC';

ALTER TABLE invoice_items ADD COLUMN clinical_order_id uuid;
CREATE UNIQUE INDEX uk_invoice_items_clinical_order
    ON invoice_items (clinical_order_id) WHERE clinical_order_id IS NOT NULL;

CREATE TABLE diagnostic_item_refund_requests (
    id uuid PRIMARY KEY,
    invoice_id uuid NOT NULL,
    line_number integer NOT NULL,
    clinical_order_id uuid NOT NULL,
    incident_id uuid NOT NULL,
    requested_by_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    idempotency_key varchar(128) NOT NULL,
    request_fingerprint char(64) NOT NULL,
    reason text NOT NULL,
    status varchar(32) NOT NULL,
    allocated_amount numeric(14,2) NOT NULL,
    approved_by_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    decision_reason text,
    provider_reference varchar(128),
    requested_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    decided_at timestamptz,
    refunded_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_diagnostic_refund_invoice_item FOREIGN KEY (invoice_id, line_number)
        REFERENCES invoice_items(invoice_id, line_number) ON DELETE RESTRICT,
    CONSTRAINT uq_diagnostic_refund_request_idempotency UNIQUE (requested_by_account_id, idempotency_key),
    CONSTRAINT ck_diagnostic_refund_status CHECK (status IN (
        'PENDING_ADMIN_REVIEW', 'APPROVED', 'REJECTED', 'REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED'
    )),
    CONSTRAINT ck_diagnostic_refund_amount CHECK (allocated_amount > 0),
    CONSTRAINT ck_diagnostic_refund_version CHECK (version >= 0),
    CONSTRAINT ck_diagnostic_refund_reason CHECK (length(trim(reason)) > 0)
);
CREATE INDEX ix_diagnostic_refund_review
    ON diagnostic_item_refund_requests(status, requested_at, id);
CREATE UNIQUE INDEX uq_diagnostic_refund_active_item
    ON diagnostic_item_refund_requests(invoice_id, line_number)
    WHERE status IN ('PENDING_ADMIN_REVIEW', 'APPROVED', 'REFUND_PENDING', 'REFUNDED');

-- A newly registered imaging order cannot be dispatched until Billing confirms its whole invoice.
ALTER TABLE diagnostic_requests DROP CONSTRAINT ck_diagnostic_requests_status;
ALTER TABLE diagnostic_requests ADD CONSTRAINT ck_diagnostic_requests_status CHECK (status IN (
    'PENDING_PAYMENT', 'PENDING', 'IN_PROGRESS', 'RESULT_AVAILABLE', 'REVIEWED', 'CANCELLED'
));
ALTER TABLE diagnostic_requests DROP CONSTRAINT ck_diagnostic_requests_attempt_state;
ALTER TABLE diagnostic_requests ADD CONSTRAINT ck_diagnostic_requests_attempt_state CHECK (
    (status IN ('PENDING_PAYMENT', 'PENDING', 'CANCELLED') AND current_attempt_id IS NULL)
    OR (status IN ('IN_PROGRESS', 'RESULT_AVAILABLE', 'REVIEWED') AND current_attempt_id IS NOT NULL)
);
ALTER TABLE diagnostic_requests DROP CONSTRAINT ck_diagnostic_request_assigned_before_use;
ALTER TABLE diagnostic_requests ADD CONSTRAINT ck_diagnostic_request_assigned_before_use
    CHECK (room_id IS NOT NULL OR status IN ('PENDING_PAYMENT', 'PENDING', 'CANCELLED'));

ALTER TABLE diagnostic_requests
    ADD COLUMN billing_invoice_id uuid,
    ADD COLUMN billing_paid_event_id uuid,
    ADD COLUMN billing_refund_event_id uuid;
CREATE UNIQUE INDEX uk_diagnostic_requests_billing_invoice_order
    ON diagnostic_requests (billing_invoice_id, service_order_id)
    WHERE billing_invoice_id IS NOT NULL AND service_order_id IS NOT NULL;
CREATE UNIQUE INDEX uk_diagnostic_requests_billing_refund_event
    ON diagnostic_requests (billing_refund_event_id) WHERE billing_refund_event_id IS NOT NULL;
