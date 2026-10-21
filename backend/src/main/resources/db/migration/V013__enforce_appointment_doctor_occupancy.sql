CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointments
    ADD COLUMN occupied_until TIMESTAMPTZ;

UPDATE appointments
SET occupied_until = end_time + make_interval(mins => buffer_minutes);

ALTER TABLE appointments
    ALTER COLUMN occupied_until SET NOT NULL,
    ADD CONSTRAINT ck_appointments_occupied_until CHECK (occupied_until >= end_time);

CREATE FUNCTION set_appointment_occupied_until() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    NEW.occupied_until := NEW.end_time + make_interval(mins => NEW.buffer_minutes);
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_appointments_occupied_until
    BEFORE INSERT OR UPDATE OF end_time, buffer_minutes, occupied_until ON appointments
    FOR EACH ROW EXECUTE FUNCTION set_appointment_occupied_until();

ALTER TABLE appointments
    ADD CONSTRAINT ex_appointments_doctor_occupied_range
        EXCLUDE USING gist (
            doctor_profile_id WITH =,
            tstzrange(start_time, occupied_until, '[)') WITH &&
        )
        WHERE (status IN ('CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED'));
