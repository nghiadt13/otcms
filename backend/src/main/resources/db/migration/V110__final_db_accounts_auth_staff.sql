-- final-db phase 02: accounts, verification_codes and staff_profiles absorb the auth, consent, avatar
-- and doctor tables. Runs after V109 (audit_logs exists).
-- Order: guard -> add columns and copy -> repoint FKs -> copy audit rows -> drop old tables.
-- staff_resource_audits is not dropped here (phase 10 drops it).

-- Guard: one account must not have a doctor profile and a staff profile with different ids.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM doctor_profiles d JOIN staff_profiles s ON s.account_id = d.account_id AND s.id <> d.id) THEN
        RAISE EXCEPTION 'V110: doctor_profiles and staff_profiles disagree on the staff id of one account';
    END IF;
END
$$;

-- 0. audit_logs widths must hold every auth_audits value copied in step 5 and written by the auth services:
--    V001 had outcome varchar(64) and identifier_hmac varchar(255); V109 created them narrower.
ALTER TABLE audit_logs
    ALTER COLUMN outcome TYPE varchar(64),
    ALTER COLUMN identifier_hmac TYPE varchar(255);

-- 1. accounts: avatar (1-1 from account_avatars) and the newest consent acceptance.
ALTER TABLE accounts
    ADD COLUMN avatar_content bytea,
    ADD COLUMN avatar_content_type varchar(32),
    ADD COLUMN avatar_updated_at timestamptz,
    ADD COLUMN terms_version varchar(64),
    ADD COLUMN privacy_version varchar(64),
    ADD COLUMN consent_actor_type varchar(16),
    ADD COLUMN consent_accepted_at timestamptz,
    ADD CONSTRAINT ck_accounts_avatar_complete CHECK (((avatar_content IS NULL) = (avatar_content_type IS NULL))
        AND ((avatar_content IS NULL) = (avatar_updated_at IS NULL))),
    ADD CONSTRAINT ck_accounts_avatar_type CHECK (avatar_content_type IS NULL
        OR avatar_content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    ADD CONSTRAINT ck_accounts_avatar_size CHECK (avatar_content IS NULL
        OR octet_length(avatar_content) BETWEEN 1 AND 2097152),
    ADD CONSTRAINT ck_accounts_consent_actor CHECK (consent_actor_type IS NULL
        OR consent_actor_type IN ('PATIENT', 'GUARDIAN')),
    ADD CONSTRAINT ck_accounts_consent_complete CHECK (((terms_version IS NULL) = (privacy_version IS NULL))
        AND ((terms_version IS NULL) = (consent_actor_type IS NULL))
        AND ((terms_version IS NULL) = (consent_accepted_at IS NULL)));

UPDATE accounts a
SET avatar_content = p.photo_content,
    avatar_content_type = p.photo_content_type,
    avatar_updated_at = p.updated_at
FROM account_avatars p
WHERE p.account_id = a.id;

-- Only the newest acceptance stays on the account row; the full history goes to audit_logs (step 5).
UPDATE accounts a
SET terms_version = c.terms_version,
    privacy_version = c.privacy_version,
    consent_actor_type = c.actor_type,
    consent_accepted_at = c.accepted_at
FROM (SELECT DISTINCT ON (account_id) account_id, terms_version, privacy_version, actor_type, accepted_at
      FROM account_consents
      ORDER BY account_id, accepted_at DESC, id) c
WHERE c.account_id = a.id;

-- 2. verification_codes: replaces email_verifications, email_change_challenges and password_resets.
--    Ids and hashes are kept. Phase 06 adds CARE_LINK and DEPENDENT_ADD rows in V111.
CREATE TABLE verification_codes (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    account_id uuid NOT NULL,
    purpose varchar(24) NOT NULL,
    code_hash varchar(255) NOT NULL,
    target_email varchar(254),
    link_id uuid,
    payload jsonb,
    failed_attempts integer NOT NULL DEFAULT 0,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    superseded_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT verification_codes_pkey PRIMARY KEY (id),
    CONSTRAINT fk_verification_codes_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    CONSTRAINT fk_verification_codes_link FOREIGN KEY (link_id) REFERENCES patient_caregivers(id),
    CONSTRAINT ck_verification_codes_purpose CHECK (purpose IN ('EMAIL_VERIFY', 'EMAIL_CHANGE', 'PASSWORD_RESET', 'CARE_LINK', 'DEPENDENT_ADD')),
    CONSTRAINT ck_verification_codes_failed_attempts CHECK (failed_attempts BETWEEN 0 AND 5),
    CONSTRAINT ck_verification_codes_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_verification_codes_consumed CHECK (consumed_at IS NULL OR consumed_at >= created_at),
    CONSTRAINT ck_verification_codes_superseded CHECK (superseded_at IS NULL OR superseded_at >= created_at),
    CONSTRAINT ck_verification_codes_target_email CHECK (target_email IS NULL
        OR (target_email = lower(btrim(target_email)) AND length(target_email) > 0)),
    CONSTRAINT ck_verification_codes_email_change_target CHECK (purpose <> 'EMAIL_CHANGE' OR target_email IS NOT NULL),
    CONSTRAINT ck_verification_codes_care_link_link CHECK (purpose <> 'CARE_LINK' OR link_id IS NOT NULL)
);

-- One current code per account and purpose. CARE_LINK is unique per link instead.
-- DEPENDENT_ADD is included: the app supersedes open requests before it issues a new one.
CREATE UNIQUE INDEX uk_verification_codes_account_current ON verification_codes (account_id, purpose)
    WHERE consumed_at IS NULL AND superseded_at IS NULL AND purpose <> 'CARE_LINK';
CREATE UNIQUE INDEX uk_verification_codes_link_current ON verification_codes (link_id, purpose)
    WHERE purpose = 'CARE_LINK' AND consumed_at IS NULL AND superseded_at IS NULL;
CREATE UNIQUE INDEX uk_verification_codes_password_reset_hash ON verification_codes (code_hash)
    WHERE purpose = 'PASSWORD_RESET';
-- FK indexes: account_id leads the first index, link_id has its own partial index.
-- The purpose-first indexes serve the resend-limit counts (phase 06).
CREATE INDEX ix_verification_codes_account_purpose_created ON verification_codes (account_id, purpose, created_at DESC);
CREATE INDEX ix_verification_codes_purpose_account_created ON verification_codes (purpose, account_id, created_at);
CREATE INDEX ix_verification_codes_link ON verification_codes (link_id) WHERE link_id IS NOT NULL;
CREATE INDEX ix_verification_codes_purpose_link_created ON verification_codes (purpose, link_id, created_at);

INSERT INTO verification_codes (id, account_id, purpose, code_hash, target_email, failed_attempts,
                                expires_at, consumed_at, superseded_at, created_at)
SELECT id, account_id, 'EMAIL_VERIFY', otp_hash, NULL, failed_attempts,
       expires_at, consumed_at, superseded_at, created_at
FROM email_verifications;

INSERT INTO verification_codes (id, account_id, purpose, code_hash, target_email, failed_attempts,
                                expires_at, consumed_at, superseded_at, created_at)
SELECT id, account_id, 'EMAIL_CHANGE', otp_hash, normalized_target_email, failed_attempts,
       expires_at, consumed_at, superseded_at, created_at
FROM email_change_challenges;

INSERT INTO verification_codes (id, account_id, purpose, code_hash, target_email, failed_attempts,
                                expires_at, consumed_at, superseded_at, created_at)
SELECT id, account_id, 'PASSWORD_RESET', token_hash, NULL, failed_attempts,
       expires_at, consumed_at, superseded_at, created_at
FROM password_resets;

-- 3. staff_profiles absorbs doctor_profiles. A doctor keeps its doctor id as its staff id.
--    Decisions D-a..D-e (phase 02): the listed columns stay separate, nothing is merged.
ALTER TABLE staff_profiles
    ALTER COLUMN display_name TYPE varchar(160),
    ALTER COLUMN work_contact TYPE varchar(254),
    ALTER COLUMN work_contact DROP NOT NULL,
    ADD COLUMN is_doctor boolean NOT NULL DEFAULT false,
    ADD COLUMN in_active_directory boolean NOT NULL DEFAULT true,
    ADD COLUMN publicly_visible boolean NOT NULL DEFAULT false,
    ADD COLUMN accepting_new_bookings boolean NOT NULL DEFAULT false,
    ADD COLUMN specialties text NOT NULL DEFAULT '',
    ADD COLUMN qualifications text NOT NULL DEFAULT '',
    ADD COLUMN biography text NOT NULL DEFAULT '',
    ADD COLUMN consultation_information text NOT NULL DEFAULT '',
    ADD COLUMN photo_url varchar(2048),
    ADD CONSTRAINT ck_staff_profiles_doctor_flags CHECK (is_doctor OR (NOT publicly_visible AND NOT accepting_new_bookings));

-- Existing staff rows that are also doctors: doctor values win for doctor fields.
UPDATE staff_profiles s
SET is_doctor = true,
    display_name = d.full_name,
    work_contact = d.work_contact,
    in_active_directory = d.in_active_directory,
    publicly_visible = d.publicly_visible,
    accepting_new_bookings = d.accepting_new_bookings,
    specialties = d.specialties,
    qualifications = d.qualifications,
    biography = d.biography,
    consultation_information = d.consultation_information,
    photo_url = d.photo_url
FROM doctor_profiles d
WHERE d.id = s.id;

-- Doctors without a staff row: insert them with the same id.
INSERT INTO staff_profiles (id, account_id, display_name, work_contact, status, created_at, updated_at,
                            is_doctor, in_active_directory, publicly_visible, accepting_new_bookings,
                            specialties, qualifications, biography, consultation_information, photo_url)
SELECT d.id, d.account_id, d.full_name, d.work_contact, 'ACTIVE', d.created_at, d.updated_at,
       true, d.in_active_directory, d.publicly_visible, d.accepting_new_bookings,
       d.specialties, d.qualifications, d.biography, d.consultation_information, d.photo_url
FROM doctor_profiles d
WHERE NOT EXISTS (SELECT 1 FROM staff_profiles s WHERE s.id = d.id);

CREATE INDEX ix_staff_profiles_public_directory ON staff_profiles (display_name, id)
    WHERE is_doctor AND publicly_visible AND in_active_directory;

-- 4. Repoint the 10 FKs that reference doctor_profiles(id) to staff_profiles(id).
--    Same column and constraint name, ON DELETE RESTRICT. The time-off FK goes away with doctor_time_off (step 7).
ALTER TABLE appointment_audits
    DROP CONSTRAINT appointment_audits_new_doctor_profile_id_fkey,
    ADD CONSTRAINT appointment_audits_new_doctor_profile_id_fkey
        FOREIGN KEY (new_doctor_profile_id) REFERENCES staff_profiles(id) ON DELETE RESTRICT;
ALTER TABLE appointment_audits
    DROP CONSTRAINT appointment_audits_old_doctor_profile_id_fkey,
    ADD CONSTRAINT appointment_audits_old_doctor_profile_id_fkey
        FOREIGN KEY (old_doctor_profile_id) REFERENCES staff_profiles(id) ON DELETE RESTRICT;
ALTER TABLE appointments
    DROP CONSTRAINT appointments_doctor_profile_id_fkey,
    ADD CONSTRAINT appointments_doctor_profile_id_fkey
        FOREIGN KEY (doctor_profile_id) REFERENCES staff_profiles(id) ON DELETE RESTRICT;
ALTER TABLE appointments
    DROP CONSTRAINT appointments_ordering_doctor_profile_id_fkey,
    ADD CONSTRAINT appointments_ordering_doctor_profile_id_fkey
        FOREIGN KEY (ordering_doctor_profile_id) REFERENCES staff_profiles(id) ON DELETE RESTRICT;
ALTER TABLE doctor_shifts
    DROP CONSTRAINT doctor_work_shifts_doctor_profile_id_fkey,
    ADD CONSTRAINT doctor_work_shifts_doctor_profile_id_fkey
        FOREIGN KEY (doctor_profile_id) REFERENCES staff_profiles(id) ON DELETE RESTRICT;
ALTER TABLE doctor_services
    DROP CONSTRAINT fk_doctor_service_assignments_doctor_profile,
    ADD CONSTRAINT fk_doctor_service_assignments_doctor_profile
        FOREIGN KEY (doctor_profile_id) REFERENCES staff_profiles(id) ON DELETE RESTRICT;
ALTER TABLE staff_resource_audits
    DROP CONSTRAINT staff_resource_management_audits_doctor_profile_id_fkey,
    ADD CONSTRAINT staff_resource_management_audits_doctor_profile_id_fkey
        FOREIGN KEY (doctor_profile_id) REFERENCES staff_profiles(id) ON DELETE RESTRICT;
ALTER TABLE visit_assignments
    DROP CONSTRAINT visit_care_assignments_doctor_profile_id_fkey,
    ADD CONSTRAINT visit_care_assignments_doctor_profile_id_fkey
        FOREIGN KEY (doctor_profile_id) REFERENCES staff_profiles(id) ON DELETE RESTRICT;
ALTER TABLE visit_examinations
    DROP CONSTRAINT visit_clinical_examinations_doctor_profile_id_fkey,
    ADD CONSTRAINT visit_clinical_examinations_doctor_profile_id_fkey
        FOREIGN KEY (doctor_profile_id) REFERENCES staff_profiles(id) ON DELETE RESTRICT;
ALTER TABLE visit_exam_queue
    DROP CONSTRAINT visit_exam_queue_doctor_profile_id_fkey,
    ADD CONSTRAINT visit_exam_queue_doctor_profile_id_fkey
        FOREIGN KEY (doctor_profile_id) REFERENCES staff_profiles(id) ON DELETE RESTRICT;

-- 5. Copy audit history to audit_logs. Each row keeps its id, so activity log ids (prefix:id) do not change.
--    Must run before doctor_profiles is dropped (record_label reads doctor_profiles.full_name).
-- 5a. Staff profile audit. DOCTOR_PROFILE rows are visible; TIME_OFF rows are kept for lookup only.
--     Label = doctor name, falling back to the resource id, as the V108 activity view did.
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, before_state, after_state, correlation_id)
SELECT s.id, s.occurred_at, 'staff_resource_audits', 'STAFF_PROFILE',
       CASE WHEN s.action = 'CREATED' THEN 'CREATE_STAFF_PROFILE' ELSE 'UPDATE_STAFF_PROFILE' END,
       s.resource_type, s.resource_id,
       CASE WHEN s.resource_type = 'DOCTOR_PROFILE' THEN COALESCE(d.full_name, s.resource_id::text) END,
       s.resource_type = 'DOCTOR_PROFILE',
       s.actor_account_id, s.before_state, s.after_state, s.correlation_id
