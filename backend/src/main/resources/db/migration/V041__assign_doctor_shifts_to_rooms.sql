ALTER TABLE doctor_shifts
    ADD COLUMN room_id UUID REFERENCES rooms(id) ON DELETE RESTRICT;

CREATE INDEX ix_doctor_shifts_room_interval
    ON doctor_shifts (room_id, starts_at, ends_at)
    WHERE room_id IS NOT NULL;

ALTER TABLE doctor_shifts
    ADD CONSTRAINT ex_doctor_shift_room_no_overlap EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (room_id IS NOT NULL);
