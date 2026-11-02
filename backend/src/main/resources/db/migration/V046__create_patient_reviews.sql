CREATE TABLE patient_reviews (
    id UUID PRIMARY KEY,
    visit_id UUID NOT NULL REFERENCES visits(id) ON DELETE RESTRICT,
    patient_profile_id UUID NOT NULL REFERENCES patient_profiles(id) ON DELETE RESTRICT,
    author_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content TEXT NOT NULL CHECK (length(btrim(content)) BETWEEN 10 AND 1000),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'HIDDEN')),
    reply_text TEXT CHECK (reply_text IS NULL OR length(btrim(reply_text)) BETWEEN 1 AND 1000),
    replied_at TIMESTAMPTZ,
    replied_by_account_id UUID REFERENCES accounts(id) ON DELETE RESTRICT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uk_patient_reviews_visit UNIQUE (visit_id),
    CONSTRAINT ck_patient_reviews_reply CHECK (
        (reply_text IS NULL AND replied_at IS NULL AND replied_by_account_id IS NULL)
        OR (reply_text IS NOT NULL AND replied_at IS NOT NULL AND replied_by_account_id IS NOT NULL)
    )
);
CREATE INDEX ix_patient_reviews_patient_submitted ON patient_reviews(patient_profile_id, submitted_at DESC, id);
CREATE INDEX ix_patient_reviews_status_submitted ON patient_reviews(status, submitted_at DESC, id);
CREATE INDEX ix_patient_reviews_author ON patient_reviews(author_account_id);
CREATE INDEX ix_patient_reviews_replier ON patient_reviews(replied_by_account_id) WHERE replied_by_account_id IS NOT NULL;

CREATE TABLE patient_review_moderation_history (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES patient_reviews(id) ON DELETE RESTRICT,
    action VARCHAR(16) NOT NULL CHECK (action IN ('SUBMIT', 'APPROVE', 'HIDE', 'UNHIDE', 'REPLY', 'EDIT_REPLY')),
    from_status VARCHAR(16) CHECK (from_status IN ('PENDING', 'PUBLISHED', 'HIDDEN')),
    to_status VARCHAR(16) NOT NULL CHECK (to_status IN ('PENDING', 'PUBLISHED', 'HIDDEN')),
    reply_text TEXT,
    actor_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_patient_review_history_review_time ON patient_review_moderation_history(review_id, occurred_at, id);
CREATE INDEX ix_patient_review_history_actor ON patient_review_moderation_history(actor_account_id);

CREATE FUNCTION reject_patient_review_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'patient review moderation history is append-only' USING ERRCODE = '55000';
END;
$$;
CREATE TRIGGER patient_review_history_append_only
    BEFORE UPDATE OR DELETE ON patient_review_moderation_history
    FOR EACH ROW EXECUTE FUNCTION reject_patient_review_history_mutation();
