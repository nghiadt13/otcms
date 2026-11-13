-- final-db phase 05: visit_check_ins folds into visits (no reception_type column: it follows visits.source);
-- walk_in_requests becomes two visits columns; walk_in_reception_queue_entries and visit_exam_queue become
-- queue_entries (stage RECEPTION | EXAM); visit_audits, visit_assignment_audits and the walk-in queue history
-- move to audit_logs; visit_examinations is dropped (phase 05 found no production code that reads or writes it).
-- Order: create and alter, guards and copy, drop triggers and functions, rewrite four functions, drop tables.

-- One table for both queues. Stage decides which columns and statuses apply (CHECKs below).
CREATE TABLE queue_entries (
    id                  uuid         NOT NULL DEFAULT gen_random_uuid(),
    visit_id            uuid         NOT NULL,
    stage               varchar(16)  NOT NULL,
    status              varchar(32)  NOT NULL,
    arrived_at          timestamptz  NULL,
    entered_at          timestamptz  NOT NULL,
    left_at             timestamptz  NULL,
    exit_reason         varchar(24)  NULL,
    doctor_profile_id   uuid         NULL,
    room_id             uuid         NULL,
    service_id          uuid         NULL,
    clinic_date         date         NULL,
    queue_number        integer      NULL,
    queued_at           timestamptz  NULL,
    started_at          timestamptz  NULL,
    awaiting_at         timestamptz  NULL,
    finished_at         timestamptz  NULL,
    CONSTRAINT queue_entries_pkey PRIMARY KEY (id),
    CONSTRAINT fk_queue_entries_visit FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE RESTRICT,
    -- staff_profiles, not doctor_profiles: per phase 02, V110 drops doctor_profiles and runs before this file.
    CONSTRAINT fk_queue_entries_doctor_profile FOREIGN KEY (doctor_profile_id) REFERENCES staff_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_queue_entries_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT,
    CONSTRAINT fk_queue_entries_service FOREIGN KEY (service_id) REFERENCES clinic_services(id) ON DELETE RESTRICT,
    CONSTRAINT ck_queue_entries_stage CHECK (stage IN ('RECEPTION', 'EXAM')),
    -- RECEPTION rows: same rules as V108 ck_walk_in_reception_queue_exit; EXAM-only columns stay NULL.
    CONSTRAINT ck_queue_entries_reception CHECK (
        stage <> 'RECEPTION' OR (
            arrived_at IS NOT NULL
            AND doctor_profile_id IS NULL AND room_id IS NULL AND service_id IS NULL
            AND clinic_date IS NULL AND queue_number IS NULL
            AND queued_at IS NULL AND started_at IS NULL AND awaiting_at IS NULL AND finished_at IS NULL
            AND (
                (status = 'WAITING' AND left_at IS NULL AND exit_reason IS NULL)
                OR (status = 'LEFT_RECEPTION' AND left_at IS NOT NULL
                    AND exit_reason IS NOT NULL AND exit_reason IN ('CARE_STARTED', 'CANCELLED', 'VISIT_CLOSED'))
            )
        )
    ),
    -- EXAM rows: same rules as V108 ck_visit_exam_queue_number and ck_visit_exam_queue_status.
    CONSTRAINT ck_queue_entries_exam CHECK (
        stage <> 'EXAM' OR (
            doctor_profile_id IS NOT NULL AND clinic_date IS NOT NULL
            AND arrived_at IS NULL AND left_at IS NULL AND exit_reason IS NULL
            AND (
                (status = 'PENDING_PAYMENT' AND queue_number IS NULL)
                OR status = 'CANCELLED'
                OR (status IN ('WAITING', 'IN_EXAMINATION', 'AWAITING_CONCLUSION', 'COMPLETED')
                    AND queue_number IS NOT NULL AND queue_number > 0)
            )
        )
    )
);

