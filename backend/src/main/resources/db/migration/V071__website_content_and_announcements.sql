CREATE TABLE website_images (
    id UUID PRIMARY KEY,
    content BYTEA NOT NULL,
    content_type VARCHAR(32) NOT NULL CHECK (content_type IN ('image/jpeg','image/png','image/webp')),
    CHECK (octet_length(content) BETWEEN 1 AND 2097152)
);
CREATE TABLE website_contents (
    id UUID PRIMARY KEY,
    type VARCHAR(32) NOT NULL CHECK (type IN ('ABOUT_PAGE','HEALTH_ARTICLE','CLINIC_INFO')),
    title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    summary TEXT NOT NULL DEFAULT '',
    body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED')),
    cover_image_id UUID REFERENCES website_images(id) ON DELETE RESTRICT,
    published_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    updated_by UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_website_contents_public_type ON website_contents(type, updated_at DESC, id) WHERE status='PUBLISHED';
CREATE TABLE announcements (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    body TEXT NOT NULL CHECK (length(trim(body)) > 0),
    audience VARCHAR(16) NOT NULL CHECK (audience IN ('PUBLIC','PATIENT','STAFF')),
    recipient_role VARCHAR(32),
    published_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK ((audience='STAFF' AND recipient_role IS NOT NULL AND recipient_role IN ('ORTHOPEDIC_DOCTOR','RECEPTIONIST','IMAGING_TECHNICIAN','LAB_TECHNICIAN'))
        OR (audience <> 'STAFF' AND recipient_role IS NULL))
);
CREATE INDEX ix_announcements_public ON announcements(published_at DESC,id) WHERE audience='PUBLIC';
CREATE TABLE content_audits (
    id UUID PRIMARY KEY,
    actor_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    category VARCHAR(32) NOT NULL CHECK (category IN ('CONTENT','ANNOUNCEMENT')),
    action VARCHAR(32) NOT NULL CHECK (action IN ('UPDATE_CONTENT','CREATE_ANNOUNCEMENT')),
    record_id UUID NOT NULL,
    record_label VARCHAR(200) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_content_audits_time ON content_audits(occurred_at DESC,id);
CREATE FUNCTION reject_content_audit_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'content audit rows are immutable' USING ERRCODE='55000'; END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER tr_content_audits_immutable BEFORE UPDATE OR DELETE ON content_audits
FOR EACH ROW EXECUTE FUNCTION reject_content_audit_mutation();
CREATE TRIGGER tr_content_audits_no_truncate BEFORE TRUNCATE ON content_audits
FOR EACH STATEMENT EXECUTE FUNCTION reject_content_audit_mutation();
