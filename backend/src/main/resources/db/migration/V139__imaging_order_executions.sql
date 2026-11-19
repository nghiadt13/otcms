-- V139: tách phần thực hiện chỉ định CĐHA khỏi clinical_service_orders sang imaging_order_executions (1 - 0..1).
-- Dòng nguồn: clinical_service_orders có execution_status IS NOT NULL. Chỉ định chưa có dòng thực hiện -> LEFT JOIN cho ra NULL như trước.
-- room_id Ở LẠI clinical_service_orders: phòng là thuộc tính chung của chỉ định (xét nghiệm cũng gán phòng ở đó).
-- Chuyển cả ordering_doctor_account_id (chỉ có nghĩa với chỉ định CĐHA).
-- Khác biệt có chủ ý: ck_clinical_orders_execution cũ kiểm tra service_type = 'IMAGING'; bảng mới không thấy service_type,
-- điều kiện đó được bảo đảm bởi preflight bên dưới và bởi code tạo dòng thực hiện (chỉ cho chỉ định IMAGING).

-- 1. Preflight.
DO $$
DECLARE bad bigint;
BEGIN
    SELECT count(*) INTO bad FROM clinical_service_orders
        WHERE execution_status IS NULL
          AND (imaging_type IS NOT NULL OR cancellation_cause IS NOT NULL
               OR cancellation_reason IS NOT NULL OR attempt_id IS NOT NULL OR technician_account_id IS NOT NULL
               OR first_use_type IS NOT NULL OR first_use_event_id IS NOT NULL OR first_use_at IS NOT NULL
               OR first_use_source_id IS NOT NULL OR study_ref IS NOT NULL OR ordering_doctor_account_id IS NOT NULL
               OR execution_created_at IS NOT NULL OR execution_updated_at IS NOT NULL);
    IF bad > 0 THEN RAISE EXCEPTION 'V139 preflight: % orders have execution columns without execution_status', bad; END IF;

    SELECT count(*) INTO bad FROM clinical_service_orders
        WHERE execution_status IS NOT NULL AND service_type <> 'IMAGING';
    IF bad > 0 THEN RAISE EXCEPTION 'V139 preflight: % non-imaging orders carry an execution', bad; END IF;
END $$;

-- 2. Bảng thực hiện.
CREATE TABLE imaging_order_executions (
    service_order_id           uuid         PRIMARY KEY REFERENCES clinical_service_orders (id) ON DELETE CASCADE,
    imaging_type               varchar(80)  NOT NULL,
    ordering_doctor_account_id uuid         NOT NULL,
    execution_status           varchar(32)  NOT NULL,
    cancellation_cause         varchar(64),
    cancellation_reason        text,
    attempt_id                 uuid,
    technician_account_id      uuid,
    first_use_type             varchar(32),
    first_use_event_id         uuid,
    first_use_at               timestamptz,
    first_use_source_id        varchar(128),
    study_ref                  text,
    created_at                 timestamptz  NOT NULL,
    updated_at                 timestamptz  NOT NULL,
    version                    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_ioe_status CHECK (execution_status IN ('PENDING', 'IN_PROGRESS', 'RESULT_AVAILABLE', 'REVIEWED', 'CANCELLED')),
    CONSTRAINT ck_ioe_attempt_state CHECK (
        (execution_status IN ('PENDING', 'CANCELLED') AND attempt_id IS NULL)
        OR (execution_status IN ('IN_PROGRESS', 'RESULT_AVAILABLE', 'REVIEWED') AND attempt_id IS NOT NULL)),
    CONSTRAINT ck_ioe_cancellation CHECK (
        CASE WHEN execution_status = 'CANCELLED'
             THEN cancellation_cause IN ('PATIENT_DECLINED', 'CLINIC_UNABLE') AND cancellation_reason IS NOT NULL
             ELSE cancellation_cause IS NULL AND cancellation_reason IS NULL END),
    CONSTRAINT ck_ioe_in_progress_no_study CHECK (execution_status IS DISTINCT FROM 'IN_PROGRESS' OR study_ref IS NULL),
    CONSTRAINT ck_ioe_result_has_study CHECK (execution_status NOT IN ('RESULT_AVAILABLE', 'REVIEWED') OR study_ref IS NOT NULL),
    CONSTRAINT ck_ioe_version CHECK (version >= 0)
);