-- Partial unique indexes per stage (they replace the old PKs and unique indexes of the two queue tables).
CREATE UNIQUE INDEX uq_queue_entries_reception_active_visit ON queue_entries (visit_id)
    WHERE stage = 'RECEPTION' AND status = 'WAITING';
CREATE UNIQUE INDEX uq_queue_entries_exam_visit ON queue_entries (visit_id) WHERE stage = 'EXAM';
CREATE UNIQUE INDEX uq_queue_entries_exam_number ON queue_entries (doctor_profile_id, clinic_date, queue_number)
    WHERE stage = 'EXAM' AND queue_number IS NOT NULL;
CREATE UNIQUE INDEX uq_queue_entries_exam_one_in_exam ON queue_entries (doctor_profile_id)
    WHERE stage = 'EXAM' AND status = 'IN_EXAMINATION';
CREATE INDEX ix_queue_entries_reception_fifo ON queue_entries (arrived_at, id)
    WHERE stage = 'RECEPTION' AND status = 'WAITING';
CREATE INDEX ix_queue_entries_exam_date ON queue_entries (clinic_date, doctor_profile_id) WHERE stage = 'EXAM';
-- FK indexes: visit_id leads ix_queue_entries_visit; the others are partial on non-null values.
CREATE INDEX ix_queue_entries_visit ON queue_entries (visit_id, stage);
CREATE INDEX ix_queue_entries_doctor ON queue_entries (doctor_profile_id) WHERE doctor_profile_id IS NOT NULL;
CREATE INDEX ix_queue_entries_room ON queue_entries (room_id) WHERE room_id IS NOT NULL;
CREATE INDEX ix_queue_entries_service ON queue_entries (service_id) WHERE service_id IS NOT NULL;

-- Walk-in idempotency keys (replace walk_in_requests). Only WALK_IN visits carry a key.
ALTER TABLE visits
    ADD COLUMN walk_in_idempotency_key varchar(128) NULL,
    ADD COLUMN walk_in_request_fingerprint varchar(64) NULL;

ALTER TABLE visits
    ADD CONSTRAINT ck_clinic_visits_walk_in_key_all_or_none CHECK (
        (walk_in_idempotency_key IS NULL AND walk_in_request_fingerprint IS NULL)
        OR (walk_in_idempotency_key IS NOT NULL AND walk_in_request_fingerprint IS NOT NULL)
    ),
    ADD CONSTRAINT ck_clinic_visits_walk_in_key_source CHECK (
        walk_in_idempotency_key IS NULL OR source = 'WALK_IN'
    ),
    ADD CONSTRAINT ck_clinic_visits_walk_in_key_length CHECK (
        walk_in_idempotency_key IS NULL OR length(trim(both from walk_in_idempotency_key)) BETWEEN 1 AND 128
    ),
    ADD CONSTRAINT ck_clinic_visits_walk_in_fingerprint CHECK (
        walk_in_request_fingerprint IS NULL OR walk_in_request_fingerprint ~ '^[0-9a-fA-F]{64}$'
    );

CREATE UNIQUE INDEX uk_clinic_visits_walk_in_idempotency ON visits (created_by_account_id, walk_in_idempotency_key)
    WHERE walk_in_idempotency_key IS NOT NULL;

-- Guards: stop before any row moves if the old tables disagree with visits.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM visit_check_ins r JOIN visits v ON v.id = r.visit_id
        WHERE r.arrived_at IS DISTINCT FROM v.arrived_at
           OR r.checked_in_at IS DISTINCT FROM v.checked_in_at
           OR r.receptionist_account_id IS DISTINCT FROM v.created_by_account_id
           OR r.prior_appointment_id IS DISTINCT FROM v.prior_appointment_id
           OR (r.reception_type = 'APPOINTMENT_CHECK_IN') IS DISTINCT FROM (v.source = 'SCHEDULED')
    ) THEN
        RAISE EXCEPTION 'visit_check_ins rows differ from visits' USING ERRCODE = '23514';
    END IF;
    IF EXISTS (
        SELECT 1 FROM walk_in_requests r JOIN visits v ON v.id = r.visit_id
        WHERE r.receptionist_account_id IS DISTINCT FROM v.created_by_account_id
    ) THEN
        RAISE EXCEPTION 'walk_in_requests rows differ from visits' USING ERRCODE = '23514';
    END IF;
