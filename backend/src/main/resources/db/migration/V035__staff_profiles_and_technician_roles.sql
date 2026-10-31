ALTER TABLE accounts DROP CONSTRAINT ck_accounts_type;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_type CHECK (account_type IN
    ('PATIENT', 'RECEPTIONIST', 'DOCTOR', 'LAB_TECHNICIAN', 'IMAGING_TECHNICIAN', 'OWNER', 'ADMIN'));

CREATE TABLE staff_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL UNIQUE REFERENCES accounts(id) ON DELETE RESTRICT,
    display_name VARCHAR(100) NOT NULL,
    work_contact VARCHAR(100) NOT NULL,
    photo_content BYTEA,
    photo_content_type VARCHAR(32),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_staff_profile_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT ck_staff_profile_name CHECK (length(trim(display_name)) > 0),
    CONSTRAINT ck_staff_profile_photo CHECK
        ((photo_content IS NULL AND photo_content_type IS NULL) OR
         (photo_content IS NOT NULL AND photo_content_type IN ('image/jpeg', 'image/png', 'image/webp')))
);

INSERT INTO staff_profiles (id, account_id, display_name, work_contact, status)
SELECT d.id, d.account_id, left(d.full_name, 100), coalesce(d.work_contact, ''),
       CASE WHEN d.in_active_directory THEN 'ACTIVE' ELSE 'REMOVED' END
FROM doctor_profiles d
ON CONFLICT (account_id) DO NOTHING;

CREATE INDEX ix_staff_profiles_status_name ON staff_profiles (status, display_name, id);
