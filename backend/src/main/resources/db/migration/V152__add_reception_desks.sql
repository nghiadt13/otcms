-- Reception desks are staff locations, not rooms that provide clinic services.
ALTER TABLE rooms DROP CONSTRAINT ck_rooms_type;

ALTER TABLE rooms
    ADD CONSTRAINT ck_rooms_type
        CHECK (room_type IN ('EXAMINATION', 'IMAGING', 'LABORATORY', 'RECEPTION'));
