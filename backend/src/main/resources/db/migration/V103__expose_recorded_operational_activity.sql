-- UC67 read composition only. Each source already records actor/time/target/action.
-- No authentication events, secrets, clinical content, JSON metadata, findings, payment payloads
-- or inferred actors. Owner tables are read through a safe reporting view, never mutated here.
DO $$
DECLARE previous_definition TEXT;
BEGIN
    SELECT regexp_replace(pg_get_viewdef(to_regclass(format('%I.activity_log_entries', current_schema())), true), ';\s*$', '')
        INTO previous_definition;
    EXECUTE 'CREATE OR REPLACE VIEW activity_log_entries AS ' || previous_definition || $projection$
    UNION ALL
    SELECT 'account:' || a.id::text, a.occurred_at, a.actor_account_id, 'ACCOUNT'::text,
        a.action::text, 'ACCOUNT'::text, a.account_id, a.account_id::text, NULL::text
    FROM account_management_audits a
    UNION ALL
    SELECT 'appointment:' || a.id::text, a.occurred_at, a.actor_account_id, 'APPOINTMENT'::text,
        a.action::text, 'APPOINTMENT'::text, a.appointment_id, a.appointment_id::text, NULL::text
    FROM appointment_audits a
    UNION ALL
    SELECT 'visit:' || a.id::text, a.occurred_at, a.actor_account_id, 'VISIT'::text,
        a.action::text, 'VISIT'::text, a.visit_id, a.visit_id::text, NULL::text
    FROM visit_audits a
    UNION ALL
    SELECT 'care:' || a.id::text, a.occurred_at, a.actor_account_id, 'VISIT'::text,
        a.action::text, 'VISIT_ASSIGNMENT'::text, coalesce(a.assignment_id, a.visit_id),
        a.visit_id::text, NULL::text
    FROM visit_assignment_audits a
    UNION ALL
    SELECT 'room_reservation:' || a.id::text, a.occurred_at, a.actor_account_id, 'ROOM'::text,
        a.action::text, 'ROOM_RESERVATION'::text, coalesce(a.reservation_id, a.visit_id),
        a.visit_id::text, NULL::text
    FROM room_reservation_audits a
    UNION ALL
    SELECT 'queue:' || a.id::text, a.occurred_at, a.actor_account_id, 'VISIT'::text,
        coalesce(a.reason, a.to_status)::text, 'VISIT'::text, q.visit_id, q.visit_id::text, NULL::text
    FROM walk_in_reception_queue_history a JOIN walk_in_reception_queue_entries q ON q.id = a.queue_entry_id
    UNION ALL
    SELECT 'diagnostic:' || a.id::text, coalesce(a.occurred_at, a.recorded_at), a.actor_account_id,
        'DIAGNOSTIC'::text, (a.action || ':' || a.outcome)::text, 'DIAGNOSTIC_REQUEST'::text,
        coalesce(a.request_id, a.visit_id), coalesce(a.request_id, a.visit_id)::text, NULL::text
    FROM diagnostic_audit_events a
    UNION ALL
    SELECT 'diagnostic_access:' || a.id::text, a.recorded_at, a.actor_account_id, 'DIAGNOSTIC'::text,
        'VIEW_HISTORY'::text, a.scope_type::text, a.scope_id, a.scope_id::text, NULL::text
    FROM diagnostic_history_access_audits a
    $projection$;
END;
$$;
