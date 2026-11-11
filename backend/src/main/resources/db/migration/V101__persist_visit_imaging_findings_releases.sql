CREATE TABLE visit_imaging_findings_releases (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL UNIQUE,
    request_id uuid NOT NULL,
    visit_id uuid NOT NULL REFERENCES visits(id) ON DELETE RESTRICT,
    patient_id uuid NOT NULL,
    study_ref varchar(255) NOT NULL,
    findings_version_id uuid NOT NULL,
    report_id uuid NOT NULL,
    report_sha256 char(64) NOT NULL,
    payload_sha256 char(64) NOT NULL,
    released_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_visit_imaging_release_report_sha CHECK (report_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_visit_imaging_release_payload_sha CHECK (payload_sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_visit_imaging_findings_release_visit ON visit_imaging_findings_releases(visit_id, released_at DESC);
