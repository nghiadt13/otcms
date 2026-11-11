-- V099 is reserved by Prescription. External linkage follows Clinical V073 public-context authorization.
CREATE TABLE clinical_medical_documents (
    id uuid PRIMARY KEY,
    visit_id uuid NOT NULL,
    patient_profile_id uuid NOT NULL,
    document_type varchar(32) NOT NULL CHECK (document_type IN ('CLINIC_EXAMINATION_REPORT','EXTERNAL_REFERRAL','EXTERNAL_REPORT')),
    title varchar(255) NOT NULL CHECK (length(trim(title)) > 0),
    original_filename varchar(255) NOT NULL CHECK (length(trim(original_filename)) > 0),
    media_type varchar(80) NOT NULL CHECK (media_type IN ('application/pdf','image/jpeg','image/png')),
    byte_size bigint NOT NULL CHECK (byte_size BETWEEN 1 AND 2097152),
    released_at timestamptz,
    released_by_doctor_profile_id uuid,
    version bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_clinical_document_report_pdf CHECK (document_type <> 'CLINIC_EXAMINATION_REPORT' OR media_type = 'application/pdf'),
    CONSTRAINT ck_clinical_document_release CHECK ((released_at IS NULL AND released_by_doctor_profile_id IS NULL)
        OR (released_at IS NOT NULL AND released_by_doctor_profile_id IS NOT NULL))
);
CREATE INDEX ix_clinical_documents_visit ON clinical_medical_documents (visit_id, created_at DESC, id DESC);
CREATE INDEX ix_clinical_documents_patient ON clinical_medical_documents (patient_profile_id, created_at DESC, id DESC);
CREATE INDEX ix_clinical_documents_released ON clinical_medical_documents (visit_id, created_at DESC, id DESC) WHERE released_at IS NOT NULL;

CREATE TABLE clinical_medical_document_payloads (
    document_id uuid PRIMARY KEY REFERENCES clinical_medical_documents(id) ON DELETE RESTRICT,
    content bytea NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 2097152)
);
