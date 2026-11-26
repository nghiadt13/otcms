-- V147 and V148 are already-applied migrations that still target clinical_service_orders.
-- V143 renames that table for the product schema; retain the legacy name until those
-- immutable migrations have run on a clean database. V151 restores the product name.
ALTER TABLE service_orders RENAME TO clinical_service_orders;
