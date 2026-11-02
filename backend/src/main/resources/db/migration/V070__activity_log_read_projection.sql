-- Read-only, safe audit projection. Source names follow product's actual schema.
-- ACCOUNT/REVIEW/CONTENT/ANNOUNCEMENT will be added by later migrations after their owners merge them.
-- Authentication events and medical/payment payloads are deliberately excluded.
CREATE VIEW activity_log_entries AS
SELECT 'staff:' || s.id::text AS id, s.occurred_at, s.actor_account_id,
    CASE s.resource_type WHEN 'DOCTOR_PROFILE' THEN 'STAFF_PROFILE' ELSE 'STAFF_SHIFT' END::text AS category,
    CASE WHEN s.resource_type = 'DOCTOR_PROFILE' THEN
        CASE s.action WHEN 'CREATED' THEN 'CREATE_STAFF_PROFILE' ELSE 'UPDATE_STAFF_PROFILE' END
    ELSE CASE s.action WHEN 'CREATED' THEN 'CREATE_STAFF_SHIFT' WHEN 'DELETED' THEN 'CANCEL_STAFF_SHIFT' ELSE 'UPDATE_STAFF_SHIFT' END END::text AS action,
    s.resource_type::text AS record_type, s.resource_id AS record_id,
    coalesce(d.full_name, s.resource_id::text)::text AS record_label, NULL::text AS correction_reason
FROM staff_resource_audits s LEFT JOIN doctor_profiles d ON d.id = s.doctor_profile_id
WHERE s.resource_type IN ('DOCTOR_PROFILE', 'WORK_SHIFT')
UNION ALL
SELECT 'room:' || a.id::text, a.occurred_at, a.actor_account_id, 'ROOM'::text,
    CASE a.action WHEN 'CREATED' THEN 'CREATE_ROOM' WHEN 'UPDATED' THEN 'UPDATE_ROOM' ELSE 'CHANGE_ROOM_STATUS' END::text,
    'ROOM'::text, a.room_id, coalesce(r.room_code, a.room_id::text)::text, NULL::text
FROM room_management_audits a LEFT JOIN rooms r ON r.id = a.room_id
UNION ALL
SELECT 'service:' || a.id::text, a.occurred_at, a.actor_account_id, 'SERVICE'::text,
    CASE WHEN a.resource_type = 'DOCTOR_ASSIGNMENT' THEN 'UPDATE_SERVICE'
         WHEN a.action = 'CREATED' THEN 'CREATE_SERVICE' WHEN a.action = 'UPDATED' THEN 'UPDATE_SERVICE'
         ELSE 'CHANGE_SERVICE_STATUS' END::text,
    'SERVICE'::text, a.service_id, coalesce(s.name, a.service_id::text)::text, NULL::text
FROM service_audits a LEFT JOIN clinic_services s ON s.id = a.service_id;

-- Handle the supported out-of-order case where KAN-41 was installed before this view.
DO $$
DECLARE previous_definition TEXT;
BEGIN
    IF to_regclass(format('%I.content_audits', current_schema())) IS NOT NULL THEN
        SELECT regexp_replace(pg_get_viewdef(to_regclass(format('%I.activity_log_entries', current_schema())), true), ';\s*$', '') INTO previous_definition;
        IF position('content_audits' in previous_definition) = 0 THEN
            EXECUTE 'CREATE OR REPLACE VIEW activity_log_entries AS ' || previous_definition ||
                ' UNION ALL SELECT ''content:'' || c.id::text, c.occurred_at, c.actor_account_id, c.category::text,
                  c.action::text, c.category::text, c.record_id, c.record_label::text, NULL::text FROM content_audits c';
        END IF;
    END IF;
END;
$$;
