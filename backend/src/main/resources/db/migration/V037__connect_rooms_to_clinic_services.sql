ALTER TABLE clinic_services
    ADD COLUMN service_type VARCHAR(16) NOT NULL DEFAULT 'EXAMINATION';

UPDATE clinic_services
SET service_type = CASE
    WHEN lower(name) LIKE '%xét nghiệm%' OR lower(name) LIKE '%glucose%' OR lower(name) LIKE '%hba1c%'
        OR lower(name) LIKE '%beta hcg%' THEN 'LABORATORY'
    WHEN lower(name) LIKE '%mri%' OR lower(name) LIKE '%x-quang%' OR lower(name) LIKE '%siêu âm%'
        OR lower(name) LIKE '%chụp%' OR lower(name) LIKE '%đo mật độ xương%' OR lower(name) LIKE '%điện tim%'
        THEN 'IMAGING'
    ELSE 'EXAMINATION'
END;

ALTER TABLE clinic_services
    ADD CONSTRAINT ck_clinic_services_type
        CHECK (service_type IN ('EXAMINATION', 'IMAGING', 'LABORATORY'));

ALTER TABLE rooms
    ADD COLUMN room_type VARCHAR(16) NOT NULL DEFAULT 'EXAMINATION',
    ADD COLUMN description TEXT NOT NULL DEFAULT '';

UPDATE rooms
SET room_type = CASE
    WHEN room_code LIKE 'CĐHA%' OR room_code LIKE 'CDHA%' THEN 'IMAGING'
    WHEN room_code LIKE 'XN%' THEN 'LABORATORY'
    ELSE 'EXAMINATION'
END;

ALTER TABLE rooms
    ADD CONSTRAINT ck_rooms_type
        CHECK (room_type IN ('EXAMINATION', 'IMAGING', 'LABORATORY'));

CREATE TABLE room_supported_services (
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    service_id UUID NOT NULL REFERENCES clinic_services(id) ON DELETE RESTRICT,
    PRIMARY KEY (room_id, service_id)
);

CREATE INDEX ix_room_supported_services_service ON room_supported_services(service_id, room_id);
