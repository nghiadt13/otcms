-- Sample rows at schema V136 (FK triggers off) to exercise V139-V144 data copy.

-- visits: one walk-in with key
INSERT INTO visits (id, patient_profile_id, source, arrived_at, checked_in_at, created_by_account_id, walk_in_idempotency_key, walk_in_request_fingerprint)
VALUES ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000b1', 'WALK_IN', now(), now(), '00000000-0000-0000-0000-0000000000c1', 'walk-key-1', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');

-- appointments: A1 QR arrival, A2 checked-in with key, A3 plain
INSERT INTO appointments (id, patient_profile_id, service_id, doctor_profile_id, booking_source, purpose, start_time, end_time, duration_minutes, created_by_account_id, occupied_until,
    arrived_at, arrival_source, arrival_recorded_at, arrival_recorded_by)
VALUES ('00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000f1', 'PATIENT_ONLINE', 'p',
    '2026-11-20 09:00+07', '2026-11-20 09:30+07', 30, '00000000-0000-0000-0000-0000000000c1', '2026-11-20 09:30+07',
    '2026-11-20 08:55+07', 'PATIENT_QR', '2026-11-20 08:55+07', '00000000-0000-0000-0000-0000000000c1');
INSERT INTO appointments (id, patient_profile_id, service_id, doctor_profile_id, booking_source, purpose, start_time, end_time, duration_minutes, created_by_account_id, occupied_until,
    status, visit_id, checkin_idempotency_key, checkin_request_fingerprint, checkin_actor_account_id, checkin_checked_in_at)
