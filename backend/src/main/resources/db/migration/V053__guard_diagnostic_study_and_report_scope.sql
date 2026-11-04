CREATE FUNCTION enforce_diagnostic_request_study_state()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    linked_study_ref text;
BEGIN
    IF NEW.status IN ('IN_PROGRESS', 'RESULT_AVAILABLE', 'REVIEWED') THEN
        SELECT attempt.study_ref INTO linked_study_ref
        FROM diagnostic_attempts attempt
        WHERE attempt.request_id = NEW.id AND attempt.id = NEW.current_attempt_id;

        IF NOT FOUND THEN
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

CREATE TRIGGER tr_diagnostic_requests_study_state
    BEFORE INSERT OR UPDATE OF status, current_attempt_id ON diagnostic_requests
    FOR EACH ROW EXECUTE FUNCTION enforce_diagnostic_request_study_state();

CREATE FUNCTION enforce_diagnostic_report_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    request_visit_id uuid;
    current_study_ref text;
BEGIN
    SELECT request.visit_id, attempt.study_ref
    INTO request_visit_id, current_study_ref
    FROM diagnostic_requests request
    JOIN diagnostic_attempts attempt
      ON attempt.request_id = request.id AND attempt.id = request.current_attempt_id
    WHERE request.id = NEW.request_id;

    IF NOT FOUND OR request_visit_id <> NEW.visit_id OR current_study_ref IS NULL
            OR current_study_ref <> NEW.study_ref THEN
        RAISE EXCEPTION 'diagnostic report must match the request Visit and current study';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_diagnostic_reports_scope
    BEFORE INSERT OR UPDATE OF request_id, visit_id, study_ref ON diagnostic_reports
    FOR EACH ROW EXECUTE FUNCTION enforce_diagnostic_report_scope();

CREATE FUNCTION enforce_diagnostic_findings_scope()
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
    SELECT request.visit_id, attempt.study_ref
    INTO request_visit_id, current_study_ref
    FROM diagnostic_requests request
    JOIN diagnostic_attempts attempt
      ON attempt.request_id = request.id AND attempt.id = request.current_attempt_id
    WHERE request.id = NEW.request_id;

    IF NOT FOUND OR request_visit_id <> NEW.visit_id OR current_study_ref IS NULL
            OR current_study_ref <> NEW.study_ref THEN
        RAISE EXCEPTION 'diagnostic findings must match the request Visit and current study';
    END IF;

    IF NEW.state = 'COMPLETED' THEN
        SELECT report.validation_status, report.visit_id, report.study_ref
        INTO report_validation_status, report_visit_id, report_study_ref
        FROM diagnostic_reports report
        WHERE report.request_id = NEW.request_id AND report.id = NEW.required_pdf_report_id;

        IF NOT FOUND OR report_validation_status <> 'VALIDATED'
                OR report_visit_id <> NEW.visit_id OR report_study_ref <> NEW.study_ref THEN
            RAISE EXCEPTION 'completed diagnostic findings require a validated PDF for the same Visit and study';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_diagnostic_findings_scope
    BEFORE INSERT OR UPDATE OF request_id, visit_id, study_ref, state, required_pdf_report_id
    ON diagnostic_findings_versions
    FOR EACH ROW EXECUTE FUNCTION enforce_diagnostic_findings_scope();

CREATE FUNCTION prevent_used_diagnostic_report_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.released_at IS NOT NULL OR EXISTS (
        SELECT 1 FROM diagnostic_findings_versions findings
        WHERE findings.required_pdf_report_id = OLD.id AND findings.state = 'COMPLETED'
    ) THEN
        RAISE EXCEPTION 'released or completed-findings diagnostic reports are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_diagnostic_reports_used_immutable
    BEFORE UPDATE OR DELETE ON diagnostic_reports
    FOR EACH ROW EXECUTE FUNCTION prevent_used_diagnostic_report_mutation();