END $$;

-- Copy demo rows. The guard above ensures the creator matches the request's receptionist.
UPDATE visits v
SET walk_in_idempotency_key = r.idempotency_key,
    walk_in_request_fingerprint = r.request_fingerprint
FROM walk_in_requests r
WHERE r.visit_id = v.id;

INSERT INTO queue_entries (id, visit_id, stage, status, arrived_at, entered_at, left_at, exit_reason)
SELECT w.id, w.visit_id, 'RECEPTION', w.status, w.arrived_at, w.entered_at, w.left_at, w.exit_reason
FROM walk_in_reception_queue_entries w;

INSERT INTO queue_entries (id, visit_id, stage, status, entered_at, doctor_profile_id, room_id, service_id,
                           clinic_date, queue_number, queued_at, started_at, awaiting_at, finished_at)
SELECT gen_random_uuid(), q.visit_id, 'EXAM', q.exam_status, q.created_at, q.doctor_profile_id, q.room_id,
       q.service_id, q.clinic_date, q.queue_number, q.queued_at, q.started_at, q.awaiting_at, q.finished_at
FROM visit_exam_queue q;

-- Audit copies keep their ids. Action and record mapping follow phase 05 section 4.8.
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, correlation_id)
SELECT a.id, a.occurred_at, 'visit_audits', 'VISIT', a.action, 'VISIT', a.visit_id, a.visit_id::text,
       true, a.actor_account_id, a.correlation_id
FROM visit_audits a;

-- CAPACITY_CONFLICT rows get the dedup_key the Java writer computes (VisitAuditEntries.careCapacityConflictKey),
-- so the shared uk_audit_logs_dedup_key replaces uq_visit_care_assignment_conflict_correlation (same pattern as V112).
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, before_state, after_state, metadata, correlation_id,
                        dedup_key)
SELECT a.id, a.occurred_at, 'visit_assignment_audits', 'VISIT', a.action, 'VISIT_ASSIGNMENT',
       COALESCE(a.assignment_id, a.visit_id), a.visit_id::text, true, a.actor_account_id,
       jsonb_build_object('expectedEnd', a.old_expected_end),
       jsonb_build_object('expectedEnd', a.new_expected_end),
       jsonb_build_object('stepCode', a.step_code),
       a.correlation_id,
       CASE WHEN a.action = 'CAPACITY_CONFLICT'
            THEN 'care-capacity:' || encode(sha256(convert_to(a.visit_id::text || ':' || a.actor_account_id::text || ':' || a.correlation_id, 'UTF8')), 'hex')
            ELSE NULL END
FROM visit_assignment_audits a;

-- Walk-in queue history: action is the exit reason, or the target status for the entry row.
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, metadata, correlation_id)
SELECT h.id, h.occurred_at, 'walk_in_reception_queue_history', 'VISIT', COALESCE(h.reason, h.to_status),
       'VISIT', e.visit_id, e.visit_id::text, true, h.actor_account_id,
       jsonb_build_object('queueEntryId', h.queue_entry_id, 'fromStatus', h.from_status, 'toStatus', h.to_status),
       h.correlation_id
FROM walk_in_reception_queue_history h
JOIN walk_in_reception_queue_entries e ON e.id = h.queue_entry_id;

