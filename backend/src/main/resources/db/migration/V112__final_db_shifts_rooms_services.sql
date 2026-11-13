-- final-db phase 07: work_shifts (doctor and staff shifts), rooms.capabilities text[],
-- room, service and room-reservation audit rows moved to audit_logs, six legacy tables dropped.
-- Runs after V109 (audit_logs) and V110. V110 merges doctor_profiles into staff_profiles and repoints
-- doctor_profile_id to staff_profiles, so the FK below targets staff_profiles; no statement here reads doctor_profiles.
-- staff_resource_audits is not dropped here: only its WORK_SHIFT rows move. V120 drops the table.

-- 1. One table for doctor and staff shifts. No kind column: accounts.account_type tells them apart.
CREATE TABLE work_shifts (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_profile_id uuid        NOT NULL REFERENCES staff_profiles(id) ON DELETE RESTRICT,
    room_id          uuid        REFERENCES rooms(id) ON DELETE RESTRICT,
    starts_at        timestamptz NOT NULL,
    ends_at          timestamptz NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_work_shifts_interval CHECK (ends_at > starts_at),
    -- Leading column staff_profile_id also serves the FK lookup.
    CONSTRAINT ex_work_shifts_staff_no_overlap EXCLUDE USING gist
        (staff_profile_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&),
    -- Room overlap now covers staff shifts too (V108 enforced it for doctor shifts only).
    CONSTRAINT ex_work_shifts_room_no_overlap EXCLUDE USING gist
        (room_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&) WHERE (room_id IS NOT NULL)
);
CREATE INDEX ix_work_shifts_room_interval ON work_shifts (room_id, starts_at, ends_at) WHERE room_id IS NOT NULL;

-- 2. Copy demo shifts. doctor_shifts has no updated_at, so created_at is used.
INSERT INTO work_shifts (id, staff_profile_id, room_id, starts_at, ends_at, created_at, updated_at)
SELECT id, doctor_profile_id, room_id, starts_at, ends_at, created_at, created_at FROM doctor_shifts;
INSERT INTO work_shifts (id, staff_profile_id, room_id, starts_at, ends_at, created_at, updated_at)
SELECT id, staff_profile_id, room_id, starts_at, ends_at, created_at, updated_at FROM staff_work_shifts;

-- 3. rooms.capabilities replaces room_capabilities. GIN with the default array_ops (jsonb_path_ops is jsonb-only).
ALTER TABLE rooms ADD COLUMN capabilities text[] NOT NULL DEFAULT '{}';
UPDATE rooms r SET capabilities = coalesce((SELECT array_agg(DISTINCT c.capability_code::text ORDER BY c.capability_code::text)
    FROM room_capabilities c WHERE c.room_id = r.id), '{}');
CREATE INDEX ix_rooms_capabilities ON rooms USING gin (capabilities);

-- 4. Audit rows -> audit_logs. source = legacy table name. Legacy rows always have an actor, so actor_type = 'ACCOUNT'.
-- 4a. Only WORK_SHIFT rows. DOCTOR_PROFILE rows belong to phase 02; TIME_OFF rows go with the leave feature.
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, actor_type, before_state, after_state, correlation_id)
SELECT s.id, s.occurred_at, 'staff_resource_audits', 'STAFF_SHIFT',
       CASE s.action WHEN 'CREATED' THEN 'CREATE_STAFF_SHIFT'
                     WHEN 'DELETED' THEN 'CANCEL_STAFF_SHIFT'
                     ELSE 'UPDATE_STAFF_SHIFT' END,
       'WORK_SHIFT', s.resource_id, coalesce(acc.full_name, s.resource_id::text),
       true, s.actor_account_id, 'ACCOUNT', s.before_state, s.after_state, s.correlation_id
FROM staff_resource_audits s
LEFT JOIN staff_profiles sp ON sp.id = s.doctor_profile_id
LEFT JOIN accounts acc ON acc.id = sp.account_id
WHERE s.resource_type = 'WORK_SHIFT';