FROM staff_resource_audits s
LEFT JOIN doctor_profiles d ON d.id = s.doctor_profile_id
WHERE s.resource_type IN ('DOCTOR_PROFILE', 'TIME_OFF');

-- 5b. Account management audit: visible, label = account id as text (as the V108 activity view did).
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, correlation_id)
SELECT a.id, a.occurred_at, 'account_management_audits', 'ACCOUNT', a.action, 'ACCOUNT',
       a.account_id, a.account_id::text, true, a.actor_account_id, a.correlation_id
FROM account_management_audits a;

-- 5c. Auth audit: never shown in the activity log. The method is the action, because the login
--     rate limit counts rows by source, action, outcome and identifier_hmac.
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id,
                        visible_in_activity_log, correlation_id, client_ip, user_agent, identifier_hmac,
                        outcome, metadata)
SELECT a.id, a.occurred_at, 'auth_audits', 'AUTH', a.method, 'AUTH', a.account_id,
       false, a.correlation_id, a.client_ip, a.user_agent, a.identifier_hmac,
       a.outcome, jsonb_build_object('durationMs', a.duration_ms)
FROM auth_audits a;

-- 5d. Consent history: one row per acceptance, never shown in the activity log.
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id,
                        visible_in_activity_log, metadata)
SELECT c.id, c.accepted_at, 'account_consents', 'CONSENT', 'ACCEPT_CONSENT', 'ACCOUNT', c.account_id,
       false, jsonb_build_object('termsVersion', c.terms_version,
                                 'privacyVersion', c.privacy_version,
                                 'actorType', c.actor_type)
FROM account_consents c;

-- 6. Triggers and functions: none is attached to the tables dropped below (checked on V108).
--    The activity view was rewritten in V109 and no longer reads them.

-- 7. Drop the old tables. doctor_time_off goes before doctor_profiles: it holds the last FK into doctor_profiles.
DROP TABLE account_avatars;
DROP TABLE account_consents;
DROP TABLE account_identities;
DROP TABLE doctor_time_off;
DROP TABLE email_verifications;
DROP TABLE email_change_challenges;
DROP TABLE password_resets;
DROP TABLE auth_audits;
DROP TABLE account_management_audits;
DROP TABLE doctor_profiles;
