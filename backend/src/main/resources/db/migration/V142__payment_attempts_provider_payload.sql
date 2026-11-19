-- V142: gom 7 cột riêng của PayOS trong payment_attempts vào provider_payload jsonb (bỏ key null).
-- Giữ nguyên provider_order_code, provider_transaction_reference (có unique) và last_event_fingerprint.
-- Key trong jsonb: paymentLinkId, checkoutUrl, qrCode, transferBankBin, transferAccountNumber, transferAccountName, transferDescription.

ALTER TABLE payment_attempts ADD COLUMN provider_payload jsonb;

UPDATE payment_attempts
SET provider_payload = jsonb_strip_nulls(jsonb_build_object(
        'paymentLinkId', provider_payment_link_id,
        'checkoutUrl', checkout_url,
        'qrCode', qr_code,
        'transferBankBin', transfer_bank_bin,
        'transferAccountNumber', transfer_account_number,
        'transferAccountName', transfer_account_name,
        'transferDescription', transfer_description))
WHERE provider_payment_link_id IS NOT NULL OR checkout_url IS NOT NULL OR qr_code IS NOT NULL
   OR transfer_bank_bin IS NOT NULL OR transfer_account_number IS NOT NULL
   OR transfer_account_name IS NOT NULL OR transfer_description IS NOT NULL;

DO $$
DECLARE expected bigint; actual bigint;
BEGIN
    SELECT count(*) INTO expected FROM payment_attempts
    WHERE provider_payment_link_id IS NOT NULL OR checkout_url IS NOT NULL OR qr_code IS NOT NULL
       OR transfer_bank_bin IS NOT NULL OR transfer_account_number IS NOT NULL
       OR transfer_account_name IS NOT NULL OR transfer_description IS NOT NULL;
    SELECT count(*) INTO actual FROM payment_attempts WHERE provider_payload IS NOT NULL;
    IF expected <> actual THEN RAISE EXCEPTION 'V142 copy check: % payloads expected, % built', expected, actual; END IF;
END $$;

ALTER TABLE payment_attempts
    DROP COLUMN provider_payment_link_id,
    DROP COLUMN checkout_url,
    DROP COLUMN qr_code,
    DROP COLUMN transfer_bank_bin,
    DROP COLUMN transfer_account_number,
    DROP COLUMN transfer_account_name,
    DROP COLUMN transfer_description;