-- 4b. Services. Label fallback follows the old activity_log_entries view.
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, actor_type, before_state, after_state, metadata, correlation_id)
SELECT a.id, a.occurred_at, 'service_audits', 'SERVICE',
       CASE WHEN a.resource_type = 'DOCTOR_ASSIGNMENT' THEN 'UPDATE_SERVICE'
            WHEN a.action = 'CREATED' THEN 'CREATE_SERVICE'
            WHEN a.action = 'UPDATED' THEN 'UPDATE_SERVICE'
            ELSE 'CHANGE_SERVICE_STATUS' END,
       'SERVICE', a.service_id, coalesce(cs.name, a.service_id::text),
       true, a.actor_account_id, 'ACCOUNT', a.before_state, a.after_state,
       jsonb_build_object('resourceType', a.resource_type, 'resourceId', a.resource_id), a.correlation_id
FROM service_audits a
LEFT JOIN clinic_services cs ON cs.id = a.service_id;

-- 4c. Rooms. Label fallback follows the old activity_log_entries view.
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, actor_type, before_state, after_state, correlation_id)
SELECT a.id, a.occurred_at, 'room_management_audits', 'ROOM',
       CASE a.action WHEN 'CREATED' THEN 'CREATE_ROOM'
                     WHEN 'UPDATED' THEN 'UPDATE_ROOM'
                     ELSE 'CHANGE_ROOM_STATUS' END,
       'ROOM', a.room_id, coalesce(r.room_code, a.room_id::text),
       true, a.actor_account_id, 'ACCOUNT', a.before_state, a.after_state, a.correlation_id
FROM room_management_audits a
LEFT JOIN rooms r ON r.id = a.room_id;

-- 4d. Room reservations. CAPACITY_CONFLICT rows have no reservation_id, so entity_id = visit_id.
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, actor_type, metadata, correlation_id, dedup_key)
SELECT a.id, a.occurred_at, 'room_reservation_audits', 'ROOM', a.action, 'ROOM_RESERVATION',
       coalesce(a.reservation_id, a.visit_id), a.visit_id::text,
       true, a.actor_account_id, 'ACCOUNT',
       jsonb_build_object('visitId', a.visit_id, 'assignmentId', a.assignment_id, 'roomId', a.room_id, 'stepCode', a.step_code),
       a.correlation_id,
       CASE WHEN a.action = 'CAPACITY_CONFLICT' AND a.actor_account_id IS NOT NULL AND a.correlation_id IS NOT NULL
            THEN 'room-capacity:' || encode(sha256(convert_to(a.visit_id::text || ':' || a.actor_account_id::text || ':' || a.correlation_id, 'UTF8')), 'hex')
            ELSE NULL END
FROM room_reservation_audits a;

-- The shared dedup_key unique constraint replaces the old partial unique index.

-- 5. Triggers first, then their functions (no other object uses them), then the tables.
-- staff_resource_audits keeps its trigger and function until V120.
DROP TRIGGER tr_service_catalog_audits_immutable ON service_audits;
DROP TRIGGER tr_service_catalog_audits_no_truncate ON service_audits;
DROP TRIGGER tr_visit_room_management_audits_immutable ON room_management_audits;
DROP TRIGGER tr_visit_room_management_audits_no_truncate ON room_management_audits;
DROP TRIGGER tr_visit_room_reservation_audits_immutable ON room_reservation_audits;
DROP TRIGGER tr_visit_room_reservation_audits_no_truncate ON room_reservation_audits;
DROP FUNCTION reject_service_catalog_audit_mutation();
DROP FUNCTION reject_visit_room_management_audit_mutation();
DROP FUNCTION reject_visit_room_reservation_audit_mutation();
DROP TABLE doctor_shifts;
DROP TABLE staff_work_shifts;
DROP TABLE room_capabilities;
DROP TABLE service_audits;
DROP TABLE room_management_audits;
DROP TABLE room_reservation_audits;
