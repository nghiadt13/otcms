CREATE TABLE visit_rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_code VARCHAR(64) NOT NULL UNIQUE,
    display_label VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_visit_rooms_code CHECK (length(trim(room_code)) BETWEEN 1 AND 64),
    CONSTRAINT ck_visit_rooms_label CHECK (length(trim(display_label)) BETWEEN 1 AND 128)
);

CREATE TABLE visit_room_capabilities (
    room_id UUID NOT NULL REFERENCES visit_rooms(id) ON DELETE RESTRICT,
    capability_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (room_id, capability_code),
    CONSTRAINT ck_visit_room_capability_code CHECK (length(trim(capability_code)) BETWEEN 1 AND 64)
);

CREATE INDEX ix_visit_room_capabilities_code ON visit_room_capabilities(capability_code, room_id);

CREATE TABLE visit_room_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    visit_id UUID NOT NULL,
    assignment_id UUID NOT NULL,
    room_id UUID NOT NULL REFERENCES visit_rooms(id) ON DELETE RESTRICT,
    step_code VARCHAR(64) NOT NULL,
    capability_code VARCHAR(64) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    expected_ends_at TIMESTAMPTZ NOT NULL,
    reserved_until TIMESTAMPTZ NOT NULL,
    cleanup_buffer_minutes INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    actual_ended_at TIMESTAMPTZ,
    assigned_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_visit_room_reservation_assignment
        FOREIGN KEY (assignment_id, visit_id)
        REFERENCES visit_care_assignments(id, visit_id) ON DELETE RESTRICT,
    CONSTRAINT ck_visit_room_reservation_step CHECK (length(trim(step_code)) BETWEEN 1 AND 64),
    CONSTRAINT ck_visit_room_reservation_capability CHECK (length(trim(capability_code)) BETWEEN 1 AND 64),
    CONSTRAINT ck_visit_room_reservation_interval
        CHECK (starts_at < expected_ends_at AND starts_at <= reserved_until),
    CONSTRAINT ck_visit_room_reservation_buffer CHECK (cleanup_buffer_minutes >= 0),
    CONSTRAINT ck_visit_room_reservation_status CHECK (status IN ('IN_PROGRESS', 'CLEANING', 'RELEASED')),
    CONSTRAINT ck_visit_room_reservation_actual_end CHECK (actual_ended_at IS NULL OR actual_ended_at >= starts_at)
);

ALTER TABLE visit_room_reservations
    ADD CONSTRAINT ex_visit_room_reservation_no_overlap
    EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(starts_at, reserved_until, '[)') WITH &&
    ) WHERE (status IN ('IN_PROGRESS', 'CLEANING'));

CREATE INDEX ix_visit_room_reservations_assignment
    ON visit_room_reservations(assignment_id, status, starts_at);
CREATE INDEX ix_visit_room_reservations_room_time
    ON visit_room_reservations(room_id, starts_at, reserved_until);

CREATE TABLE visit_room_reservation_audits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    visit_id UUID NOT NULL REFERENCES clinic_visits(id) ON DELETE RESTRICT,
    assignment_id UUID,
    reservation_id UUID,
    room_id UUID,
    actor_account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    action VARCHAR(32) NOT NULL,
    step_code VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    CONSTRAINT fk_visit_room_audit_assignment
        FOREIGN KEY (assignment_id, visit_id)
        REFERENCES visit_care_assignments(id, visit_id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_room_audit_reservation
        FOREIGN KEY (reservation_id) REFERENCES visit_room_reservations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_visit_room_audit_room
        FOREIGN KEY (room_id) REFERENCES visit_rooms(id) ON DELETE RESTRICT,
    CONSTRAINT ck_visit_room_audit_action
        CHECK (action IN ('RESERVED', 'EXTENDED', 'COMPLETED', 'CAPACITY_CONFLICT')),
    CONSTRAINT ck_visit_room_audit_step CHECK (length(trim(step_code)) BETWEEN 1 AND 64),
    CONSTRAINT ck_visit_room_audit_correlation CHECK (length(trim(correlation_id)) BETWEEN 1 AND 128)
);

CREATE UNIQUE INDEX uq_visit_room_capacity_conflict_correlation
    ON visit_room_reservation_audits(visit_id, actor_account_id, correlation_id)
    WHERE action = 'CAPACITY_CONFLICT';
CREATE INDEX ix_visit_room_reservation_audits_visit_time
    ON visit_room_reservation_audits(visit_id, occurred_at, id);

CREATE FUNCTION reject_visit_room_reservation_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'visit room reservation audit is append-only' USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_visit_room_reservation_audits_immutable
    BEFORE UPDATE OR DELETE ON visit_room_reservation_audits
    FOR EACH ROW EXECUTE FUNCTION reject_visit_room_reservation_audit_mutation();

CREATE TRIGGER tr_visit_room_reservation_audits_no_truncate
    BEFORE TRUNCATE ON visit_room_reservation_audits
    FOR EACH STATEMENT EXECUTE FUNCTION reject_visit_room_reservation_audit_mutation();
