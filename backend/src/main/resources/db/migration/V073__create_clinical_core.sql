CREATE TABLE clinical_examinations (
 id UUID PRIMARY KEY, visit_id UUID NOT NULL UNIQUE, reason_for_visit TEXT, symptoms TEXT,
 examination_findings TEXT, injury_body_site VARCHAR(120), injury_side VARCHAR(24), injury_mechanism TEXT,
 status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','COMPLETED')),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE clinical_examination_diagnoses (
 id UUID PRIMARY KEY, examination_id UUID NOT NULL REFERENCES clinical_examinations(id) ON DELETE CASCADE,
 code VARCHAR(8) NOT NULL, type VARCHAR(16) NOT NULL CHECK (type IN ('PRIMARY','SECONDARY')),
 CONSTRAINT ck_clinical_diagnosis_icd_groups CHECK (code ~* '^[MS][0-9]{2}(\.[A-Z0-9]{1,4})?$')
);
CREATE UNIQUE INDEX uk_clinical_primary_diagnosis ON clinical_examination_diagnoses(examination_id) WHERE type='PRIMARY';
CREATE TABLE clinical_ordering_rounds (
 id UUID PRIMARY KEY, visit_id UUID NOT NULL, patient_id UUID NOT NULL, round_number INTEGER NOT NULL CHECK(round_number>0),
 ordered_by_doctor_id UUID NOT NULL, ordered_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_clinical_round_visit_number UNIQUE(visit_id,round_number)
);
CREATE TABLE clinical_service_orders (
 id UUID PRIMARY KEY, order_code VARCHAR(32) NOT NULL UNIQUE, ordering_round_id UUID NOT NULL REFERENCES clinical_ordering_rounds(id) ON DELETE RESTRICT,
 visit_id UUID NOT NULL, patient_id UUID NOT NULL, service_id UUID NOT NULL, service_name_snapshot VARCHAR(255) NOT NULL,
 service_type VARCHAR(16) NOT NULL CHECK(service_type IN ('IMAGING','LABORATORY','OTHER')), requesting_doctor_id UUID NOT NULL,
 clinical_reason TEXT NOT NULL, body_area VARCHAR(255), instructions TEXT, price_snapshot NUMERIC(12,2) NOT NULL CHECK(price_snapshot>=0),
 order_status VARCHAR(24) NOT NULL CHECK(order_status IN ('ORDERED','RESULTS_AVAILABLE','CANCELLED')),
 payment_status VARCHAR(24) NOT NULL CHECK(payment_status IN ('PENDING_PAYMENT','PAID')), cancel_reason TEXT,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_clinical_rounds_visit_latest ON clinical_ordering_rounds(visit_id,round_number DESC);
CREATE INDEX ix_clinical_orders_round ON clinical_service_orders(ordering_round_id);
CREATE INDEX ix_clinical_orders_visit ON clinical_service_orders(visit_id);
