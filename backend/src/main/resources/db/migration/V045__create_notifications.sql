CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    type VARCHAR(48) NOT NULL,
    title VARCHAR(240) NOT NULL,
    content TEXT NOT NULL,
    related_kind VARCHAR(24),
    related_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ,
    CONSTRAINT ck_notifications_related_kind CHECK (related_kind IN ('APPOINTMENT', 'VISIT', 'VISIT_RESULT', 'PAYMENT', 'REVIEW')),
    CONSTRAINT ck_notifications_related_pair CHECK ((related_kind IS NULL) = (related_id IS NULL))
);
CREATE INDEX ix_notifications_recipient_created ON notifications(recipient_account_id, created_at DESC, id DESC);
CREATE INDEX ix_notifications_recipient_unread ON notifications(recipient_account_id, created_at DESC, id DESC) WHERE read_at IS NULL;