-- Drop the triggers that use the functions being removed, plus the triggers on tables being dropped
-- (the two rewritten functions are re-attached to queue_entries below).
DROP TRIGGER ct_preserve_checked_in_appointment_reception ON visit_check_ins;
DROP TRIGGER ct_visit_initial_reception_type ON visit_check_ins;
DROP TRIGGER tr_walk_in_reception_queue_record_transition ON walk_in_reception_queue_entries;
DROP TRIGGER tr_walk_in_reception_queue_entry_lifecycle ON walk_in_reception_queue_entries;
DROP TRIGGER tr_walk_in_reception_queue_visit_guard ON walk_in_reception_queue_entries;
DROP TRIGGER tr_walk_in_reception_queue_history_generated_only ON walk_in_reception_queue_history;
DROP TRIGGER tr_walk_in_reception_queue_history_immutable ON walk_in_reception_queue_history;
DROP TRIGGER tr_walk_in_reception_queue_history_no_truncate ON walk_in_reception_queue_history;
DROP TRIGGER tr_visit_audits_immutable ON visit_audits;
DROP TRIGGER tr_visit_audits_no_truncate ON visit_audits;
DROP TRIGGER tr_visit_care_assignment_audits_immutable ON visit_assignment_audits;
DROP TRIGGER tr_visit_care_assignment_audits_no_truncate ON visit_assignment_audits;

DROP FUNCTION enforce_checked_in_appointment_reception_evidence();
DROP FUNCTION enforce_checked_in_appointment_reception_for_visit(uuid);
DROP FUNCTION enforce_initial_reception_type();
DROP FUNCTION record_walk_in_reception_queue_transition();
DROP FUNCTION reject_direct_walk_in_reception_queue_history_insert();
DROP FUNCTION reject_walk_in_reception_queue_history_mutation();
DROP FUNCTION reject_append_only_visit_audit_mutation();
DROP FUNCTION reject_visit_care_assignment_audit_mutation();

-- Rewritten functions (same signatures, so the triggers already attached to appointments and visits keep working).

-- Linked visit must be a SCHEDULED visit of the same appointment. SCHEDULED visits only come from check-in,
-- so this replaces the old visit_check_ins evidence lookup.
CREATE OR REPLACE FUNCTION enforce_appointment_visit_link() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    target_appointment_id UUID;
    target_visit_id UUID;
    appointment_status VARCHAR(16);
    appointment_visit_id UUID;
    visit_appointment_id UUID;
    visit_source VARCHAR(16);
BEGIN
    IF TG_TABLE_NAME = 'appointments' THEN
        target_appointment_id := NEW.id;
    ELSE
        target_visit_id := NEW.id;
        target_appointment_id := NEW.originating_appointment_id;
        IF target_appointment_id IS NULL THEN
            RETURN NULL;
        END IF;
        SELECT a.visit_id INTO appointment_visit_id
        FROM appointments a WHERE a.id = target_appointment_id;
        IF appointment_visit_id IS DISTINCT FROM target_visit_id THEN
            RAISE EXCEPTION 'visit must be the appointment linked visit' USING ERRCODE = '23514';
        END IF;
    END IF;

    SELECT a.status, a.visit_id INTO appointment_status, appointment_visit_id
    FROM appointments a WHERE a.id = target_appointment_id;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    IF TG_TABLE_NAME = 'appointments' THEN
        IF NEW.booking_source = 'DOCTOR_FOLLOW_UP' AND NEW.source_appointment_id IS NOT NULL THEN
            SELECT v.originating_appointment_id INTO visit_appointment_id
            FROM visits v WHERE v.id = NEW.source_visit_id;
            IF visit_appointment_id IS DISTINCT FROM NEW.source_appointment_id THEN
                RAISE EXCEPTION 'follow-up source appointment must match the source visit' USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;

    IF appointment_visit_id IS NOT NULL THEN
        SELECT v.originating_appointment_id, v.source INTO visit_appointment_id, visit_source
        FROM visits v WHERE v.id = appointment_visit_id;
        IF visit_appointment_id IS DISTINCT FROM target_appointment_id OR visit_source IS DISTINCT FROM 'SCHEDULED' THEN
            RAISE EXCEPTION 'appointment visit link is inconsistent' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF appointment_status IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED') AND appointment_visit_id IS NULL THEN
        RAISE EXCEPTION 'checked-in appointment requires a linked visit' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

