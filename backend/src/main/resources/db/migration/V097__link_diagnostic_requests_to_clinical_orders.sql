-- Links an imaging request (diagnostic module: where/when the study is performed) to the
-- clinical service order (clinical module: what the doctor ordered, price snapshot, visit
-- completion evidence). No foreign key on purpose: the modules own separate tables and talk
-- through ports. Nullable so requests created without an order keep working.
ALTER TABLE diagnostic_requests
    ADD COLUMN service_order_id uuid;

-- One live request per order; a cancelled request frees the order for a new request.
CREATE UNIQUE INDEX uk_diagnostic_requests_active_service_order
    ON diagnostic_requests (service_order_id)
    WHERE service_order_id IS NOT NULL AND status <> 'CANCELLED';
