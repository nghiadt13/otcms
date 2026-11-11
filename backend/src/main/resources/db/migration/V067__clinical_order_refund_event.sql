ALTER TABLE clinical_service_orders ADD COLUMN billing_refund_event_id uuid;
CREATE UNIQUE INDEX uk_clinical_service_orders_refund_event
    ON clinical_service_orders(billing_refund_event_id)
    WHERE billing_refund_event_id IS NOT NULL;
