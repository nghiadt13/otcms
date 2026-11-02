ALTER TABLE clinic_services
    ADD COLUMN price NUMERIC(12, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE clinic_services
    ADD CONSTRAINT ck_clinic_services_price_nonnegative CHECK (price >= 0);
