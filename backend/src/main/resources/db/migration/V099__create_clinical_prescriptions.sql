-- Clinical-owned UUID linkage follows V073: Visit eligibility/identity is checked through its public port.
CREATE TABLE clinical_prescriptions (
    id uuid PRIMARY KEY,
    visit_id uuid NOT NULL,
    patient_profile_id uuid NOT NULL,
    doctor_profile_id uuid NOT NULL,
    status varchar(16) NOT NULL,
    issued_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    issuance_key varchar(128),
    issuance_expected_version bigint,
    CONSTRAINT uk_clinical_prescription_visit UNIQUE (visit_id),
    CONSTRAINT ck_clinical_prescription_version CHECK (version >= 1),
    CONSTRAINT ck_clinical_prescription_status CHECK (status IN ('DRAFT', 'ISSUED')),
    CONSTRAINT ck_clinical_prescription_issuance CHECK (
        (status = 'DRAFT' AND issued_at IS NULL AND issuance_key IS NULL AND issuance_expected_version IS NULL)
        OR (status = 'ISSUED' AND issued_at IS NOT NULL AND issuance_key IS NOT NULL
            AND length(trim(issuance_key)) BETWEEN 1 AND 128 AND issuance_expected_version IS NOT NULL
            AND issuance_expected_version >= 1 AND version = issuance_expected_version + 1)
    )
);
CREATE INDEX ix_clinical_prescription_issued_patient
    ON clinical_prescriptions (patient_profile_id, issued_at DESC, id DESC) WHERE status = 'ISSUED';

CREATE TABLE clinical_prescription_items (
    id uuid PRIMARY KEY,
    prescription_id uuid NOT NULL REFERENCES clinical_prescriptions(id) ON DELETE RESTRICT,
    line_number integer NOT NULL CHECK (line_number > 0),
    medicine varchar(255) NOT NULL CHECK (length(trim(medicine)) > 0),
    dose varchar(255) NOT NULL CHECK (length(trim(dose)) > 0),
    frequency varchar(255) NOT NULL CHECK (length(trim(frequency)) > 0),
    usage varchar(2000) NOT NULL CHECK (length(trim(usage)) > 0),
    CONSTRAINT uk_clinical_prescription_line UNIQUE (prescription_id, line_number)
);
