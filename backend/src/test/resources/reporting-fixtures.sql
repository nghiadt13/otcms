DO $$
DECLARE admin_id uuid=gen_random_uuid(); doctor_account uuid=gen_random_uuid(); patient_account uuid=gen_random_uuid();
doctor_id uuid=gen_random_uuid(); patient_id uuid=gen_random_uuid(); service_id uuid=gen_random_uuid(); started timestamptz;
BEGIN
INSERT INTO accounts(id,normalized_email,account_type,status,created_at,updated_at)
VALUES(admin_id,admin_id||'@qa43.test','ADMIN','ACTIVE',now(),now()),
(doctor_account,doctor_account||'@qa43.test','DOCTOR','ACTIVE',now(),now()),
(patient_account,patient_account||'@qa43.test','PATIENT','ACTIVE',now(),now());
INSERT INTO patient_profiles(id,account_id,full_name,phone_canonical,phone_owner_type,date_of_birth,gender)
VALUES(patient_id,patient_account,'QA43 Patient','+84901234567','PATIENT','1990-01-01','OTHER');
INSERT INTO doctor_profiles(id,account_id,full_name,work_contact,specialties,qualifications,biography,consultation_information,publicly_visible,accepting_new_bookings,in_active_directory)
VALUES(doctor_id,doctor_account,'QA43 Doctor','','','','','',false,false,false);
INSERT INTO clinic_services(id,name,duration_minutes,buffer_minutes,online_bookable,active)
VALUES(service_id,'QA43 Service '||service_id,30,0,false,true);
INSERT INTO doctor_services(id,doctor_profile_id,service_id,active) VALUES(gen_random_uuid(),doctor_id,service_id,true);
FOREACH started IN ARRAY ARRAY['2030-01-01 16:00:00+00'::timestamptz,'2030-01-01 17:00:00+00'::timestamptz,'2030-01-02 17:00:00+00'::timestamptz]
LOOP
INSERT INTO appointments(id,patient_profile_id,service_id,doctor_profile_id,status,booking_source,purpose,start_time,end_time,duration_minutes,buffer_minutes,created_by_account_id)
VALUES(gen_random_uuid(),patient_id,service_id,doctor_id,'CONFIRMED','RECEPTIONIST_ASSISTED','QA43 date boundary',started,started+interval '30 minutes',30,0,admin_id);
END LOOP;
END $$;
