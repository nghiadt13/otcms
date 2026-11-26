-- Return to the product schema name after the immutable V147/V148 migrations.
ALTER TABLE clinical_service_orders RENAME TO service_orders;
