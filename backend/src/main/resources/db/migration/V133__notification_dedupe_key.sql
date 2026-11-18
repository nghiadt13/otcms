-- NF-09: appointment / follow-up reminders must never be sent twice. The reminder job writes
-- dedupe_key = '<type>:<appointmentId>[:<startEpochSecond>]' and inserts with ON CONFLICT DO NOTHING.
-- Existing (non-reminder) notifications keep dedupe_key NULL and are never constrained.
ALTER TABLE notifications ADD COLUMN dedupe_key varchar(120);

CREATE UNIQUE INDEX uq_notifications_dedupe_key ON notifications (dedupe_key) WHERE dedupe_key IS NOT NULL;
