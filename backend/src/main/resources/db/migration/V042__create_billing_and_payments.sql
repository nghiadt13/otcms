CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    invoice_number VARCHAR(48) NOT NULL UNIQUE,
    patient_profile_id UUID NOT NULL REFERENCES patient_profiles(id) ON DELETE RESTRICT,
    visit_id UUID REFERENCES visits(id) ON DELETE RESTRICT,
    appointment_id UUID REFERENCES appointments(id) ON DELETE RESTRICT,
    source_type VARCHAR(16) NOT NULL,
    source_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_by_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_invoices_source_type CHECK (source_type IN ('VISIT', 'APPOINTMENT')),
    CONSTRAINT ck_invoices_source CHECK (
        (source_type = 'VISIT' AND visit_id IS NOT NULL AND appointment_id IS NULL)
        OR (source_type = 'APPOINTMENT' AND appointment_id IS NOT NULL AND visit_id IS NULL)
    ),
    CONSTRAINT ck_invoices_status CHECK (status IN ('OPEN', 'PAID', 'VOID')),
    CONSTRAINT ck_invoices_version CHECK (version >= 0),
    CONSTRAINT ck_invoices_source_fingerprint CHECK (source_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX uk_invoices_visit_source ON invoices(visit_id) WHERE source_type = 'VISIT';
CREATE UNIQUE INDEX uk_invoices_appointment_source ON invoices(appointment_id) WHERE source_type = 'APPOINTMENT';
CREATE INDEX ix_invoices_patient_issued ON invoices(patient_profile_id, issued_at DESC, id);
CREATE INDEX ix_invoices_status_issued ON invoices(status, issued_at DESC, id);

CREATE TABLE invoice_items (
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    line_number INTEGER NOT NULL,
    service_id UUID REFERENCES clinic_services(id) ON DELETE RESTRICT,
    description_snapshot VARCHAR(240) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price_snapshot NUMERIC(14, 2) NOT NULL,
    PRIMARY KEY (invoice_id, line_number),
    CONSTRAINT ck_invoice_items_line CHECK (line_number > 0),
    CONSTRAINT ck_invoice_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_invoice_items_price CHECK (unit_price_snapshot >= 0)
);
CREATE INDEX ix_invoice_items_service ON invoice_items(service_id) WHERE service_id IS NOT NULL;

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    channel VARCHAR(16) NOT NULL,
    counter_method VARCHAR(24),
    provider VARCHAR(32),
    amount NUMERIC(14, 2) NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(32) NOT NULL,
    created_by_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    provider_order_code BIGINT,
    provider_payment_link_id VARCHAR(100),
    provider_transaction_reference VARCHAR(128),
    checkout_url VARCHAR(1000),
    qr_code TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    reconciliation_reason VARCHAR(500),
    CONSTRAINT ck_payment_attempt_channel CHECK (channel IN ('COUNTER', 'ONLINE')),
    CONSTRAINT ck_payment_attempt_counter CHECK (
        (channel = 'COUNTER' AND counter_method IS NOT NULL AND provider IS NULL AND provider_order_code IS NULL)
        OR (channel = 'ONLINE' AND counter_method IS NULL AND provider IS NOT NULL AND provider_order_code IS NOT NULL)
    ),
    CONSTRAINT ck_payment_attempt_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_attempt_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_attempt_status CHECK (status IN (
        'PENDING', 'CONFIRMED', 'FAILED', 'CANCELLED', 'EXPIRED', 'RECONCILIATION_REQUIRED'
    )),
    CONSTRAINT ck_payment_attempt_counter_method CHECK (
        counter_method IS NULL OR counter_method IN ('CASH', 'BANK_TRANSFER', 'OTHER')
    ),
    CONSTRAINT ck_payment_attempt_confirmation CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL)),
    CONSTRAINT ck_payment_attempt_idempotency CHECK (
        length(trim(idempotency_key)) BETWEEN 1 AND 128 AND request_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT uq_payment_attempt_actor_key UNIQUE (created_by_account_id, idempotency_key),
    CONSTRAINT uq_payment_provider_order UNIQUE (provider, provider_order_code),
    CONSTRAINT uq_payment_provider_transaction UNIQUE (provider, provider_transaction_reference)
);
CREATE INDEX ix_payment_attempts_invoice_state ON payment_attempts(invoice_id, status, created_at DESC, id);
CREATE INDEX ix_payment_attempts_reconcile ON payment_attempts(status, created_at, id)
    WHERE status = 'RECONCILIATION_REQUIRED';
CREATE UNIQUE INDEX uk_payment_attempt_active_online ON payment_attempts(invoice_id)
    WHERE channel = 'ONLINE' AND status IN ('PENDING', 'RECONCILIATION_REQUIRED');

CREATE TABLE payment_gateway_events (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    event_fingerprint CHAR(64) NOT NULL,
    provider_order_code BIGINT,
    provider_transaction_reference VARCHAR(128),
    signature_verified BOOLEAN NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    outcome_detail VARCHAR(200),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_payment_gateway_event_fingerprint CHECK (event_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_payment_gateway_event_outcome CHECK (outcome IN (
        'APPLIED', 'DUPLICATE', 'REJECTED', 'RECONCILIATION_REQUIRED'
    )),
    CONSTRAINT uq_payment_gateway_event UNIQUE (provider, event_fingerprint)
);
CREATE INDEX ix_payment_gateway_events_received ON payment_gateway_events(received_at DESC, id);
