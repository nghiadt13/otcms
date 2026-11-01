-- KAN-40 should merge before KAN-41. On a standalone KAN-41 checkout the log view may not exist yet.
DO $$
DECLARE previous_definition TEXT;
BEGIN
    IF to_regclass('activity_log_entries') IS NOT NULL THEN
        SELECT regexp_replace(pg_get_viewdef('activity_log_entries'::regclass, true), ';\s*$', '') INTO previous_definition;
        EXECUTE 'CREATE OR REPLACE VIEW activity_log_entries AS ' || previous_definition ||
            ' UNION ALL SELECT ''content:'' || c.id::text, c.occurred_at, c.actor_account_id, c.category::text,
              c.action::text, c.category::text, c.record_id, c.record_label::text, NULL::text FROM content_audits c';
    END IF;
END;
$$;
