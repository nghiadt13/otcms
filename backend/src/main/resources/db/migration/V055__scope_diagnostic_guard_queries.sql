CREATE OR REPLACE FUNCTION enforce_diagnostic_request_study_state()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    linked_study_ref text;
    current_attempt_exists boolean;
BEGIN
    IF NEW.status IN ('IN_PROGRESS', 'RESULT_AVAILABLE', 'REVIEWED') THEN
        EXECUTE format(
            'SELECT EXISTS (SELECT 1 FROM %I.diagnostic_attempts attempt '
                || 'WHERE attempt.request_id = $1 AND attempt.id = $2), '
                || '(SELECT attempt.study_ref FROM %I.diagnostic_attempts attempt '
                || 'WHERE attempt.request_id = $1 AND attempt.id = $2)',
            TG_TABLE_SCHEMA, TG_TABLE_SCHEMA)
            INTO current_attempt_exists, linked_study_ref
            USING NEW.id, NEW.current_attempt_id;

        IF NOT current_attempt_exists THEN
            RAISE EXCEPTION 'diagnostic request current attempt must belong to the request';
        END IF;
        IF NEW.status IN ('RESULT_AVAILABLE', 'REVIEWED') AND linked_study_ref IS NULL THEN
            RAISE EXCEPTION 'diagnostic result status requires a linked current study';
        END IF;
        IF NEW.status = 'IN_PROGRESS' AND linked_study_ref IS NOT NULL THEN
            RAISE EXCEPTION 'in-progress diagnostic request cannot have a linked study';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_diagnostic_report_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    request_visit_id uuid;
    current_study_ref text;
BEGIN
    EXECUTE format(
        'SELECT request.visit_id, attempt.study_ref '
            || 'FROM %I.diagnostic_requests request '
            || 'JOIN %I.diagnostic_attempts attempt '
            || 'ON attempt.request_id = request.id AND attempt.id = request.current_attempt_id '
            || 'WHERE request.id = $1',
        TG_TABLE_SCHEMA, TG_TABLE_SCHEMA)
        INTO request_visit_id, current_study_ref
        USING NEW.request_id;

    IF request_visit_id IS NULL OR request_visit_id <> NEW.visit_id OR current_study_ref IS NULL
            OR current_study_ref <> NEW.study_ref THEN
        RAISE EXCEPTION 'diagnostic report must match the request Visit and current study';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_diagnostic_findings_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    request_visit_id uuid;
    current_study_ref text;
    report_validation_status varchar(16);
    report_visit_id uuid;
    report_study_ref text;
BEGIN
    EXECUTE format(
        'SELECT request.visit_id, attempt.study_ref '
            || 'FROM %I.diagnostic_requests request '
            || 'JOIN %I.diagnostic_attempts attempt '
            || 'ON attempt.request_id = request.id AND attempt.id = request.current_attempt_id '
            || 'WHERE request.id = $1',
        TG_TABLE_SCHEMA, TG_TABLE_SCHEMA)
        INTO request_visit_id, current_study_ref
        USING NEW.request_id;

    IF request_visit_id IS NULL OR request_visit_id <> NEW.visit_id OR current_study_ref IS NULL
            OR current_study_ref <> NEW.study_ref THEN
        RAISE EXCEPTION 'diagnostic findings must match the request Visit and current study';
    END IF;

    IF NEW.state = 'COMPLETED' THEN
        EXECUTE format(
            'SELECT report.validation_status, report.visit_id, report.study_ref '
                || 'FROM %I.diagnostic_reports report '
                || 'WHERE report.request_id = $1 AND report.id = $2',
            TG_TABLE_SCHEMA)
            INTO report_validation_status, report_visit_id, report_study_ref
            USING NEW.request_id, NEW.required_pdf_report_id;

        IF report_validation_status IS DISTINCT FROM 'VALIDATED'
                OR report_visit_id IS DISTINCT FROM NEW.visit_id
                OR report_study_ref IS DISTINCT FROM NEW.study_ref THEN
            RAISE EXCEPTION 'completed diagnostic findings require a validated PDF for the same Visit and study';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION prevent_used_diagnostic_report_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    is_used_by_completed_findings boolean;
BEGIN
    EXECUTE format(
        'SELECT EXISTS (SELECT 1 FROM %I.diagnostic_findings_versions findings '
            || 'WHERE findings.required_pdf_report_id = $1 AND findings.state = ''COMPLETED'')',
        TG_TABLE_SCHEMA)
        INTO is_used_by_completed_findings
        USING OLD.id;

    IF OLD.released_at IS NOT NULL OR is_used_by_completed_findings THEN
        RAISE EXCEPTION 'released or completed-findings diagnostic reports are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;
