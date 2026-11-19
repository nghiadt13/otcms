-- V138: khôi phục bảng clinical_ordering_rounds (V118 đã gộp vào clinical_service_orders).
-- Mỗi đợt chỉ định là một dòng; clinical_service_orders chỉ còn ordering_round_id (FK).
-- Bỏ các cột round_head, round_number, ordered_at khỏi clinical_service_orders.
-- Dòng nguồn của bảng mới: dòng round_head = true của từng ordering_round_id.

-- 1. Preflight: mỗi đợt có đúng một dòng đầu; mọi dòng cùng đợt khớp visit/số đợt/giờ.
DO $$
DECLARE bad bigint;
BEGIN
    SELECT count(*) INTO bad FROM (
        SELECT ordering_round_id FROM clinical_service_orders
        GROUP BY ordering_round_id HAVING count(*) FILTER (WHERE round_head) <> 1) x;
    IF bad > 0 THEN RAISE EXCEPTION 'V138 preflight: % ordering rounds without exactly one head order', bad; END IF;

    SELECT count(*) INTO bad FROM (
        SELECT ordering_round_id FROM clinical_service_orders
        GROUP BY ordering_round_id
        HAVING count(DISTINCT visit_id) > 1 OR count(DISTINCT round_number) > 1 OR count(DISTINCT ordered_at) > 1) x;
    IF bad > 0 THEN RAISE EXCEPTION 'V138 preflight: % ordering rounds whose orders disagree on visit/round_number/ordered_at', bad; END IF;

    SELECT count(*) INTO bad FROM invoices i
        WHERE i.clinical_ordering_round_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM clinical_service_orders o WHERE o.ordering_round_id = i.clinical_ordering_round_id);
    IF bad > 0 THEN RAISE EXCEPTION 'V138 preflight: % invoices point at a missing ordering round', bad; END IF;
END $$;

-- 2. Bảng đợt chỉ định (id = ordering_round_id hiện có).
CREATE TABLE clinical_ordering_rounds (
    id                 uuid        PRIMARY KEY,
    visit_id           uuid        NOT NULL,
    round_number       integer     NOT NULL,
    ordered_by_doctor_id uuid      NOT NULL,
    ordered_at         timestamptz NOT NULL,
    created_at         timestamptz NOT NULL,
    CONSTRAINT ck_clinical_rounds_round_number CHECK (round_number > 0),
    CONSTRAINT uk_clinical_round_visit_number UNIQUE (visit_id, round_number)
);
CREATE INDEX ix_clinical_rounds_visit_latest ON clinical_ordering_rounds (visit_id, round_number DESC);

-- 3. Copy từ dòng đầu đợt.
INSERT INTO clinical_ordering_rounds (id, visit_id, round_number, ordered_by_doctor_id, ordered_at, created_at)
SELECT ordering_round_id, visit_id, round_number, requesting_doctor_id, ordered_at, created_at
FROM clinical_service_orders WHERE round_head;

-- 4. Kiểm tra số dòng.
DO $$
DECLARE expected bigint; actual bigint;
BEGIN
    SELECT count(DISTINCT ordering_round_id) INTO expected FROM clinical_service_orders;
    SELECT count(*) INTO actual FROM clinical_ordering_rounds;
    IF expected <> actual THEN RAISE EXCEPTION 'V138 copy check: % rounds expected, % copied', expected, actual; END IF;
END $$;

-- 5. FK mới; gỡ constraint/index cũ của round_head/round_number rồi drop cột.
ALTER TABLE clinical_service_orders
    ADD CONSTRAINT fk_clinical_orders_round FOREIGN KEY (ordering_round_id)
        REFERENCES clinical_ordering_rounds (id) ON DELETE RESTRICT;
ALTER TABLE invoices
    ADD CONSTRAINT invoices_clinical_ordering_round_id_fkey FOREIGN KEY (clinical_ordering_round_id)
        REFERENCES clinical_ordering_rounds (id) ON DELETE RESTRICT;

DROP INDEX uk_clinical_orders_round_head;
DROP INDEX uk_clinical_orders_round_number;
DROP INDEX ix_clinical_orders_round_head_visit;
ALTER TABLE clinical_service_orders DROP CONSTRAINT ck_clinical_orders_round_number;

ALTER TABLE clinical_service_orders
    DROP COLUMN round_head,
    DROP COLUMN round_number,
    DROP COLUMN ordered_at;
