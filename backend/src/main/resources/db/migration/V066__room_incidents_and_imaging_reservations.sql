CREATE TABLE imaging_room_work_reservations (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL UNIQUE,
    room_id uuid NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    imaging_type varchar(80) NOT NULL,
    state varchar(16) NOT NULL,
    reserved_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    released_at timestamptz,
    release_cause varchar(64),
    CONSTRAINT ck_imaging_room_work_state CHECK (state IN ('RESERVED', 'RELEASED')),
    CONSTRAINT ck_imaging_room_work_release CHECK (
        (state = 'RESERVED' AND released_at IS NULL)
        OR (state = 'RELEASED' AND released_at IS NOT NULL AND release_cause IS NOT NULL)
    )
);
CREATE INDEX ix_imaging_room_work_queue ON imaging_room_work_reservations(room_id, state, reserved_at);

CREATE TABLE room_incident_requests (
    id uuid PRIMARY KEY,
    room_id uuid NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    diagnostic_request_id uuid NOT NULL,
    visit_id uuid NOT NULL,
    reported_by_account_id uuid NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    report_idempotency_key varchar(128) NOT NULL,
    report_fingerprint char(64) NOT NULL,
    incident_type varchar(40) NOT NULL,
    description text NOT NULL,
    status varchar(16) NOT NULL,
    decided_by_account_id uuid REFERENCES accounts(id) ON DELETE RESTRICT,
    decision_reason text,
    reported_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    decided_at timestamptz,
    restored_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_room_incident_status CHECK (status IN ('REPORTED', 'CONFIRMED', 'REJECTED', 'RESTORED')),
    CONSTRAINT ck_room_incident_type CHECK (incident_type IN ('MACHINE_FAILURE', 'ROOM_UNAVAILABLE', 'OTHER')),
    CONSTRAINT ck_room_incident_description CHECK (length(trim(description)) > 0),
    CONSTRAINT ck_room_incident_version CHECK (version >= 0),
    CONSTRAINT uq_room_incident_report_key UNIQUE (reported_by_account_id, report_idempotency_key)
);
CREATE INDEX ix_room_incident_review ON room_incident_requests(status, reported_at, id);
CREATE INDEX ix_room_incident_room ON room_incident_requests(room_id, reported_at DESC, id);
