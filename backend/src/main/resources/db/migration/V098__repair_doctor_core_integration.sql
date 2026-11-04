-- UC38 creates clinical/operational requests; UC24 assigns rooms separately.
ALTER TABLE diagnostic_requests ALTER COLUMN room_id DROP NOT NULL;
ALTER TABLE diagnostic_requests ADD CONSTRAINT ck_diagnostic_request_assigned_before_use
    CHECK (room_id IS NOT NULL OR status IN ('PENDING', 'CANCELLED'));
COMMENT ON COLUMN diagnostic_requests.ordering_doctor_id IS 'Doctor account ID, not doctor profile ID';

-- Preserve the already-public Clinical UC38 body-area/TEXT reason contract (V073).
ALTER TABLE diagnostic_requests ALTER COLUMN body_area TYPE varchar(255);
ALTER TABLE diagnostic_requests DROP CONSTRAINT ck_diagnostic_requests_body_area;
ALTER TABLE diagnostic_requests ADD CONSTRAINT ck_diagnostic_requests_body_area
    CHECK (length(trim(body_area)) BETWEEN 1 AND 255);
ALTER TABLE diagnostic_requests DROP CONSTRAINT ck_diagnostic_requests_clinical_reason;
ALTER TABLE diagnostic_requests ADD CONSTRAINT ck_diagnostic_requests_clinical_reason
    CHECK (length(trim(clinical_reason)) > 0);

-- Explicit follow-up intent; existing free text cannot safely establish this fact.
ALTER TABLE clinical_examinations ADD COLUMN follow_up_indicated boolean;
COMMENT ON COLUMN clinical_examinations.follow_up_indicated IS 'Explicit Doctor recommendation; NULL means not yet stated';