VALUES ('00000000-0000-0000-0000-0000000000d2', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000f1', 'PATIENT_ONLINE', 'p',
    '2026-11-20 10:00+07', '2026-11-20 10:30+07', 30, '00000000-0000-0000-0000-0000000000c1', '2026-11-20 10:30+07',
    'CHECKED_IN', '00000000-0000-0000-0000-0000000000a1', 'checkin-key-1', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', '00000000-0000-0000-0000-0000000000c2', '2026-11-20 10:01+07');
INSERT INTO appointments (id, patient_profile_id, service_id, doctor_profile_id, booking_source, purpose, start_time, end_time, duration_minutes, created_by_account_id, occupied_until)
VALUES ('00000000-0000-0000-0000-0000000000d3', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000f1', 'PATIENT_ONLINE', 'p',
    '2026-11-20 11:00+07', '2026-11-20 11:30+07', 30, '00000000-0000-0000-0000-0000000000c1', '2026-11-20 11:30+07');

-- rounds R1 (O1 head imaging PENDING + O2 lab with room), R2 (O3 head imaging IN_PROGRESS + O4 imaging RESULT_AVAILABLE)
INSERT INTO clinical_service_orders (id, order_code, ordering_round_id, visit_id, patient_id, service_id, service_name_snapshot, service_type, requesting_doctor_id, clinical_reason,
    price_snapshot, order_status, payment_status, created_at, updated_at, round_number, ordered_at, round_head, submit_idempotency_key, submit_request_fingerprint,
    ordering_doctor_account_id, room_id, imaging_type, execution_status, execution_created_at, execution_updated_at, cancel_idempotency_key, cancel_fingerprint)
VALUES ('00000000-0000-0000-0000-000000000101', 'O1', '00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000e2',
    'XR', 'IMAGING', '00000000-0000-0000-0000-0000000000c3', 'r', 1, 'ORDERED', 'PAID', now(), now(), 1, '2026-11-20 09:05+07', true, 'submit-key-1', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    '00000000-0000-0000-0000-0000000000c3', NULL, 'XR', 'PENDING', now(), now(), 'cancel-key-1', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb');
INSERT INTO clinical_service_orders (id, order_code, ordering_round_id, visit_id, patient_id, service_id, service_name_snapshot, service_type, requesting_doctor_id, clinical_reason,
    price_snapshot, order_status, payment_status, created_at, updated_at, round_number, ordered_at, round_head, room_id, lab_status)
VALUES ('00000000-0000-0000-0000-000000000102', 'O2', '00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000e3',
    'LAB', 'LABORATORY', '00000000-0000-0000-0000-0000000000c3', 'r', 1, 'ORDERED', 'PAID', now(), now(), 1, '2026-11-20 09:05+07', false, '00000000-0000-0000-0000-0000000000a9', 'ASSIGNED');
INSERT INTO clinical_service_orders (id, order_code, ordering_round_id, visit_id, patient_id, service_id, service_name_snapshot, service_type, requesting_doctor_id, clinical_reason,
    price_snapshot, order_status, payment_status, created_at, updated_at, round_number, ordered_at, round_head,
    ordering_doctor_account_id, room_id, imaging_type, execution_status, execution_created_at, execution_updated_at, attempt_id, technician_account_id, first_use_type, first_use_event_id, first_use_at, first_use_source_id, execution_version)
VALUES ('00000000-0000-0000-0000-000000000103', 'O3', '00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000e2',
    'XR', 'IMAGING', '00000000-0000-0000-0000-0000000000c3', 'r', 1, 'ORDERED', 'PAID', now(), now(), 2, '2026-11-20 09:40+07', true,
    '00000000-0000-0000-0000-0000000000c3', '00000000-0000-0000-0000-0000000000a9', 'XR', 'IN_PROGRESS', now(), now(), '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000c4', 'ACQUISITION_STARTED', '00000000-0000-0000-0000-000000000401', now(), 'src-1', 3);
INSERT INTO clinical_service_orders (id, order_code, ordering_round_id, visit_id, patient_id, service_id, service_name_snapshot, service_type, requesting_doctor_id, clinical_reason,
    price_snapshot, order_status, payment_status, created_at, updated_at, round_number, ordered_at, round_head,
    ordering_doctor_account_id, room_id, imaging_type, execution_status, execution_created_at, execution_updated_at, attempt_id, study_ref, review_idempotency_key, review_fingerprint)
VALUES ('00000000-0000-0000-0000-000000000104', 'O4', '00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000e2',
    'XR2', 'IMAGING', '00000000-0000-0000-0000-0000000000c3', 'r', 1, 'ORDERED', 'PAID', now(), now(), 2, '2026-11-20 09:40+07', false,
    '00000000-0000-0000-0000-0000000000c3', '00000000-0000-0000-0000-0000000000a9', 'XR', 'RESULT_AVAILABLE', now(), now(), '00000000-0000-0000-0000-000000000302', 'STUDY-1', 'review-key-1', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');

-- diagnostic report with draft key, tied to O4 / STUDY-1
INSERT INTO diagnostic_reports (id, service_order_id, visit_id, version_number, study_ref, draft_idempotency_key, draft_fingerprint)
VALUES ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-0000000000a1', 1, 'STUDY-1', 'draft-key-1', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa');

-- imaging study with upload + send keys and confirmation data
INSERT INTO imaging_studies (id, service_order_id, attempt_id, visit_id, patient_id, actor_account_id, state, payload_sha256, upload_idempotency_key, send_idempotency_key, send_fingerprint,
    confirmation_event_id, confirmation_attempt_count, next_confirmation_attempt_at, last_safe_error_code)
VALUES ('00000000-0000-0000-0000-000000000601', '00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000b1',
    '00000000-0000-0000-0000-0000000000c4', 'CONFIRMATION_PENDING', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'upload-key-1', 'send-key-1', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
    '00000000-0000-0000-0000-000000000701', 2, now(), 'SOME_ERROR');
-- plain study (no release data)
INSERT INTO imaging_studies (id, service_order_id, attempt_id, visit_id, patient_id, actor_account_id, state, payload_sha256, upload_idempotency_key)
VALUES ('00000000-0000-0000-0000-000000000602', '00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000b1',
    '00000000-0000-0000-0000-0000000000c4', 'PROCESSING', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'upload-key-2');

-- payment attempts: PayOS with all link fields, counter without
INSERT INTO payment_attempts (id, invoice_id, channel, provider, amount, status, created_by_account_id, idempotency_key, request_fingerprint, provider_order_code,
    provider_payment_link_id, checkout_url, qr_code, transfer_bank_bin, transfer_account_number, transfer_account_name, transfer_description)
VALUES ('00000000-0000-0000-0000-000000000801', '00000000-0000-0000-0000-000000000901', 'ONLINE', 'PAYOS', 100, 'PENDING', '00000000-0000-0000-0000-0000000000c1', 'pay-key-1', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 123456,
    'plink-1', 'https://pay.example/checkout', 'QRDATA', '970422', '0123456789', 'CLINIC', 'DESC 1');
INSERT INTO payment_attempts (id, invoice_id, channel, counter_method, amount, status, created_by_account_id, idempotency_key, request_fingerprint, confirmed_at)
VALUES ('00000000-0000-0000-0000-000000000802', '00000000-0000-0000-0000-000000000901', 'COUNTER', 'CASH', 100, 'CONFIRMED', '00000000-0000-0000-0000-0000000000c1', 'pay-key-2', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', now());

