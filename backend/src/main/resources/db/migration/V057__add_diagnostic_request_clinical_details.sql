ALTER TABLE diagnostic_requests
    ALTER COLUMN imaging_type TYPE varchar(80),
    ADD COLUMN body_area varchar(120) NOT NULL DEFAULT 'UNSPECIFIED',
    ADD COLUMN clinical_reason text NOT NULL DEFAULT 'Legacy diagnostic request';

ALTER TABLE diagnostic_requests
    ADD CONSTRAINT ck_diagnostic_requests_body_area CHECK (
        length(trim(body_area)) BETWEEN 1 AND 120
    ),
    ADD CONSTRAINT ck_diagnostic_requests_clinical_reason CHECK (
        length(trim(clinical_reason)) BETWEEN 1 AND 2000
    ),
    ADD CONSTRAINT ck_diagnostic_requests_cancellation_cause CHECK (
        status <> 'CANCELLED' OR cancellation_cause IN ('PATIENT_DECLINED', 'CLINIC_UNABLE')
    );
