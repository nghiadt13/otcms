-- final-db phase 08 (payOS billing). Requires V109 (audit_logs).
-- Drops payment_gateway_events. Its replay guard moves to payment_attempts.last_event_fingerprint;
-- its rows move to audit_logs (hidden from the activity log; payload not copied).

-- 1. Replay guard columns. UNIQUE (provider, provider_order_code) already exists as
--    uq_payment_provider_order (V042), so it is not added again.
ALTER TABLE payment_attempts
    ADD COLUMN last_event_fingerprint varchar(64),
    ADD COLUMN signature_verified boolean,
    ADD CONSTRAINT ck_payment_attempt_event_fingerprint
        CHECK (last_event_fingerprint IS NULL OR last_event_fingerprint ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_payment_attempt_event_pair
        CHECK ((last_event_fingerprint IS NULL) = (signature_verified IS NULL));

-- 2. Latest verified event fingerprint per attempt.
--    DEVIATION: phase-08 (Architecture 5 and Risk) says "no backfill". This block follows the
--    staging brief instead. Delete this block to keep the spec behaviour.
--    DUPLICATE events never set the fingerprint (same rule as the phase-08 PaymentServiceImpl).
UPDATE payment_attempts pa
SET last_event_fingerprint = latest.event_fingerprint,
    signature_verified = TRUE
FROM (
    SELECT DISTINCT ON (e.provider, e.provider_order_code)
           e.provider, e.provider_order_code, e.event_fingerprint
    FROM payment_gateway_events e
    WHERE e.signature_verified
      AND e.outcome <> 'DUPLICATE'
      AND e.provider_order_code IS NOT NULL
    ORDER BY e.provider, e.provider_order_code, e.received_at DESC, e.id DESC
) latest
WHERE pa.provider = latest.provider
  AND pa.provider_order_code = latest.provider_order_code;

-- 3. Webhook history: one audit_logs row per old event, same id. entity_id is the matched
--    attempt, NULL when no attempt has that provider and order code.
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id,
                        visible_in_activity_log, actor_type, metadata, outcome)
SELECT e.id, e.received_at, 'payment_gateway_events', 'PAYMENT_GATEWAY', e.outcome, 'PAYMENT_WEBHOOK', pa.id,
       false, 'PAYOS',
       jsonb_build_object('provider', e.provider, 'fingerprint', e.event_fingerprint,
                          'orderCode', e.provider_order_code,
                          'transactionReference', e.provider_transaction_reference,
                          'signatureVerified', e.signature_verified, 'detail', e.outcome_detail),
       e.outcome
FROM payment_gateway_events e
LEFT JOIN payment_attempts pa ON pa.provider = e.provider AND pa.provider_order_code = e.provider_order_code;

-- 4. No trigger or function in V001-V108 references payment_gateway_events (checked by grep),
--    so there is nothing to drop before the table.
DROP TABLE payment_gateway_events;
