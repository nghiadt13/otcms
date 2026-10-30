-- Local/demo seed only. Do not run against production.
-- Both accounts use the BCrypt hash for the demo password: password
-- Change the password immediately if this database is shared.

INSERT INTO accounts (
    id, normalized_email, account_type, status, password_hash,
    email_verified_at, failed_credential_count, created_at, updated_at
)
VALUES
    ('00000000-0000-0000-0000-000000000101', 'demo.patient@localhost', 'PATIENT', 'ACTIVE',
     '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     now(), 0, now(), now()),
    ('00000000-0000-0000-0000-000000000102', 'demo.staff@localhost', 'STAFF', 'ACTIVE',
     '{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
     now(), 0, now(), now())
ON CONFLICT (normalized_email) DO NOTHING;
