-- final-db phase 09: announcements move into website_contents (type ANNOUNCEMENT); content_audits and
-- patient_review_moderation_history move into audit_logs. website_images stays (phase 09 decision A).
-- Runs after V109: the rewritten activity_log_entries view no longer reads content_audits, so DROP TABLE can succeed.

-- 1. website_contents takes general announcements (audience, recipient_role).
ALTER TABLE website_contents
    ADD COLUMN audience varchar(16),
    ADD COLUMN recipient_role varchar(32),
    DROP CONSTRAINT website_contents_type_check,
    ADD CONSTRAINT website_contents_type_check
        CHECK (type IN ('ABOUT_PAGE', 'HEALTH_ARTICLE', 'CLINIC_INFO', 'ANNOUNCEMENT')),
    -- Same rules as the old announcements checks: audience only on announcements, recipient_role only for STAFF.
    ADD CONSTRAINT ck_website_contents_announcement CHECK (
        (type = 'ANNOUNCEMENT') = (audience IS NOT NULL)
        AND (audience IS NULL OR audience IN ('PUBLIC', 'PATIENT', 'STAFF'))
        AND (COALESCE(audience, '') = 'STAFF') = (recipient_role IS NOT NULL)
        AND (recipient_role IS NULL OR recipient_role IN
             ('ORTHOPEDIC_DOCTOR', 'RECEPTIONIST', 'IMAGING_TECHNICIAN', 'LAB_TECHNICIAN'))
        AND (type <> 'ANNOUNCEMENT' OR (status = 'PUBLISHED'
             AND length(btrim(title)) > 0 AND length(btrim(body)) > 0)));
CREATE INDEX ix_website_contents_announcement_public
    ON website_contents (published_at DESC, id) WHERE type = 'ANNOUNCEMENT' AND audience = 'PUBLIC';

-- 2. Copy announcements (demo data). Summary is empty and there is no cover image.
INSERT INTO website_contents (id, type, title, summary, body, status, cover_image_id, published_at, updated_at,
                              created_by, updated_by, version, audience, recipient_role)
SELECT id, 'ANNOUNCEMENT', title, '', body, 'PUBLISHED', NULL, published_at, published_at,
       created_by, created_by, version, audience, recipient_role
FROM announcements;

-- 3. Audit rows -> audit_logs. source = legacy table name. Legacy rows always have an actor, so actor_type = 'ACCOUNT'.
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, actor_type)
SELECT id, occurred_at, 'content_audits', category, action, category, record_id, record_label,
       true, actor_account_id, 'ACCOUNT'
FROM content_audits;

-- Moderation history stays out of the activity log (visible = false). before/after keep only the legacy fields.
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id,
                        visible_in_activity_log, actor_account_id, actor_type, before_state, after_state)
SELECT id, occurred_at, 'patient_review_moderation_history', 'REVIEW', action, 'PATIENT_REVIEW', review_id,
       false, actor_account_id, 'ACCOUNT',
       jsonb_build_object('status', from_status, 'replyText', NULL::text),
       jsonb_build_object('status', to_status, 'replyText', reply_text)
FROM patient_review_moderation_history;

-- 4. Triggers first, then their functions, then the tables. Immutability now comes from the audit_logs triggers (V109).
DROP TRIGGER tr_content_audits_immutable ON content_audits;
DROP TRIGGER tr_content_audits_no_truncate ON content_audits;
DROP TRIGGER patient_review_history_append_only ON patient_review_moderation_history;
DROP FUNCTION reject_content_audit_mutation();
DROP FUNCTION reject_patient_review_history_mutation();
DROP TABLE content_audits;
DROP TABLE patient_review_moderation_history;
DROP TABLE announcements;
