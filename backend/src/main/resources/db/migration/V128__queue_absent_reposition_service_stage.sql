ALTER TABLE queue_entries
    ADD COLUMN sort_order bigint,
    ADD COLUMN called_at timestamptz,
    ADD COLUMN absent_at timestamptz,
    ADD COLUMN service_order_id uuid REFERENCES clinical_service_orders(id);

-- Reception lifecycle protection forbids updates to closed entries, including backfills.
DROP TRIGGER tr_queue_entries_reception_lifecycle ON queue_entries;
UPDATE queue_entries SET sort_order = (extract(epoch FROM coalesce(arrived_at, entered_at)) * 1000)::bigint;
CREATE TRIGGER tr_queue_entries_reception_lifecycle BEFORE UPDATE ON queue_entries
    FOR EACH ROW EXECUTE FUNCTION protect_walk_in_reception_queue_entry_lifecycle();

ALTER TABLE queue_entries ALTER COLUMN sort_order SET NOT NULL;

-- Existing writers, including native inserts, need the same check-in-based default.
CREATE FUNCTION queue_entries_default_sort_order() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.sort_order IS NULL THEN
        NEW.sort_order := (extract(epoch FROM coalesce(NEW.arrived_at, NEW.entered_at)) * 1000)::bigint;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tr_queue_entries_default_sort_order BEFORE INSERT ON queue_entries
    FOR EACH ROW EXECUTE FUNCTION queue_entries_default_sort_order();

ALTER TABLE queue_entries DROP CONSTRAINT ck_queue_entries_stage;
ALTER TABLE queue_entries ADD CONSTRAINT ck_queue_entries_stage
    CHECK (stage IN ('RECEPTION', 'EXAM', 'SERVICE', 'RESULT_REVIEW'));
ALTER TABLE queue_entries DROP CONSTRAINT ck_queue_entries_exam;
ALTER TABLE queue_entries ADD CONSTRAINT ck_queue_entries_exam CHECK (
    stage <> 'EXAM' OR (
        doctor_profile_id IS NOT NULL AND clinic_date IS NOT NULL
        AND arrived_at IS NULL AND left_at IS NULL AND exit_reason IS NULL
        AND ((status = 'PENDING_PAYMENT' AND queue_number IS NULL)
            OR status = 'CANCELLED'
            OR (status IN ('WAITING', 'CALLED', 'ABSENT', 'IN_EXAMINATION', 'AWAITING_CONCLUSION', 'COMPLETED')
                AND queue_number IS NOT NULL AND queue_number > 0))
    )
);
ALTER TABLE queue_entries ADD CONSTRAINT ck_queue_entries_service CHECK (
    stage <> 'SERVICE' OR (room_id IS NOT NULL AND service_order_id IS NOT NULL)
);
CREATE INDEX ix_queue_entries_room_order ON queue_entries (stage, room_id, status, sort_order);
