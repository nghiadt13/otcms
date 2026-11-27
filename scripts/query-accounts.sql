-- Read-only account inspection for the local OTCMS PostgreSQL container.
-- Intentionally excludes password_hash and avatar_content.
BEGIN TRANSACTION READ ONLY;

SELECT account_type, status, COUNT(*) AS account_count
FROM accounts
GROUP BY account_type, status
ORDER BY account_type, status;

SELECT id,
       username,
       normalized_email,
       account_type,
       status,
       display_name,
       full_name,
       must_change_password,
       email_verified_at,
       failed_credential_count,
       locked_until,
       created_at,
       updated_at
FROM accounts
ORDER BY created_at DESC, id
LIMIT 200;

COMMIT;
