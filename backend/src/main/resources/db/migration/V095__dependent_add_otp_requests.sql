-- UC13: adding a dependent is confirmed first with an email OTP sent to the requesting
-- account's own email. The request is held here until the code is verified; only then does
-- the existing add-dependent flow run, so no profile is created for an unverified request.
CREATE TABLE dependent_add_requests (
    id UUID PRIMARY KEY,
    requester_account_id UUID NOT NULL REFERENCES accounts(id),
    full_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(16) NOT NULL,
    national_id VARCHAR(20),
    identity_document_type VARCHAR(16),
    relationship VARCHAR(16) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts BETWEEN 0 AND 5),
    consumed_at TIMESTAMPTZ,
    superseded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > created_at)
);
CREATE INDEX ix_dependent_add_requests_requester_created
    ON dependent_add_requests(requester_account_id, created_at DESC);