-- Walk-in entries only: the visit must be WALK_IN, the row starts WAITING, and arrival equals the visit's arrival.
CREATE OR REPLACE FUNCTION enforce_walk_in_reception_queue_visit() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    visit_source VARCHAR(16);
    visit_arrived_at TIMESTAMPTZ;
BEGIN
    IF NEW.stage <> 'RECEPTION' THEN
        RETURN NEW;
    END IF;
    SELECT source, arrived_at INTO visit_source, visit_arrived_at
    FROM visits WHERE id = NEW.visit_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'queue entry Visit does not exist' USING ERRCODE = '23503';
    END IF;
    IF visit_source IS DISTINCT FROM 'WALK_IN' THEN
        RAISE EXCEPTION 'only WALK_IN Visits may enter reception queue' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'INSERT' AND NEW.status IS DISTINCT FROM 'WAITING' THEN
        RAISE EXCEPTION 'new reception queue entries must start in WAITING' USING ERRCODE = '23514';
    END IF;
    IF NEW.arrived_at IS DISTINCT FROM visit_arrived_at THEN
        RAISE EXCEPTION 'queue entry arrival time must match Visit arrival time' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

-- Reception entries only. stage is also frozen here, so a RECEPTION row cannot turn into an EXAM row.
CREATE OR REPLACE FUNCTION protect_walk_in_reception_queue_entry_lifecycle() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.stage IS DISTINCT FROM 'RECEPTION' THEN
        RETURN NEW;
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.stage IS DISTINCT FROM OLD.stage
       OR NEW.visit_id IS DISTINCT FROM OLD.visit_id
       OR NEW.arrived_at IS DISTINCT FROM OLD.arrived_at
       OR NEW.entered_at IS DISTINCT FROM OLD.entered_at THEN
        RAISE EXCEPTION 'reception queue entry identity and arrival fields are immutable' USING ERRCODE = '55000';
    END IF;
    IF OLD.status IS DISTINCT FROM 'WAITING' THEN
        RAISE EXCEPTION 'closed reception queue entries cannot be reopened or changed' USING ERRCODE = '55000';
    END IF;
    IF NEW.status IS DISTINCT FROM 'LEFT_RECEPTION' THEN
        RAISE EXCEPTION 'active reception queue entries may only transition to LEFT_RECEPTION' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

-- Same behaviour as before, reading reception rows of queue_entries.
CREATE OR REPLACE FUNCTION protect_queued_visit_source_and_arrival() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM queue_entries q WHERE q.visit_id = OLD.id AND q.stage = 'RECEPTION') THEN
        IF NEW.source IS DISTINCT FROM OLD.source THEN
            RAISE EXCEPTION 'Visit source cannot change after a reception queue entry exists' USING ERRCODE = '23514';
        END IF;
        IF NEW.arrived_at IS DISTINCT FROM OLD.arrived_at THEN
            RAISE EXCEPTION 'Visit arrival time cannot change after a reception queue entry exists' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_queue_entries_reception_visit_guard
    BEFORE INSERT OR UPDATE OF visit_id, arrived_at, status, entered_at, left_at, exit_reason ON queue_entries
    FOR EACH ROW EXECUTE FUNCTION enforce_walk_in_reception_queue_visit();

CREATE TRIGGER tr_queue_entries_reception_lifecycle
    BEFORE UPDATE ON queue_entries
    FOR EACH ROW EXECUTE FUNCTION protect_walk_in_reception_queue_entry_lifecycle();

-- Drop the old tables. The history references the entries table, so it goes first.
DROP TABLE walk_in_reception_queue_history;
DROP TABLE walk_in_reception_queue_entries;
DROP TABLE visit_exam_queue;
DROP TABLE visit_check_ins;
DROP TABLE walk_in_requests;
DROP TABLE visit_audits;
DROP TABLE visit_assignment_audits;
DROP TABLE visit_examinations;
