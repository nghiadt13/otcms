ALTER TABLE invoice_items
    ADD COLUMN id uuid NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN payment_attempt_id uuid REFERENCES payment_attempts(id),
    ADD COLUMN voided_at timestamptz,
    ADD COLUMN refund_status varchar(16),
    ADD COLUMN refund_reason text,
    ADD COLUMN refunded_at timestamptz,
    ADD COLUMN refunded_by_account_id uuid REFERENCES accounts(id),
    ADD CONSTRAINT uq_invoice_items_id UNIQUE (id),
    ADD CONSTRAINT ck_invoice_items_refund_status CHECK (refund_status IS NULL OR refund_status IN ('REFUND_PENDING', 'REFUNDED'));

CREATE INDEX ix_invoice_items_invoice_payment ON invoice_items(invoice_id, payment_attempt_id);

ALTER TABLE payment_attempts DROP CONSTRAINT ck_payment_attempt_channel;
ALTER TABLE payment_attempts ADD CONSTRAINT ck_payment_attempt_channel CHECK (channel IN ('COUNTER', 'ONLINE', 'COUNTER_QR'));
ALTER TABLE payment_attempts DROP CONSTRAINT ck_payment_attempt_counter;
ALTER TABLE payment_attempts ADD CONSTRAINT ck_payment_attempt_counter CHECK (
    (channel = 'COUNTER' AND counter_method IS NOT NULL AND provider IS NULL AND provider_order_code IS NULL)
    OR (channel IN ('ONLINE', 'COUNTER_QR') AND counter_method IS NULL AND provider IS NOT NULL AND provider_order_code IS NOT NULL)
);

-- Existing fully paid invoices have no item-level allocation; use the latest confirmed attempt.
UPDATE invoice_items item
SET payment_attempt_id = latest.id
FROM invoices invoice
JOIN LATERAL (
    SELECT attempt.id FROM payment_attempts attempt
    WHERE attempt.invoice_id = invoice.id AND attempt.status = 'CONFIRMED'
    ORDER BY attempt.confirmed_at DESC, attempt.id DESC LIMIT 1
) latest ON true
WHERE item.invoice_id = invoice.id AND invoice.status = 'PAID';
