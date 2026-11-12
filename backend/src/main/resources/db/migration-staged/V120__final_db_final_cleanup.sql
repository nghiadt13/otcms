-- final-db phase 10: both staff audit writers have moved to audit_logs.
-- V110 copied DOCTOR_PROFILE/TIME_OFF; V112 copied WORK_SHIFT.
-- Explicit drops keep an unexpected dependency visible rather than using CASCADE.
DROP TRIGGER tr_staff_resource_audits_immutable ON staff_resource_audits;
DROP TRIGGER tr_staff_resource_audits_no_truncate ON staff_resource_audits;
DROP TABLE staff_resource_audits;
DROP FUNCTION reject_staff_resource_audit_mutation();
