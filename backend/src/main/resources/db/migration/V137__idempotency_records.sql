-- V137: gom các cột idempotency key/fingerprint rải ở 6 bảng vào bảng dùng chung idempotency_records.
-- Mẫu theo V118: 1 preflight, 2 tạo bảng, 3 copy, 4 kiểm tra số dòng, 5 chuyển constraint, 6 drop cột cũ.
-- Không đổi hành vi API: phạm vi unique (owner_id) giữ đúng như unique cũ của từng thao tác.
-- Không giữ lại: payment_attempts.last_event_fingerprint (chống trùng webhook), invoices.source_fingerprint (chống trùng hoá đơn).
-- Lưu ý: DROP COLUMN không kích hoạt trigger UPDATE nên tr_diagnostic_reports_immutable không cần gỡ.

-- 1. Preflight: key và fingerprint phải đi cặp, nếu không thì không chuyển được.
DO $$
DECLARE bad bigint;
BEGIN
    SELECT count(*) INTO bad FROM appointments
        WHERE (checkin_idempotency_key IS NULL) <> (checkin_request_fingerprint IS NULL);
    IF bad > 0 THEN RAISE EXCEPTION 'V137 preflight: % appointments rows have unpaired check-in key/fingerprint', bad; END IF;

    SELECT count(*) INTO bad FROM visits
        WHERE (walk_in_idempotency_key IS NULL) <> (walk_in_request_fingerprint IS NULL);
    IF bad > 0 THEN RAISE EXCEPTION 'V137 preflight: % visits rows have unpaired walk-in key/fingerprint', bad; END IF;

    SELECT count(*) INTO bad FROM clinical_service_orders
        WHERE (submit_idempotency_key IS NULL) <> (submit_request_fingerprint IS NULL)
           OR (review_idempotency_key IS NULL) <> (review_fingerprint IS NULL)
           OR (cancel_idempotency_key IS NULL) <> (cancel_fingerprint IS NULL)
           OR (transfer_idempotency_key IS NULL) <> (transfer_fingerprint IS NULL);
    IF bad > 0 THEN RAISE EXCEPTION 'V137 preflight: % clinical_service_orders rows have unpaired key/fingerprint', bad; END IF;

    SELECT count(*) INTO bad FROM diagnostic_reports
        WHERE (draft_idempotency_key IS NULL) <> (draft_fingerprint IS NULL)
           OR (correction_idempotency_key IS NULL) <> (correction_fingerprint IS NULL)
           OR (upload_idempotency_key IS NULL) <> (upload_fingerprint IS NULL)
           OR (complete_idempotency_key IS NULL) <> (complete_fingerprint IS NULL);
    IF bad > 0 THEN RAISE EXCEPTION 'V137 preflight: % diagnostic_reports rows have unpaired key/fingerprint', bad; END IF;

    SELECT count(*) INTO bad FROM imaging_studies
        WHERE send_idempotency_key IS NOT NULL AND send_fingerprint IS NULL;
    IF bad > 0 THEN RAISE EXCEPTION 'V137 preflight: % imaging_studies rows have send key without fingerprint', bad; END IF;
END $$;

