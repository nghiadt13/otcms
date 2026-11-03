ALTER TABLE accounts DROP CONSTRAINT ck_accounts_email_required;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_email_required CHECK (
    normalized_email IS NOT NULL
    OR (account_type <> 'PATIENT' AND username IS NOT NULL)
);

CREATE TABLE account_management_audits (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    actor_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    action VARCHAR(24) NOT NULL CHECK (action IN ('CREATE', 'ACTIVATE', 'DEACTIVATE', 'REISSUE_PASSWORD')),
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(64) NOT NULL
);
CREATE INDEX ix_account_management_audits_account
    ON account_management_audits (account_id, occurred_at DESC, id DESC);
CREATE INDEX ix_account_management_audits_actor
    ON account_management_audits (actor_account_id, occurred_at DESC, id DESC);
