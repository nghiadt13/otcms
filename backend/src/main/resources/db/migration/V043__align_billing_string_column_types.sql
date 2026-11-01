ALTER TABLE invoices
    ALTER COLUMN source_fingerprint TYPE VARCHAR(64)
    USING btrim(source_fingerprint)::VARCHAR(64);

ALTER TABLE payment_attempts
    ALTER COLUMN currency_code DROP DEFAULT,
    ALTER COLUMN currency_code TYPE VARCHAR(3)
    USING btrim(currency_code)::VARCHAR(3),
    ALTER COLUMN currency_code SET DEFAULT 'VND',
    ALTER COLUMN request_fingerprint TYPE VARCHAR(64)
    USING btrim(request_fingerprint)::VARCHAR(64);

ALTER TABLE payment_gateway_events
    ALTER COLUMN event_fingerprint TYPE VARCHAR(64)
    USING btrim(event_fingerprint)::VARCHAR(64);