-- 2. Bảng dùng chung.
CREATE TABLE idempotency_records (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    scope               varchar(40)  NOT NULL,
    owner_id            uuid         NOT NULL,
    idempotency_key     varchar(255) NOT NULL,
    request_fingerprint varchar(64)  NOT NULL,
    resource_id         uuid         NOT NULL,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_records_scope_owner_key UNIQUE (scope, owner_id, idempotency_key),
    CONSTRAINT ck_idempotency_records_key CHECK (length(btrim(idempotency_key)) BETWEEN 1 AND 255),
    CONSTRAINT ck_idempotency_records_fingerprint CHECK (request_fingerprint ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_idempotency_records_scope CHECK (scope IN (
        'APPOINTMENT_CHECKIN', 'WALK_IN_VISIT', 'ORDER_ROUND_SUBMIT', 'ORDER_REVIEW', 'ORDER_CANCEL', 'ORDER_TRANSFER',
        'RESULT_DRAFT', 'RESULT_CORRECTION', 'RESULT_UPLOAD', 'RESULT_COMPLETE',
        'IMAGING_UPLOAD', 'IMAGING_SEND', 'PAYMENT_ATTEMPT'))
);
CREATE INDEX ix_idempotency_records_resource ON idempotency_records (resource_id);

-- 3. Copy theo từng scope. owner_id = đúng cột đầu của unique cũ (hoặc id dòng khi unique cũ không tồn tại).
INSERT INTO idempotency_records (scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
SELECT 'APPOINTMENT_CHECKIN', checkin_actor_account_id, checkin_idempotency_key, checkin_request_fingerprint, id,
       COALESCE(checkin_checked_in_at, created_at)
FROM appointments WHERE checkin_idempotency_key IS NOT NULL;

INSERT INTO idempotency_records (scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
SELECT 'WALK_IN_VISIT', created_by_account_id, walk_in_idempotency_key, walk_in_request_fingerprint, id, created_at
FROM visits WHERE walk_in_idempotency_key IS NOT NULL;

-- ORDER_ROUND_SUBMIT: resource_id = ordering_round_id (đợt chỉ định), key nằm trên dòng đầu đợt.
INSERT INTO idempotency_records (scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
SELECT 'ORDER_ROUND_SUBMIT', requesting_doctor_id, submit_idempotency_key, rtrim(submit_request_fingerprint)::varchar,
       ordering_round_id, created_at
FROM clinical_service_orders WHERE submit_idempotency_key IS NOT NULL;

-- ORDER_REVIEW/CANCEL/TRANSFER: không có unique cũ, mỗi dòng chỉ định giữ một key -> owner_id = resource_id = id chỉ định.
INSERT INTO idempotency_records (scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
SELECT 'ORDER_REVIEW', id, review_idempotency_key, rtrim(review_fingerprint)::varchar, id, updated_at
FROM clinical_service_orders WHERE review_idempotency_key IS NOT NULL;
INSERT INTO idempotency_records (scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
SELECT 'ORDER_CANCEL', id, cancel_idempotency_key, rtrim(cancel_fingerprint)::varchar, id, updated_at
FROM clinical_service_orders WHERE cancel_idempotency_key IS NOT NULL;
INSERT INTO idempotency_records (scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
SELECT 'ORDER_TRANSFER', id, transfer_idempotency_key, rtrim(transfer_fingerprint)::varchar, id, updated_at
FROM clinical_service_orders WHERE transfer_idempotency_key IS NOT NULL;

-- RESULT_*: owner_id = service_order_id (unique cũ là (service_order_id, key) cho từng loại), resource_id = id báo cáo.
INSERT INTO idempotency_records (scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
SELECT 'RESULT_DRAFT', service_order_id, draft_idempotency_key, rtrim(draft_fingerprint)::varchar, id, COALESCE(recorded_at, uploaded_at, now())
FROM diagnostic_reports WHERE draft_idempotency_key IS NOT NULL;
INSERT INTO idempotency_records (scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
SELECT 'RESULT_CORRECTION', service_order_id, correction_idempotency_key, rtrim(correction_fingerprint)::varchar, id, COALESCE(recorded_at, uploaded_at, now())
FROM diagnostic_reports WHERE correction_idempotency_key IS NOT NULL;
INSERT INTO idempotency_records (scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
SELECT 'RESULT_UPLOAD', service_order_id, upload_idempotency_key, rtrim(upload_fingerprint)::varchar, id, COALESCE(recorded_at, uploaded_at, now())
FROM diagnostic_reports WHERE upload_idempotency_key IS NOT NULL;
INSERT INTO idempotency_records (scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
SELECT 'RESULT_COMPLETE', service_order_id, complete_idempotency_key, rtrim(complete_fingerprint)::varchar, id, COALESCE(recorded_at, uploaded_at, now())
FROM diagnostic_reports WHERE complete_idempotency_key IS NOT NULL;

-- IMAGING_UPLOAD: unique cũ (actor, service_order, attempt, key) -> owner_id = actor,
-- idempotency_key = '<service_order_id>:<attempt_id>:<key>'. Fingerprint = payload_sha256 của lần upload (cột NOT NULL, giữ ở imaging_studies).
INSERT INTO idempotency_records (scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
SELECT 'IMAGING_UPLOAD', actor_account_id, service_order_id::text || ':' || attempt_id::text || ':' || upload_idempotency_key,
       payload_sha256, id, created_at
FROM imaging_studies;

-- IMAGING_SEND: unique cũ không có -> owner_id = resource_id = id imaging_studies.
INSERT INTO idempotency_records (scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
SELECT 'IMAGING_SEND', id, send_idempotency_key, rtrim(send_fingerprint)::varchar, id, updated_at
FROM imaging_studies WHERE send_idempotency_key IS NOT NULL;

INSERT INTO idempotency_records (scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
SELECT 'PAYMENT_ATTEMPT', created_by_account_id, idempotency_key, request_fingerprint, id, created_at
FROM payment_attempts;

-- 4. Kiểm tra số dòng nguồn = số dòng đích cho từng scope.
DO $$
DECLARE r record;
BEGIN
    FOR r IN
        SELECT s.scope, s.expected, COALESCE(d.actual, 0) AS actual FROM (
            SELECT 'APPOINTMENT_CHECKIN' AS scope, count(*) AS expected FROM appointments WHERE checkin_idempotency_key IS NOT NULL
            UNION ALL SELECT 'WALK_IN_VISIT', count(*) FROM visits WHERE walk_in_idempotency_key IS NOT NULL
            UNION ALL SELECT 'ORDER_ROUND_SUBMIT', count(*) FROM clinical_service_orders WHERE submit_idempotency_key IS NOT NULL
            UNION ALL SELECT 'ORDER_REVIEW', count(*) FROM clinical_service_orders WHERE review_idempotency_key IS NOT NULL
            UNION ALL SELECT 'ORDER_CANCEL', count(*) FROM clinical_service_orders WHERE cancel_idempotency_key IS NOT NULL
            UNION ALL SELECT 'ORDER_TRANSFER', count(*) FROM clinical_service_orders WHERE transfer_idempotency_key IS NOT NULL
            UNION ALL SELECT 'RESULT_DRAFT', count(*) FROM diagnostic_reports WHERE draft_idempotency_key IS NOT NULL
            UNION ALL SELECT 'RESULT_CORRECTION', count(*) FROM diagnostic_reports WHERE correction_idempotency_key IS NOT NULL
            UNION ALL SELECT 'RESULT_UPLOAD', count(*) FROM diagnostic_reports WHERE upload_idempotency_key IS NOT NULL
            UNION ALL SELECT 'RESULT_COMPLETE', count(*) FROM diagnostic_reports WHERE complete_idempotency_key IS NOT NULL
            UNION ALL SELECT 'IMAGING_UPLOAD', count(*) FROM imaging_studies
            UNION ALL SELECT 'IMAGING_SEND', count(*) FROM imaging_studies WHERE send_idempotency_key IS NOT NULL
            UNION ALL SELECT 'PAYMENT_ATTEMPT', count(*) FROM payment_attempts
        ) s
        LEFT JOIN (SELECT scope, count(*) AS actual FROM idempotency_records GROUP BY scope) d ON d.scope = s.scope
    LOOP
        IF r.expected <> r.actual THEN
            RAISE EXCEPTION 'V137 copy check: scope % expected % rows, copied %', r.scope, r.expected, r.actual;
        END IF;
    END LOOP;
END $$;

-- 5. Gỡ constraint/index cũ liên quan tới các cột sắp drop.
ALTER TABLE appointments
    DROP CONSTRAINT ck_appointments_checkin_all_or_none,
    DROP CONSTRAINT ck_appointments_checkin_fingerprint,
    DROP CONSTRAINT ck_appointments_checkin_key,
    DROP CONSTRAINT uk_appointments_checkin_actor_key;
-- Phần còn lại của ck_appointments_checkin_all_or_none: actor và checked_in_at đi cặp (V141 sẽ chuyển sang appointment_arrivals).
ALTER TABLE appointments ADD CONSTRAINT ck_appointments_checkin_all_or_none
    CHECK ((checkin_actor_account_id IS NULL) = (checkin_checked_in_at IS NULL));

ALTER TABLE visits
    DROP CONSTRAINT ck_clinic_visits_walk_in_fingerprint,
    DROP CONSTRAINT ck_clinic_visits_walk_in_key_all_or_none,
    DROP CONSTRAINT ck_clinic_visits_walk_in_key_length,
    DROP CONSTRAINT ck_clinic_visits_walk_in_key_source;
DROP INDEX uk_clinic_visits_walk_in_idempotency;

ALTER TABLE clinical_service_orders
    DROP CONSTRAINT ck_clinical_orders_round_head_keys,
    DROP CONSTRAINT ck_clinical_orders_submit_fingerprint;
DROP INDEX uk_clinical_orders_submit_key;

DROP INDEX uq_results_draft_key;
DROP INDEX uq_results_correction_key;
DROP INDEX uq_results_upload_key;
DROP INDEX uq_results_complete_key;

ALTER TABLE imaging_studies DROP CONSTRAINT ck_imaging_studies_sha256;
ALTER TABLE imaging_studies DROP CONSTRAINT uk_imaging_studies_upload_scope;
ALTER TABLE imaging_studies ADD CONSTRAINT ck_imaging_studies_sha256 CHECK (
    payload_sha256 ~ '^[0-9a-f]{64}$'
    AND (release_payload_sha256 IS NULL OR release_payload_sha256 ~ '^[0-9a-f]{64}$')
    AND (released_report_sha256 IS NULL OR released_report_sha256 ~ '^[0-9a-f]{64}$'));

ALTER TABLE payment_attempts
    DROP CONSTRAINT ck_payment_attempt_idempotency,
    DROP CONSTRAINT uq_payment_attempt_actor_key;

-- 6. Drop cột cũ.
ALTER TABLE appointments
    DROP COLUMN checkin_idempotency_key,
    DROP COLUMN checkin_request_fingerprint;
ALTER TABLE visits
    DROP COLUMN walk_in_idempotency_key,
    DROP COLUMN walk_in_request_fingerprint;
ALTER TABLE clinical_service_orders
    DROP COLUMN submit_idempotency_key,
    DROP COLUMN submit_request_fingerprint,
    DROP COLUMN review_idempotency_key,
    DROP COLUMN review_fingerprint,
    DROP COLUMN cancel_idempotency_key,
    DROP COLUMN cancel_fingerprint,
    DROP COLUMN transfer_idempotency_key,
    DROP COLUMN transfer_fingerprint;
ALTER TABLE diagnostic_reports
    DROP COLUMN draft_idempotency_key,
    DROP COLUMN draft_fingerprint,
    DROP COLUMN correction_idempotency_key,
    DROP COLUMN correction_fingerprint,
    DROP COLUMN upload_idempotency_key,
    DROP COLUMN upload_fingerprint,
    DROP COLUMN complete_idempotency_key,
    DROP COLUMN complete_fingerprint;
ALTER TABLE imaging_studies
    DROP COLUMN upload_idempotency_key,
    DROP COLUMN send_idempotency_key,
    DROP COLUMN send_fingerprint;
ALTER TABLE payment_attempts
    DROP COLUMN idempotency_key,
    DROP COLUMN request_fingerprint;
