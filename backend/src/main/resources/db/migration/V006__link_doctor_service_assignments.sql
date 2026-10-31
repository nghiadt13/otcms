ALTER TABLE doctor_service_assignments
    ADD CONSTRAINT fk_doctor_service_assignments_doctor_profile
    FOREIGN KEY (doctor_profile_id) REFERENCES doctor_profiles(id) ON DELETE RESTRICT;
