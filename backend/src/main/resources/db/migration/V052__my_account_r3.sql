CREATE TABLE account_avatars (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    photo_content BYTEA NOT NULL,
    photo_content_type VARCHAR(32) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_account_avatars_mime CHECK (photo_content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_account_avatars_size CHECK (octet_length(photo_content) BETWEEN 1 AND 2097152)
);

CREATE TABLE email_change_challenges (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    normalized_target_email VARCHAR(254) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts BETWEEN 0 AND 5),
    consumed_at TIMESTAMPTZ,
    superseded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_email_change_target CHECK (normalized_target_email = lower(btrim(normalized_target_email))
        AND length(normalized_target_email) > 0),
    CONSTRAINT ck_email_change_expiry CHECK (expires_at > created_at)
);
CREATE UNIQUE INDEX uk_email_change_current ON email_change_challenges(account_id)
    WHERE consumed_at IS NULL AND superseded_at IS NULL;
CREATE INDEX ix_email_change_account_created ON email_change_challenges(account_id, created_at DESC);
