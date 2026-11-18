-- P05 (BR-65, NF-05b): a visit Awaiting Conclusion that is not concluded within 7 days of its first check-in
-- becomes INCOMPLETE. The exam queue entry keeps AWAITING_CONCLUSION; the visit row carries the new status.
-- V115 put no status guard on visits (only the source/arrival protection trigger), so only the CHECK changes.
ALTER TABLE visits DROP CONSTRAINT ck_clinic_visits_status;
ALTER TABLE visits ADD CONSTRAINT ck_clinic_visits_status CHECK (status IN ('OPEN', 'CLOSED', 'INCOMPLETE'));

ALTER TABLE visits ADD COLUMN incomplete_at timestamptz NULL;
ALTER TABLE visits ADD CONSTRAINT ck_clinic_visits_incomplete_at
    CHECK ((status = 'INCOMPLETE') = (incomplete_at IS NOT NULL));

-- Daily sweeps only look at open visits by check-in time.
CREATE INDEX ix_visits_open_checked_in ON visits (checked_in_at) WHERE status = 'OPEN';
