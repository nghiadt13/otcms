ALTER TABLE clinic_services
    ADD COLUMN modality VARCHAR(16),
    ADD COLUMN patient_note TEXT,
    ADD COLUMN lab_indicators TEXT NOT NULL DEFAULT '[]';

UPDATE clinic_services
SET modality = CASE
    WHEN lower(name) LIKE '%mri%' THEN 'MRI'
    WHEN lower(name) LIKE '%siêu âm%' THEN 'ULTRASOUND'
    WHEN lower(name) LIKE '%ct%' OR lower(name) LIKE '%chụp%' THEN 'CT'
    ELSE 'XRAY'
END
WHERE service_type = 'IMAGING';

ALTER TABLE clinic_services
    ADD CONSTRAINT ck_clinic_services_modality
        CHECK (
            (service_type = 'IMAGING' AND modality IN ('XRAY', 'ULTRASOUND', 'CT', 'MRI'))
            OR (service_type <> 'IMAGING' AND modality IS NULL)
        );
