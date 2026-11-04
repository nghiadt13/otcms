-- Seed DEV: dịch vụ "Test dịch vụ" giá 10.000 đ để thử luồng đặt lịch + thanh toán PayOS.
-- Idempotent (chạy lại không tạo trùng). Sao chép bác sĩ phụ trách từ dịch vụ khám
-- "Khám Nội – Ngoại khoa với Thạc sĩ – Bác sĩ Nội trú" để slot đặt lịch hiện ra như dịch vụ thật.
-- Chạy trên DB local: psql -d otcms_dev -f scripts/seed-test-service-10k.sql
-- Xóa khi không dùng: DELETE FROM doctor_services WHERE service_id = (SELECT id FROM clinic_services WHERE name = 'Test dịch vụ');
--                      DELETE FROM clinic_services WHERE name = 'Test dịch vụ';  (lỗi nếu đã có lịch/hóa đơn dùng dịch vụ này)
WITH ref AS (
    SELECT id FROM clinic_services
    WHERE name = 'Khám Nội – Ngoại khoa với Thạc sĩ – Bác sĩ Nội trú'
    LIMIT 1
), svc AS (
    INSERT INTO clinic_services (name, description, duration_minutes, buffer_minutes, online_bookable,
                                 active, price, service_type)
    SELECT 'Test dịch vụ', 'Dịch vụ thử luồng đặt lịch và thanh toán (10.000 đ).', 30, 0, TRUE, TRUE, 10000,
           'EXAMINATION'
    WHERE EXISTS (SELECT 1 FROM ref)
      AND NOT EXISTS (SELECT 1 FROM clinic_services WHERE name = 'Test dịch vụ')
    RETURNING id
)
INSERT INTO doctor_services (doctor_profile_id, service_id, active)
SELECT ds.doctor_profile_id, svc.id, TRUE
FROM svc
JOIN ref ON TRUE
JOIN doctor_services ds ON ds.service_id = ref.id AND ds.active;

SELECT s.id, s.name, s.price, s.online_bookable, s.active,
       (SELECT count(*) FROM doctor_services d WHERE d.service_id = s.id AND d.active) AS doctors
FROM clinic_services s WHERE s.name = 'Test dịch vụ';
