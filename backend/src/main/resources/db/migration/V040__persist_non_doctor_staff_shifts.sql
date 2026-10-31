CREATE TABLE staff_work_shifts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_profile_id UUID NOT NULL REFERENCES staff_profiles(id) ON DELETE RESTRICT,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_staff_work_shift_interval CHECK (ends_at > starts_at),
    CONSTRAINT ex_staff_work_shift_no_overlap EXCLUDE USING gist (
        staff_profile_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    )
);

CREATE INDEX ix_staff_work_shifts_room_interval
    ON staff_work_shifts (room_id, starts_at, ends_at);
