CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    normalized_email VARCHAR(254) NOT NULL,
    account_type VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    password_hash VARCHAR(255),
    email_verified_at TIMESTAMPTZ,
    failed_credential_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_accounts_normalized_email UNIQUE (normalized_email),
    CONSTRAINT ck_accounts_type CHECK (account_type IN ('PATIENT', 'STAFF')),
    CONSTRAINT ck_accounts_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'TEMPORARILY_LOCKED', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_accounts_failed_credentials CHECK (failed_credential_count >= 0)
);

CREATE INDEX ix_accounts_status_created ON accounts (status, created_at);

CREATE TABLE patient_profiles (
    account_id UUID PRIMARY KEY REFERENCES accounts(id),
    full_name VARCHAR(100) NOT NULL,
    phone_canonical VARCHAR(16) NOT NULL,
    phone_owner_type VARCHAR(16) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(16) NOT NULL,
    CONSTRAINT ck_patient_profiles_phone_owner CHECK (phone_owner_type IN ('PATIENT', 'GUARDIAN')),
    CONSTRAINT ck_patient_profiles_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER'))
);

CREATE INDEX ix_patient_profiles_phone ON patient_profiles (phone_canonical);

CREATE TABLE external_identities (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    normalized_email_at_link VARCHAR(254) NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_external_identities_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT ck_external_identities_provider CHECK (provider IN ('GOOGLE'))
);

CREATE INDEX ix_external_identities_account ON external_identities (account_id);

CREATE TABLE consent_evidence (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    actor_type VARCHAR(16) NOT NULL,
    terms_version VARCHAR(64) NOT NULL,
    privacy_version VARCHAR(64) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_consent_evidence_actor CHECK (actor_type IN ('PATIENT', 'GUARDIAN')),
    CONSTRAINT uk_consent_evidence_version UNIQUE (account_id, actor_type, terms_version, privacy_version)
);

CREATE INDEX ix_consent_evidence_account ON consent_evidence (account_id);

CREATE TABLE email_verification_challenges (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    otp_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    superseded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_email_challenges_failed_attempts CHECK (failed_attempts BETWEEN 0 AND 5)
);

CREATE UNIQUE INDEX uk_email_challenges_current_account
    ON email_verification_challenges (account_id)
    WHERE consumed_at IS NULL AND superseded_at IS NULL;
CREATE INDEX ix_email_challenges_account_created
    ON email_verification_challenges (account_id, created_at DESC);
CREATE INDEX ix_email_challenges_expires_at ON email_verification_challenges (expires_at);
CREATE INDEX ix_email_challenges_consumed_at ON email_verification_challenges (consumed_at)
    WHERE consumed_at IS NOT NULL;

CREATE TABLE password_reset_challenges (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    superseded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_password_reset_failed_attempts CHECK (failed_attempts BETWEEN 0 AND 5)
);

CREATE UNIQUE INDEX uk_password_reset_current_account
    ON password_reset_challenges (account_id)
    WHERE consumed_at IS NULL AND superseded_at IS NULL;
CREATE INDEX ix_password_reset_account_created
    ON password_reset_challenges (account_id, created_at DESC);
CREATE INDEX ix_password_reset_expires_at ON password_reset_challenges (expires_at);
CREATE INDEX ix_password_reset_consumed_at ON password_reset_challenges (consumed_at)
    WHERE consumed_at IS NOT NULL;

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    token_family_id UUID NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    last_rotated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(64),
    client_ip VARCHAR(64),
    user_agent VARCHAR(512),
    CONSTRAINT uk_auth_sessions_refresh_hash UNIQUE (refresh_token_hash)
);

CREATE INDEX ix_auth_sessions_account_active
    ON auth_sessions (account_id, created_at, id)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_auth_sessions_token_family ON auth_sessions (token_family_id);
CREATE INDEX ix_auth_sessions_expires_at ON auth_sessions (expires_at);
CREATE INDEX ix_auth_sessions_revoked_at ON auth_sessions (revoked_at)
    WHERE revoked_at IS NOT NULL;

CREATE TABLE authentication_audits (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    method VARCHAR(64) NOT NULL,
    outcome VARCHAR(64) NOT NULL,
    account_id UUID REFERENCES accounts(id),
    identifier_hmac VARCHAR(255),
    correlation_id VARCHAR(128) NOT NULL,
    client_ip VARCHAR(64),
    user_agent VARCHAR(512),
    duration_ms BIGINT NOT NULL,
    CONSTRAINT ck_authentication_audits_duration CHECK (duration_ms >= 0),
    CONSTRAINT ck_authentication_audits_reference CHECK (account_id IS NOT NULL OR identifier_hmac IS NOT NULL)
);

CREATE INDEX ix_authentication_audits_occurred_at ON authentication_audits (occurred_at);
CREATE INDEX ix_authentication_audits_account ON authentication_audits (account_id, occurred_at DESC);
CREATE INDEX ix_authentication_audits_correlation ON authentication_audits (correlation_id);