-- 3. Copy.
INSERT INTO imaging_order_executions (service_order_id, imaging_type, ordering_doctor_account_id, execution_status,
    cancellation_cause, cancellation_reason, attempt_id, technician_account_id, first_use_type, first_use_event_id,
    first_use_at, first_use_source_id, study_ref, created_at, updated_at, version)
SELECT id, imaging_type, ordering_doctor_account_id, execution_status,
    cancellation_cause, cancellation_reason, attempt_id, technician_account_id, first_use_type, first_use_event_id,
    first_use_at, first_use_source_id, study_ref, execution_created_at, execution_updated_at, execution_version
FROM clinical_service_orders WHERE execution_status IS NOT NULL;

-- 4. Kiểm tra số dòng.
DO $$
DECLARE expected bigint; actual bigint;
BEGIN
    SELECT count(*) INTO expected FROM clinical_service_orders WHERE execution_status IS NOT NULL;
    SELECT count(*) INTO actual FROM imaging_order_executions;
    IF expected <> actual THEN RAISE EXCEPTION 'V139 copy check: % executions expected, % copied', expected, actual; END IF;
END $$;

-- 5. Unique/index chuyển sang bảng mới; FK của kết quả trỏ lại.
CREATE UNIQUE INDEX uk_ioe_attempt_id ON imaging_order_executions (attempt_id) WHERE attempt_id IS NOT NULL;
CREATE UNIQUE INDEX uk_ioe_first_use_event ON imaging_order_executions (first_use_event_id) WHERE first_use_event_id IS NOT NULL;
CREATE UNIQUE INDEX uk_ioe_study_ref ON imaging_order_executions (study_ref) WHERE study_ref IS NOT NULL;
ALTER TABLE imaging_order_executions ADD CONSTRAINT uk_ioe_id_study UNIQUE (service_order_id, study_ref);
CREATE INDEX ix_clinical_orders_room ON clinical_service_orders (room_id) WHERE room_id IS NOT NULL;
CREATE INDEX ix_ioe_status ON imaging_order_executions (execution_status);
CREATE INDEX ix_ioe_pending_created ON imaging_order_executions (created_at, service_order_id) WHERE execution_status = 'PENDING';

ALTER TABLE diagnostic_reports DROP CONSTRAINT fk_results_order_study;
ALTER TABLE diagnostic_reports ADD CONSTRAINT fk_results_order_study FOREIGN KEY (service_order_id, study_ref)
    REFERENCES imaging_order_executions (service_order_id, study_ref) ON DELETE RESTRICT;

DROP INDEX uk_clinical_orders_attempt_id;
DROP INDEX uk_clinical_orders_first_use_event;
DROP INDEX uk_clinical_orders_study_ref;
DROP INDEX ix_clinical_orders_room_work;
DROP INDEX ix_clinical_orders_pending_execution_created;
ALTER TABLE clinical_service_orders DROP CONSTRAINT uk_clinical_orders_id_study;
ALTER TABLE clinical_service_orders
    DROP CONSTRAINT ck_clinical_orders_attempt_state,
    DROP CONSTRAINT ck_clinical_orders_cancellation,
    DROP CONSTRAINT ck_clinical_orders_execution,
    DROP CONSTRAINT ck_clinical_orders_execution_timestamps,
    DROP CONSTRAINT ck_clinical_orders_execution_version,
    DROP CONSTRAINT ck_clinical_orders_in_progress_no_study,
    DROP CONSTRAINT ck_clinical_orders_result_has_study;

-- 6. Drop cột cũ.
ALTER TABLE clinical_service_orders
    DROP COLUMN imaging_type,
    DROP COLUMN ordering_doctor_account_id,
    DROP COLUMN execution_status,
    DROP COLUMN cancellation_cause,
    DROP COLUMN cancellation_reason,
    DROP COLUMN attempt_id,
    DROP COLUMN technician_account_id,
    DROP COLUMN first_use_type,
    DROP COLUMN first_use_event_id,
    DROP COLUMN first_use_at,
    DROP COLUMN first_use_source_id,
    DROP COLUMN study_ref,
    DROP COLUMN execution_created_at,
    DROP COLUMN execution_updated_at,
    DROP COLUMN execution_version;
