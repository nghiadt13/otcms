-- Exam queue per visit (plans/261111-0208-visit-flow-r3, phase BE-1).
-- One row per visits.id: the single source of truth for exam status, queue number and responsible doctor.
-- Legal status moves are enforced in VisitExamQueueServiceImpl; this file guards shape and uniqueness.

CREATE TABLE visit_exam_queue (
    visit_id           UUID PRIMARY KEY REFERENCES visits(id) ON DELETE RESTRICT,
    doctor_profile_id  UUID NOT NULL REFERENCES doctor_profiles(id) ON DELETE RESTRICT,
    room_id            UUID NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    service_id         UUID NULL REFERENCES clinic_services(id) ON DELETE RESTRICT,
    clinic_date        DATE NOT NULL,
    queue_number       INTEGER NULL,
    exam_status        VARCHAR(32) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    queued_at          TIMESTAMPTZ NULL,
    started_at         TIMESTAMPTZ NULL,
    awaiting_at        TIMESTAMPTZ NULL,
    finished_at        TIMESTAMPTZ NULL,
    CONSTRAINT ck_visit_exam_queue_status CHECK (exam_status IN (
        'PENDING_PAYMENT', 'WAITING', 'IN_EXAMINATION', 'AWAITING_CONCLUSION', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_visit_exam_queue_number CHECK (
        (exam_status = 'PENDING_PAYMENT' AND queue_number IS NULL)
        OR exam_status = 'CANCELLED'
        OR (exam_status NOT IN ('PENDING_PAYMENT', 'CANCELLED') AND queue_number IS NOT NULL AND queue_number > 0))
);

-- Queue numbers are unique per doctor and clinic day (cancelled rows keep their number).
CREATE UNIQUE INDEX uk_visit_exam_queue_number
    ON visit_exam_queue (doctor_profile_id, clinic_date, queue_number)
    WHERE queue_number IS NOT NULL;

-- A doctor examines one patient at a time.
CREATE UNIQUE INDEX uk_visit_exam_queue_one_in_exam
    ON visit_exam_queue (doctor_profile_id)
    WHERE exam_status = 'IN_EXAMINATION';

CREATE INDEX ix_visit_exam_queue_date
    ON visit_exam_queue (clinic_date, doctor_profile_id);

-- Backfill for visits that were already open before this table existed (plan section 2, "Du lieu cu").
-- Clinic day is taken in Asia/Ho_Chi_Minh; numbers follow check-in order per doctor and clinic day.
-- Legacy walk-ins without a care assignment get no row: reception cancels them by hand.
WITH legacy_in_care AS (
    SELECT DISTINCT ON (a.visit_id)
           a.visit_id, a.doctor_profile_id, a.starts_at AS started_at, 'AWAITING_CONCLUSION' AS exam_status
    FROM visit_assignments a
    JOIN visits v ON v.id = a.visit_id AND v.status = 'OPEN'
    WHERE a.status = 'IN_PROGRESS'
    ORDER BY a.visit_id, a.starts_at DESC, a.id DESC
),
legacy_scheduled_waiting AS (
    SELECT v.id AS visit_id, ap.doctor_profile_id, NULL::TIMESTAMPTZ AS started_at, 'WAITING' AS exam_status
    FROM visits v
    JOIN appointments ap ON ap.id = v.originating_appointment_id
    WHERE v.status = 'OPEN'
      AND v.source = 'SCHEDULED'
      AND NOT EXISTS (SELECT 1 FROM visit_assignments a WHERE a.visit_id = v.id)
),
legacy AS (
    SELECT * FROM legacy_in_care
    UNION ALL
    SELECT * FROM legacy_scheduled_waiting
)
INSERT INTO visit_exam_queue (visit_id, doctor_profile_id, room_id, service_id, clinic_date, queue_number,
                              exam_status, created_at, queued_at, started_at, awaiting_at, finished_at)
SELECT l.visit_id,
       l.doctor_profile_id,
       NULL::UUID,
       NULL::UUID,
       (v.checked_in_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::DATE,
       ROW_NUMBER() OVER (PARTITION BY l.doctor_profile_id, (v.checked_in_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::DATE
                          ORDER BY v.checked_in_at, v.id),
       l.exam_status,
       v.created_at,
       v.checked_in_at,
       l.started_at,
       NULL::TIMESTAMPTZ,
       NULL::TIMESTAMPTZ
FROM legacy l
JOIN visits v ON v.id = l.visit_id;
