---
phase: 3
title: "Chỉ định, cận lâm sàng, ảnh, kết quả, bỏ outbox"
status: pending
priority: P1
effort: "8h"
owner: NghiaDT
dependencies: [1]
migration: V118__final_db_diagnostic_imaging_orders.sql
---

# Phase 03: Chỉ định, cận lâm sàng, ảnh, kết quả, bỏ outbox

## Overview
Gộp ba nhóm vào ba bảng đích và bỏ các bảng trung gian:
- Chỉ định: `clinical_ordering_rounds`, `clinical_imaging_request_replays`, `diagnostic_requests`, `diagnostic_attempts` vào `clinical_service_orders`.
- Ảnh: 8 bảng `imaging_*` vào `imaging_studies`.
- Kết quả: `diagnostic_findings_versions` vào `diagnostic_reports` (bảng này được sửa tại chỗ).

Bỏ outbox, idempotency, inbox và receipt. Nhật ký chuyển sang `AuditLogWriter` (phase 01). Khi ảnh được liên kết hoặc kết quả được phát hành, lượt khám (`visits`) được cập nhật trong cùng transaction.

- Người làm: NghiaDT. Migration: `V118__final_db_diagnostic_imaging_orders.sql` (chuyển từ `migration-staged/`).
- Phụ thuộc: phase 01 (`common/audit/AuditLogWriter`, bảng `audit_logs`, view `activity_log_entries` đã viết lại). Merge sau phase 05 vì cả hai sửa `ClinicVisitJpaEntity`.
- Quy mô: 18 bảng bị bỏ; 3 bảng sửa tại chỗ (`clinical_service_orders`, `diagnostic_reports`, `imaging_studies`); giữ nguyên `imaging_viewer_grants` (D3), `clinical_medical_document_payloads` (D5); `imaging_reconciliation_cases` giữ, chờ Q2.
- Ngoài phạm vi: `clinical_examinations`, `clinical_examination_diagnoses`, `clinical_prescriptions`, `clinical_prescription_items`, `clinical_medical_documents`. Đã kiểm trên V108: không có FK nào tới `doctor_profiles` ở nhóm này, nên phase 02 không phải sửa FK ở đây.
- Phase dài hơn 400 dòng. Mục 10 đề xuất tách 03a (mở rộng) và 03b (thu gọn).

Quy ước đường dẫn: `main:` = `backend/src/main/java/fu/fa26/sep490/g58/otcms_be/`, `test:` = `backend/src/test/java/fu/fa26/sep490/g58/otcms_be/`, `db:` = `backend/src/main/resources/db/`. Các file đã có đều đã kiểm trên `origin/product` @ 21e304cd. File đánh dấu "(mới)" chưa tồn tại.

## Requirements
1. API giữ nguyên: URL, method, body, mã HTTP, mã MSG. Endpoint chạm dữ liệu trong phase này:
   - Bác sĩ: `POST /api/v1/visits/{visitId}/imaging-requests`, `GET /api/v1/visits/{visitId}/imaging-orders`, `GET /api/v1/imaging-history`, `/api/v1/imaging-requests/{requestId}/**` (cancellations, room-transfers, reviews, findings, findings/corrections, reports, findings/completions).
   - Lễ tân: `GET /api/v1/reception/clinical-ordering-rounds`, `POST /api/v1/reception/clinical-ordering-rounds/{roundId}/imaging-dispatch`, `POST /api/v1/clinical-ordering-rounds/{roundId}/invoices`.
   - Kỹ thuật viên ảnh: `GET /api/v1/imaging-requests/assigned-upload-work`, `POST /api/v1/imaging/requests/{requestId}/attempts/{attemptId}/ingest-operations`, `GET /api/v1/imaging/ingest-operations/{operationId}`, `POST /api/v1/imaging/ingest-operations/{operationId}/send`.
   - Admin và viewer: `/api/v1/imaging/reconciliation-cases/**`, `/api/v1/imaging/studies/{studyRef}/viewer-grants`, `/dicom-web/**`.
2. Các ID đã lộ ra API giữ giá trị và tên trường:
   - `roundId` = `clinical_service_orders.ordering_round_id`. Không còn bảng đợt; dòng `round_head` là đầu đợt.
   - `requestId` = `clinical_service_orders.id`. Trước đây là id riêng của `diagnostic_requests`; từ nay là id phiếu chỉ định. Hình dạng JSON không đổi.
   - `attemptId` = `clinical_service_orders.attempt_id` (luôn là lần chụp số 1).
   - `operationId` = `imaging_studies.id` (chính là id thao tác upload cũ).
   - `reportId` = `diagnostic_reports.report_id`; `findingsVersionId` = `diagnostic_reports.id`.
   - `caseId` = `imaging_reconciliation_cases.id` (xem Q2).
3. Hành vi đổi có chủ đích, cần NghiaDT duyệt (Q6):
   - `POST .../findings/completions` trả `releaseState = "RELEASED"` thay cho `"RELEASE_PENDING"`. Outbox bị bỏ nên phát hành làm ngay trong transaction (D2). FE không so sánh chuỗi này (`git grep RELEASE_PENDING origin/product -- frontend/src`: không có kết quả). Ba test assert `RELEASE_PENDING` được sửa thành `RELEASED`.
   - `visits.imaging_result_ready_at` được set khi ảnh LINKED. Hiện trên product cột này không bao giờ được set, vì `DiagnosticEvidenceServiceImpl.dispatchDueOutbox` không có caller.
4. Chống gửi trùng (agent-rules 6.3): không còn bảng idempotency. Mỗi thao tác có cột khoá và fingerprint trên dòng nghiệp vụ, với UNIQUE đúng phạm vi cũ (mục 5.6). Không kiểm trùng bằng SELECT rồi INSERT. Bắt `DataIntegrityViolationException` ở bean không `@Transactional` (giống `ClinicalImagingRequestServiceImpl`) và kiểm tên constraint trước khi trả kết quả cũ.
5. Ràng buộc DB thay trigger (mục 5.7). Trigger còn cần thì viết lại trên bảng mới; hàm plpgsql cũ bị DROP, không để lại.
6. Dữ liệu chỉ là demo nhưng migration phải chạy được trên DB rỗng. Vẫn phải có câu `INSERT … SELECT` chép dữ liệu cũ (phase 00, bước 6.2).

## Architecture

### 5.1 Ánh xạ bảng cũ sang bảng đích (V108 → đích)

| Bảng cũ | Đích | Cách gộp |
|---|---|---|
| `clinical_ordering_rounds` | `clinical_service_orders` | `round_number`, `ordered_at`; dòng `round_head` mang thông tin đầu đợt |
| `clinical_imaging_request_replays` | `clinical_service_orders` | `submit_idempotency_key`, `submit_request_fingerprint` trên dòng `round_head` |
| `diagnostic_requests` | `clinical_service_orders` | cùng dòng (`requestId` = `id`), các cột `room_id`, `imaging_type`, `execution_status`, `cancellation_*`, `ordering_doctor_account_id` |
| `diagnostic_attempts` | `clinical_service_orders` | `attempt_id`, `first_use_*`, `technician_account_id`, `study_ref` |
| `diagnostic_findings_versions` | `diagnostic_reports` | một dòng cho mỗi phiên bản kết quả; `id` giữ nguyên |
| `diagnostic_reports` | `diagnostic_reports` | giữ bảng, sửa tại chỗ; báo cáo PDF gắn vào dòng cùng `version_number` |
| `imaging_ingest_operations` | `imaging_studies` | `id` = operation id; một dòng cho mỗi lần upload |
| `imaging_studies` | `imaging_studies` | giữ bảng; `id` thay `study_ref` làm PK; `study_ref` thành UNIQUE NULL |
| `imaging_study_series`, `imaging_study_instances` | `imaging_studies.dicom_index` (jsonb) | |
| `imaging_uid_claims` (loại STUDY) | `imaging_studies.study_instance_uid` UNIQUE | claim SERIES và SOP bỏ (D4) |
| `imaging_idempotency_records` | `imaging_studies.upload_idempotency_key`, `send_idempotency_key` | |
| `imaging_confirmation_outbox` | `imaging_studies` (`confirmation_*`, `next_confirmation_attempt_at`, `acknowledged_at`) | hàng đợi xác nhận là các dòng `CONFIRMATION_PENDING` |
| `imaging_release_inbox` | `imaging_studies` (`release_event_id`, `released_*`, `findings_released_at`) | |
| `imaging_reconciliation_cases` | giữ bảng (Q2), đổi FK sang `imaging_studies(id)` | |
| `visit_imaging_result_receipts` | `visits` (`imaging_result_ready_at`, `findings_released_at`) | |
| `diagnostic_outbox`, `diagnostic_idempotency_records` | bỏ | |
| `diagnostic_audit_events`, `diagnostic_history_access_audits`, `imaging_audit_events` | bỏ, ghi vào `audit_logs` | mục 5.9 |
| `invoices.clinical_ordering_round_id` | bỏ; liên kết qua `invoice_items.service_order_id` | mục 5.5 |

Giữ nguyên: `imaging_viewer_grants`, `clinical_medical_document_payloads`.

Ghi chú: schema-33 mục 2.8 để `study_ref` làm PK. Phase này đổi PK thành `id` vì API trả `operationId` trước khi có `study_ref` (ảnh còn ở PROCESSING). Đây là sai lệch cần NghiaDT chấp nhận (Q9). Schema-33 ghi trạng thái `status`; phase này giữ tên cột `state` của code.

### 5.2 `clinical_service_orders` (sửa tại chỗ)

Cột thêm (ADD nullable, copy dữ liệu, sau đó SET NOT NULL cho `round_number`, `ordered_at`):

```sql
round_number               integer,           -- từ clinical_ordering_rounds
ordered_at                 timestamptz,       -- từ clinical_ordering_rounds
round_head                 boolean NOT NULL DEFAULT false, -- đúng một dòng mỗi đợt
submit_idempotency_key     varchar(128),      -- chỉ có trên dòng round_head
submit_request_fingerprint char(64),          -- chỉ có trên dòng round_head
ordering_doctor_account_id uuid,              -- account bác sĩ (diagnostic_requests.ordering_doctor_id); requesting_doctor_id là profile
room_id                    uuid,              -- không FK (như V108)
imaging_type               varchar(80),
execution_status           varchar(32),       -- PENDING|IN_PROGRESS|RESULT_AVAILABLE|REVIEWED|CANCELLED; order_status giữ nguyên
cancellation_cause         varchar(64),       -- PATIENT_DECLINED|CLINIC_UNABLE
cancellation_reason        text,
attempt_id                 uuid,
technician_account_id      uuid,
first_use_type             varchar(32),       -- ACQUISITION_STARTED|CONTRAST_ADMINISTERED
first_use_event_id         uuid,
first_use_at               timestamptz,
first_use_source_id        varchar(128),
study_ref                  text,
review_idempotency_key     varchar(255), review_fingerprint char(64),
cancel_idempotency_key     varchar(255), cancel_fingerprint char(64),
transfer_idempotency_key   varchar(255), transfer_fingerprint char(64)  -- lần gần nhất (Q3)
```

Ràng buộc và chỉ mục (thay cho các CHECK/UNIQUE của bảng cũ và trigger `enforce_diagnostic_request_study_state`):

```sql
ALTER TABLE clinical_service_orders
  ADD CONSTRAINT ck_clinical_orders_round_number CHECK (round_number > 0),
  ADD CONSTRAINT ck_clinical_orders_round_head_keys CHECK (round_head OR (submit_idempotency_key IS NULL AND submit_request_fingerprint IS NULL)),
  ADD CONSTRAINT ck_clinical_orders_submit_fingerprint CHECK (submit_request_fingerprint IS NULL OR submit_request_fingerprint ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT ck_clinical_orders_execution CHECK (execution_status IS NULL OR (execution_status IN ('PENDING','IN_PROGRESS','RESULT_AVAILABLE','REVIEWED','CANCELLED') AND service_type = 'IMAGING' AND imaging_type IS NOT NULL AND ordering_doctor_account_id IS NOT NULL)),
  ADD CONSTRAINT ck_clinical_orders_assigned_before_use CHECK (execution_status IS NULL OR room_id IS NOT NULL OR execution_status IN ('PENDING','CANCELLED')),
  ADD CONSTRAINT ck_clinical_orders_attempt_state CHECK (execution_status IS NULL OR (execution_status IN ('PENDING','CANCELLED') AND attempt_id IS NULL) OR (execution_status IN ('IN_PROGRESS','RESULT_AVAILABLE','REVIEWED') AND attempt_id IS NOT NULL)),
  ADD CONSTRAINT ck_clinical_orders_in_progress_no_study CHECK (execution_status IS DISTINCT FROM 'IN_PROGRESS' OR study_ref IS NULL),
  ADD CONSTRAINT ck_clinical_orders_result_has_study CHECK (execution_status NOT IN ('RESULT_AVAILABLE','REVIEWED') OR study_ref IS NOT NULL),
  ADD CONSTRAINT ck_clinical_orders_cancellation CHECK (CASE WHEN execution_status = 'CANCELLED' THEN cancellation_cause IN ('PATIENT_DECLINED','CLINIC_UNABLE') AND cancellation_reason IS NOT NULL ELSE cancellation_cause IS NULL AND cancellation_reason IS NULL END),
  ADD CONSTRAINT uk_clinical_orders_id_visit UNIQUE (id, visit_id),
  ADD CONSTRAINT uk_clinical_orders_id_study UNIQUE (id, study_ref);

CREATE UNIQUE INDEX uk_clinical_orders_round_head ON clinical_service_orders (ordering_round_id) WHERE round_head;
CREATE UNIQUE INDEX uk_clinical_orders_round_number ON clinical_service_orders (visit_id, round_number) WHERE round_head;
CREATE UNIQUE INDEX uk_clinical_orders_submit_key ON clinical_service_orders (requesting_doctor_id, submit_idempotency_key) WHERE submit_idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uk_clinical_orders_study_ref ON clinical_service_orders (study_ref) WHERE study_ref IS NOT NULL;
CREATE UNIQUE INDEX uk_clinical_orders_attempt_id ON clinical_service_orders (attempt_id) WHERE attempt_id IS NOT NULL;
CREATE UNIQUE INDEX uk_clinical_orders_first_use_event ON clinical_service_orders (first_use_event_id) WHERE first_use_event_id IS NOT NULL;
CREATE INDEX ix_clinical_orders_round_head_visit ON clinical_service_orders (visit_id, round_number DESC) WHERE round_head;
CREATE INDEX ix_clinical_orders_room_work ON clinical_service_orders (room_id, execution_status) WHERE execution_status IS NOT NULL;
```

Ghi chú: `ordering_round_id` giữ NOT NULL và không FK; `ix_clinical_orders_round` giữ nguyên. Khoá `(visit_id, round_number)` chỉ áp trên dòng `round_head` nên mỗi số đợt chỉ xuất hiện một lần trong visit. Phần này vẫn dựa vào `CoordinationGuardService.acquire()` như hiện tại.

Bỏ: `clinical_service_orders_ordering_round_id_fkey` (DROP trước khi DROP bảng đợt), `uk_diagnostic_requests_active_service_order` (mỗi phiếu chỉ có một yêu cầu).

### 5.3 `diagnostic_reports` (sửa tại chỗ; một dòng = một phiên bản kết quả và/hoặc báo cáo PDF của phiên bản đó)

Đổi tên ở V118: `request_id` → `service_order_id`; `storage_ref` → `report_storage_ref`; `content_sha256` → `report_sha256`; `report_version` → `version_number`. Nới NOT NULL: `study_ref`, `findings_text`. Thêm cột:

```sql
result_type            varchar(16) NOT NULL DEFAULT 'IMAGING',  -- IMAGING|LAB
state                  varchar(16) NOT NULL DEFAULT 'DRAFT',    -- DRAFT|COMPLETED
findings_text          text,                -- NULL cho dòng chỉ có báo cáo
correction_reason      text,
lab_values             jsonb,               -- chưa map Java trong phase này (chưa có luồng xét nghiệm)
report_id              uuid UNIQUE,         -- id báo cáo cũ (API reportId)
validation_status      varchar(16),         -- PENDING|VALIDATED|INVALID; NULL khi chưa có báo cáo
validated_at           timestamptz,
occurred_at            timestamptz,
recorded_by_doctor_id  uuid,                -- account như cũ
completed_at           timestamptz,
released_at            timestamptz,
recorded_at            timestamptz NOT NULL DEFAULT clock_timestamp(),
draft_idempotency_key  varchar(255), draft_fingerprint char(64),
correction_idempotency_key varchar(255), correction_fingerprint char(64),
upload_idempotency_key varchar(255), upload_fingerprint char(64),
complete_idempotency_key varchar(255), complete_fingerprint char(64)
```

Ràng buộc:

```sql
ALTER TABLE diagnostic_reports
  ADD CONSTRAINT ck_results_state CHECK (state IN ('DRAFT','COMPLETED')),
  ADD CONSTRAINT ck_results_type CHECK (result_type IN ('IMAGING','LAB')),
  ADD CONSTRAINT ck_results_version CHECK (version_number > 0),
  ADD CONSTRAINT ck_results_findings_text CHECK (findings_text IS NULL OR length(btrim(findings_text)) > 0),
  ADD CONSTRAINT ck_results_imaging_study CHECK (result_type = 'LAB' OR study_ref IS NOT NULL),
  ADD CONSTRAINT ck_results_report_all_or_none CHECK ((report_id IS NULL) = (report_storage_ref IS NULL) AND (report_id IS NULL) = (report_sha256 IS NULL) AND (report_id IS NULL) = (validation_status IS NULL)),
  ADD CONSTRAINT ck_results_sha256 CHECK (report_sha256 IS NULL OR report_sha256 ~ '^[0-9a-f]{64}$'),
  ADD CONSTRAINT ck_results_validation CHECK (validation_status IS NULL OR (validation_status IN ('PENDING','VALIDATED','INVALID') AND ((validation_status = 'PENDING') = (validated_at IS NULL)))),
  ADD CONSTRAINT ck_results_completed CHECK (state <> 'COMPLETED' OR (findings_text IS NOT NULL AND completed_at IS NOT NULL AND report_id IS NOT NULL AND validation_status = 'VALIDATED')),
  ADD CONSTRAINT ck_results_released CHECK (released_at IS NULL OR (state = 'COMPLETED' AND validation_status = 'VALIDATED')),
  ADD CONSTRAINT uq_results_version UNIQUE (service_order_id, version_number),
  ADD CONSTRAINT fk_results_order_visit FOREIGN KEY (service_order_id, visit_id) REFERENCES clinical_service_orders (id, visit_id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_results_order_study FOREIGN KEY (service_order_id, study_ref) REFERENCES clinical_service_orders (id, study_ref) ON DELETE RESTRICT;
-- FK kiểu MATCH SIMPLE: dòng LAB (study_ref NULL) không bị kiểm tra FK study.
CREATE UNIQUE INDEX uq_results_draft_key ON diagnostic_reports (service_order_id, draft_idempotency_key) WHERE draft_idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uq_results_correction_key ON diagnostic_reports (service_order_id, correction_idempotency_key) WHERE correction_idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uq_results_upload_key ON diagnostic_reports (service_order_id, upload_idempotency_key) WHERE upload_idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uq_results_complete_key ON diagnostic_reports (service_order_id, complete_idempotency_key) WHERE complete_idempotency_key IS NOT NULL;
CREATE INDEX ix_results_visit ON diagnostic_reports (visit_id);   -- FK rule
```

Ghi chú: `ix_diagnostic_reports_request_uploaded` và `ix_diagnostic_findings_request_version` (V108) được thay bằng `uq_results_version`. Phiên bản COMPLETED là bất biến (mục 5.7). `required_pdf_report_id` không còn cần: báo cáo nằm cùng dòng.

### 5.4 `imaging_studies` (sửa tại chỗ; PK đổi thành `id`)

Cột sau khi xong (giữ tên cũ khi có thể):

```sql
id                           uuid PRIMARY KEY,     -- = operationId cũ
service_order_id             uuid NOT NULL,        -- đổi từ request_id; FK clinical_service_orders(id) RESTRICT
attempt_id                   uuid NOT NULL,
visit_id                     uuid NOT NULL,        -- FK visits(id) RESTRICT
patient_id                   uuid NOT NULL,
actor_account_id             uuid NOT NULL,        -- kỹ thuật viên upload
study_ref                    varchar(128) UNIQUE,  -- img_<hex>, gán khi lưu Orthanc (NULL trước đó)
state                        varchar(32) NOT NULL, -- PROCESSING|ARCHIVED_NOT_SENT|CONFIRMATION_PENDING|LINKED|QUARANTINED|FAILED|RECONCILIATION_REQUIRED
payload_sha256               char(64) NOT NULL,
payload_size_bytes           bigint NOT NULL DEFAULT 0,
upload_idempotency_key       varchar(128) NOT NULL,
study_instance_uid           varchar(64) UNIQUE,
modality                     varchar(16),
series_count                 integer NOT NULL DEFAULT 0,
instance_count               integer NOT NULL DEFAULT 0,
dicom_index                  jsonb,                -- {"series":[{"uid","orthanc_ref","instances":[{"sop_uid","orthanc_ref","frames"}]}]}
archive_study_ref            text UNIQUE,
verification_status          varchar(24) NOT NULL DEFAULT 'NOT_EVALUATED',
verification_evidence        jsonb NOT NULL DEFAULT '[]',  -- Java đang ghi danh sách mã
safe_outcome_code            varchar(64),
send_idempotency_key         varchar(128),
send_fingerprint             char(64),
confirmation_event_id        uuid UNIQUE,          -- eventId của SEND (thay event_id của outbox)
confirmation_attempt_count   integer NOT NULL DEFAULT 0,
next_confirmation_attempt_at timestamptz,
last_safe_error_code         varchar(64),
acknowledged_at              timestamptz,
release_event_id             uuid UNIQUE,          -- thay imaging_release_inbox.event_id
release_payload_sha256       char(64),
released_findings_id         uuid,                 -- diagnostic_reports.id
released_report_id           uuid,
released_report_sha256       char(64),
findings_released_at         timestamptz,
version                      bigint NOT NULL DEFAULT 0,
created_at                   timestamptz NOT NULL DEFAULT clock_timestamp(),
updated_at                   timestamptz NOT NULL DEFAULT clock_timestamp()
```

Ràng buộc và chỉ mục:
- UNIQUE `uk_imaging_studies_upload_scope (actor_account_id, service_order_id, attempt_id, upload_idempotency_key)` — đúng phạm vi cũ của `imaging_idempotency_records`.
- CHECK: `state` trong 7 giá trị trên (kể cả `ARCHIVED_NOT_SENT`, mục Q8); `verification_status` trong (`NOT_EVALUATED`, `CONFIRMED_MATCH`, `MISMATCH`, `UNKNOWN`); các hash đúng định dạng hex; `series_count`, `instance_count`, `payload_size_bytes` không âm; `confirmation_attempt_count` từ 0 đến 8; `safe_outcome_code` đúng regex `^[A-Z][A-Z0-9_]{0,63}$`.
- CHECK: `state = 'LINKED'` kéo theo `study_ref`, `archive_study_ref`, `study_instance_uid`, `acknowledged_at` đều NOT NULL. `findings_released_at` NOT NULL chỉ khi `state = 'LINKED'`.
- Chỉ mục: GIN `ix_imaging_studies_dicom_index` trên `dicom_index jsonb_path_ops`; `ix_imaging_studies_service_order (service_order_id)`; `ix_imaging_studies_visit (visit_id)`; `ix_imaging_studies_owner (service_order_id, attempt_id, created_at DESC)` giữ như cũ; partial `ix_imaging_studies_confirm_due (next_confirmation_attempt_at) WHERE state = 'CONFIRMATION_PENDING'`; partial `ix_imaging_studies_reconciliation (updated_at, id) WHERE state = 'RECONCILIATION_REQUIRED'`.
- FK đến: `imaging_viewer_grants.study_ref` → `imaging_studies(study_ref)` (cần UNIQUE, đã có); `imaging_reconciliation_cases.operation_id` → `imaging_studies(id)`.

### 5.5 `invoices`, `invoice_items`, `visits`

- `invoice_items`: thêm `service_order_id uuid`; FK `invoice_items_service_order_id_fkey` → `clinical_service_orders(id)` ON DELETE RESTRICT; `CREATE UNIQUE INDEX uk_invoice_items_service_order ON invoice_items (service_order_id) WHERE service_order_id IS NOT NULL` (vừa là UNIQUE vừa là index cho FK). Mỗi phiếu chỉ vào một hoá đơn, giống "một hoá đơn cho một đợt" trước đây.
- `invoices`: DROP FK `invoices_clinical_ordering_round_id_fkey`; DROP CONSTRAINT `ck_invoices_source` rồi ADD lại: VISIT và APPOINTMENT như cũ; CLINICAL_ROUND yêu cầu `visit_id IS NULL AND appointment_id IS NULL`. Sau đó DROP COLUMN `clinical_ordering_round_id` (kéo theo `uk_invoices_clinical_round_source`). CLINICAL_ROUND giữ `visit_id` NULL để `InvoiceRepository.findFirstByVisitId` không trả nhầm hoá đơn đợt.
- `visits`: thêm `imaging_result_ready_at timestamptz`, `findings_released_at timestamptz`. Không FK, không CHECK.
- Phần billing trong phase này chỉ gồm các thay đổi trên. Phase 08 (Hùng) phải biết trước khi sửa `InvoiceJpaEntity` và `InvoiceServiceImpl`.

### 5.6 Chống gửi trùng: cột khoá và phạm vi UNIQUE

| Thao tác | Dòng lưu khoá | Cột khoá / fingerprint | Phạm vi UNIQUE | Replay trả về |
|---|---|---|---|---|
| Gửi chỉ định theo đợt (`POST /visits/{id}/imaging-requests`) | dòng `round_head` của đợt | `submit_idempotency_key`, `submit_request_fingerprint` | `(requesting_doctor_id, submit_idempotency_key)` | cả đợt, `replayed = true` như cũ |
| Dispatch cận lâm sàng (`imaging-dispatch`) | dòng phiếu | `room_id`, `execution_status` (điều kiện `execution_status IS NULL`) | theo dòng | trạng thái hiện tại, cùng phòng thì coi là replay |
| Hủy (`cancellations`) | dòng phiếu | `cancel_idempotency_key`, `cancel_fingerprint` | theo dòng | view hiện tại |
| Đổi phòng (`room-transfers`) | dòng phiếu | `transfer_idempotency_key`, `transfer_fingerprint` (lần gần nhất) | theo dòng | view hiện tại. Khác cũ: chỉ nhớ khoá gần nhất (Q3) |
| Duyệt (`reviews`) | dòng phiếu | `review_idempotency_key`, `review_fingerprint` | theo dòng | view hiện tại |
| Lưu nháp (`findings`) | dòng kết quả | `draft_idempotency_key`, `draft_fingerprint` | `(service_order_id, draft_idempotency_key)` | phiên bản hiện tại |
| Sửa kết quả (`findings/corrections`) | dòng kết quả mới | `correction_idempotency_key`, `correction_fingerprint` | `(service_order_id, correction_idempotency_key)` | phiên bản mới |
| Tải báo cáo PDF (`reports`) | dòng kết quả của phiên bản | `upload_idempotency_key`, `upload_fingerprint` | `(service_order_id, upload_idempotency_key)` | `reportId` đã có |
| Phát hành (`findings/completions`) | dòng kết quả | `complete_idempotency_key`, `complete_fingerprint` | `(service_order_id, complete_idempotency_key)` | view hiện tại, `RELEASED` |
| Upload ảnh (`ingest-operations`) | dòng `imaging_studies` | `upload_idempotency_key`, `payload_sha256` | `uk_imaging_studies_upload_scope` | thao tác đã có |
| SEND ảnh (`.../send`) | dòng `imaging_studies` | `send_idempotency_key`, `send_fingerprint` | theo dòng (một SEND cho mỗi ảnh) | trạng thái hiện tại |

Quy tắc: thao tác "một lần" trên dòng (review, cancel) dùng UPDATE có điều kiện (`WHERE <khoá> IS NULL`) để đảm bảo nguyên tử, thay vì SELECT rồi INSERT.

### 5.7 Trigger và CHECK: thay thế các quy tắc của V108

| Đối tượng V108 | Hành vi cũ | Thay bằng |
|---|---|---|
| `enforce_diagnostic_findings_scope` (trigger trên `diagnostic_findings_versions`) | visit và study khớp request và attempt hiện tại; COMPLETED cần báo cáo VALIDATED | FK kép `fk_results_order_visit`, `fk_results_order_study`; CHECK `ck_results_completed` |
| `enforce_diagnostic_report_scope` | visit và study khớp | cùng FK kép |
| `enforce_diagnostic_request_study_state` | `status` khớp với attempt và study | CHECK `ck_clinical_orders_attempt_state`, `ck_clinical_orders_in_progress_no_study`, `ck_clinical_orders_result_has_study` |
| `ck_diagnostic_request_assigned_before_use`, `ck_diagnostic_requests_attempt_state`, `ck_diagnostic_requests_cancellation*` | CHECK | CHECK bản sao trên `clinical_service_orders` |
| `prevent_completed_diagnostic_findings_mutation` (BEFORE UPDATE/DELETE) | COMPLETED bất biến | trigger `prevent_result_mutation` trên `diagnostic_reports` (xem dưới) |
| `prevent_used_diagnostic_report_mutation` | báo cáo đã phát hành bất biến | gộp vào `prevent_result_mutation` |
| `touch_diagnostic_request_updated_at` | `updated_at = clock_timestamp()` | `touch_service_order_updated_at` trên `clinical_service_orders` |
| `reject_visit_imaging_result_receipt_mutation` | receipt bất biến | bỏ cùng bảng (DROP FUNCTION) |
| `uq_diagnostic_attempts_study_ref`, `uq_diagnostic_attempts_one_study_per_request` | study_ref duy nhất | `uk_clinical_orders_study_ref` |
| `uq_diagnostic_outbox_first_use_billing_per_request` | một đối tượng billing mỗi yêu cầu | bỏ (outbox) |
| Audit cũ (`diagnostic_audit_events`, `diagnostic_history_access_audits`, `imaging_audit_events`) | V108 không có trigger chặn UPDATE/DELETE | `audit_logs` có trigger chặn (phase 01); bảng cũ bị bỏ |

Trigger mới (phase này viết trong V118, sau khi các bảng đã đổi):

```sql
CREATE FUNCTION prevent_result_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.state = 'COMPLETED' OR OLD.released_at IS NOT NULL THEN
      RAISE EXCEPTION 'released or completed results are immutable';
    END IF;
    RETURN OLD;
  END IF;
  IF OLD.released_at IS NOT NULL THEN
    RAISE EXCEPTION 'released or completed results are immutable';
  END IF;
  -- Lần phát hành duy nhất: DRAFT -> COMPLETED kèm released_at trong cùng một UPDATE.
  IF OLD.state = 'COMPLETED' AND (to_jsonb(OLD) - 'released_at') IS DISTINCT FROM (to_jsonb(NEW) - 'released_at') THEN
    RAISE EXCEPTION 'completed diagnostic findings versions are immutable';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tr_diagnostic_reports_immutable BEFORE UPDATE OR DELETE ON diagnostic_reports
  FOR EACH ROW EXECUTE FUNCTION prevent_result_mutation();

CREATE FUNCTION touch_service_order_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = clock_timestamp(); RETURN NEW; END $$;
CREATE TRIGGER tr_clinical_orders_updated_at BEFORE UPDATE ON clinical_service_orders
  FOR EACH ROW EXECUTE FUNCTION touch_service_order_updated_at();
```

### 5.8 Luồng bàn giao D2 (cùng transaction, không outbox)

1. Lễ tân dispatch (`imaging-dispatch`): UPDATE dòng phiếu sang `execution_status = 'PENDING'` kèm `room_id`, `imaging_type`, `ordering_doctor_account_id` (mục 5.11, snippet I).
2. Upload: `imaging_studies` đi PROCESSING, rồi ARCHIVED_NOT_SENT (đã có `study_ref`, `dicom_index`, `study_instance_uid`).
3. SEND: ghi `confirmation_event_id`, `next_confirmation_attempt_at = now`, `send_idempotency_key`, `send_fingerprint`; state sang `CONFIRMATION_PENDING`; commit.
4. `ImagingConfirmationDispatcher` (mỗi 5 giây, như cũ) lấy tối đa 50 dòng `CONFIRMATION_PENDING` đến hạn. Với mỗi dòng, trong MỘT transaction:
   - gọi `DiagnosticEvidenceService.linkConfirmedStudy` (kiểm tra và ghi trực tiếp, không enqueue);
   - gọi `ClinicalImagingOrderPort.markResultAvailable` (order_status = RESULTS_AVAILABLE);
   - gọi `VisitDiagnosticPort.publishResultReady` (`visits.imaging_result_ready_at`);
   - set `imaging_studies.state = 'LINKED'`, `acknowledged_at`;
   - ghi nhật ký.
   Ghi nhận thử lại hoặc đối soát (`confirmation_attempt_count`, `last_safe_error_code`, state RECONCILIATION_REQUIRED) nằm ở transaction riêng `REQUIRES_NEW`, vì transaction link đã rollback khi lỗi (như code hiện tại).
   Replay: nếu dòng đã LINKED với đúng `study_ref` thì trả view hiện tại; khác `study_ref` thì `EVIDENCE_MISMATCH` như cũ.
5. Bác sĩ duyệt (`reviews`): `execution_status = 'REVIEWED'`.
6. Bác sĩ phát hành (`findings/completions`), trong MỘT transaction, theo đúng thứ tự:
   a. ghi dòng nguồn trước: `diagnostic_reports` (state COMPLETED, completed_at, released_at, report_id đã VALIDATED), `complete_idempotency_key`;
   b. gọi `VisitDiagnosticPort.publishFindingsReleased` (`visits.findings_released_at`);
   c. gọi `ImagingStudyPort.acceptFindingsRelease` (`imaging_studies.release_event_id`, `released_*`, `findings_released_at`).
   Phải ghi (a) trước (b), (c) vì `requireFindingsRelease` đọc lại dòng nguồn để kiểm tra.
   Trả về `releaseState = "RELEASED"`.
7. Kết quả cho bên đọc: phiếu có `order_status = RESULTS_AVAILABLE`. `WalkInVisitLifecycleServiceImpl` (visit) gọi `ClinicalVisitCompletionEvidenceService.getCompletionEvidence` và `DiagnosticVisitCompletionEvidenceService.getCompletionEvidence`; bằng chứng diagnostic thấy phiên bản COMPLETED.

Lưu ý `@Transactional(noRollbackFor = ImagingOperationException.class)` ở `ImagingStudyContractAdapter.acceptFindingsRelease` và `ImagingSendService.send`: khi gọi trong transaction chung, lỗi nghiệp vụ không được làm commit nửa vời. Các đường gọi nội bộ (b), (c) và link phải bỏ `noRollbackFor` hoặc kiểm tra điều kiện trước khi ghi (mục 9).

### 5.9 Nhật ký (audit) qua `AuditLogWriter`

Ánh xạ theo phase 01 (mục "Bảng ánh xạ"):

| `source` (giữ đúng chuỗi, view dùng để đặt tiền tố id) | category | action | record_type | entity_id | visible_in_activity_log |
|---|---|---|---|---|---|
| `diagnostic_audit_events` | DIAGNOSTIC | `action || ':' || outcome` | DIAGNOSTIC_REQUEST | `request_id` (không có thì `visit_id`) | true |
| `diagnostic_history_access_audits` | DIAGNOSTIC | VIEW_HISTORY | `scope_type` | `scope_id` | true |
| `imaging_audit_events` | IMAGING | action cũ | IMAGING_STUDY | NULL (`study_ref` vào metadata) | false |

- Ghi trong cùng transaction nghiệp vụ. `AuditLogWriter` dùng `MANDATORY`, nên mọi call site phải nằm trong `@Transactional`. `ImagingUploadOrchestrator` không transactional; nó gọi qua `ImagingAuditPersistenceService` (đã `@Transactional`), giữ nguyên.
- `ImagingAuditPersistenceService.SAFE_METADATA_KEYS` được giữ: chỉ ghi các khoá an toàn vào `metadata`.
- `correlation_id` lấy từ MDC. Các call imaging truyền `correlationId` tường minh (eventId, operationId): đưa vào `metadata.correlationRef`. Cần phase 01 cho phép truyền tường minh (phụ thuộc, ghi trong mục 9).
- Trường `diagnostic_history_access_audits.scope_type` là enum `DiagnosticHistoryScope`; ghi `name()`.
- Bảng cũ không còn: sự kiện "đã LINK" trước đây tra theo `event_id` UNIQUE trong `diagnostic_audit_events`; nay thay bằng kiểm state (mục 5.8, bước 4).

### 5.10 V118: thứ tự bước trong file migration

Không dùng `IF EXISTS`. Mọi lệnh DROP phải chạy được trên DB đã có V001–V117 (chỉ merge 01–03 hoặc các phase khác đã merge).

1. Kiểm trước (chạy trên DB Docker sau V117, ghi kết quả vào PR; không nằm trong file migration):
   - `SELECT proname FROM pg_proc WHERE prosrc ~* '(diagnostic_outbox|diagnostic_idempotency_records|diagnostic_attempts|diagnostic_findings_versions|diagnostic_requests|diagnostic_audit_events|diagnostic_history_access_audits|clinical_ordering_rounds|clinical_imaging_request_replays|imaging_ingest_operations|imaging_study_series|imaging_study_instances|imaging_uid_claims|imaging_idempotency_records|imaging_confirmation_outbox|imaging_release_inbox|imaging_audit_events|visit_imaging_result_receipts)';`
   - `SELECT viewname FROM pg_views WHERE definition ~* '<cùng danh sách>';`
   - Kết quả đúng: chỉ các hàm ở bước 10 (sẽ DROP). View không được phép xuất hiện.
2. `clinical_service_orders`: DROP FK `clinical_service_orders_ordering_round_id_fkey`. ADD các cột mục 5.2 (nullable). Copy: `round_number`, `ordered_at` từ `clinical_ordering_rounds`; `round_head` = `ROW_NUMBER() OVER (PARTITION BY ordering_round_id ORDER BY created_at, id) = 1`; khoá từ `clinical_imaging_request_replays` gắn vào dòng head; cột diagnostic từ `diagnostic_requests` (theo `id` = `service_order_id`) và `diagnostic_attempts` (theo `current_attempt_id`). Sau đó SET NOT NULL cho `round_number`, `ordered_at`; ADD CONSTRAINT/INDEX mục 5.2.
3. `diagnostic_reports` tại chỗ: đổi tên cột (mục 5.3), nới NOT NULL, ADD cột, copy dữ liệu từ `diagnostic_findings_versions` và `diagnostic_reports` cũ (ghép theo `request_id` và `version_number`; dòng báo cáo không có kết quả thì INSERT với state DRAFT). DB rỗng: không có dòng để copy nhưng câu lệnh vẫn phải chạy không lỗi. ADD CONSTRAINT, UNIQUE, FK kép, index mục 5.3.
4. Bỏ trigger và hàm của bảng cũ (mục 5.7, mục 10).
5. DROP TABLE theo thứ tự phụ thuộc: `diagnostic_outbox`, `diagnostic_idempotency_records`, `diagnostic_audit_events`, `diagnostic_history_access_audits`, `diagnostic_findings_versions`, `diagnostic_attempts`, `diagnostic_requests`, `clinical_imaging_request_replays`, `clinical_ordering_rounds`.
6. `imaging_studies` đổi PK:
   a. ADD `id uuid`; UPDATE `id = ingest_operation_id`;
   b. DROP FK `imaging_viewer_grants_study_ref_fkey`, `imaging_study_series_study_ref_fkey`, `imaging_study_instances_series_id_fkey` (nếu còn), `imaging_uid_claims_study_ref_fkey`, `imaging_confirmation_outbox_study_ref_fkey`, `imaging_audit_events_study_ref_fkey`;
   c. DROP CONSTRAINT `imaging_studies_pkey`; SET `id` NOT NULL; ADD PRIMARY KEY (`id`); `study_ref` DROP NOT NULL; ADD UNIQUE (`study_ref`); ADD lại FK `imaging_viewer_grants_study_ref_fkey`;
   d. ADD các cột ingest và cột mục 5.4; UPDATE từ `imaging_ingest_operations` theo `id`; INSERT các operation chưa có dòng study (`WHERE NOT EXISTS`);
   e. copy `confirmation_*` và `release_*` từ outbox và inbox (theo `operation_id` / `event_id`);
   f. `dicom_index` = jsonb tổng hợp từ `imaging_study_series` và `imaging_study_instances`;
   g. DROP tables: `imaging_study_instances`, `imaging_study_series`, `imaging_uid_claims`, `imaging_idempotency_records`, `imaging_confirmation_outbox`, `imaging_release_inbox`, `imaging_audit_events`;
   h. `imaging_reconciliation_cases`: DROP FK `imaging_reconciliation_cases_operation_id_fkey`, ADD FK tới `imaging_studies(id)`;
   i. DROP TABLE `imaging_ingest_operations`; DROP cột `imaging_studies.ingest_operation_id`; RENAME `request_id` → `service_order_id`; tạo index mục 5.4; GIN.
7. `visits`: ADD `imaging_result_ready_at`, `findings_released_at`. DROP TABLE `visit_imaging_result_receipts` (kéo theo trigger và FK của nó).
8. `invoice_items`, `invoices`: mục 5.5.
9. Tạo hàm và trigger mới: `prevent_result_mutation`, `touch_service_order_updated_at` (mục 5.7).
10. DROP FUNCTION (sau khi đã DROP bảng và trigger liên quan): `enforce_diagnostic_findings_scope()`, `enforce_diagnostic_report_scope()`, `enforce_diagnostic_request_study_state()`, `prevent_completed_diagnostic_findings_mutation()`, `prevent_used_diagnostic_report_mutation()`, `touch_diagnostic_request_updated_at()`, `reject_visit_imaging_result_receipt_mutation()`.
11. Chạy lại kiểm tra của bước 1. Kết quả phải rỗng.

### 5.11 Java: trước và sau ở các điểm khó

**A. Retry constraint và replay submit** (`ClinicalImagingRequestServiceImpl`, `ClinicalImagingRequestTransactionService`)

Trước:
```java
static final String REPLAY_KEY = "uk_clinical_imaging_replay_doctor_key";
static final String ROUND_NUMBER = "uk_clinical_round_visit_number";
var existing = replays.findByDoctorProfileIdAndIdempotencyKey(command.doctorProfileId(), command.idempotencyKey());
replays.saveAndFlush(new ClinicalImagingRequestReplayJpaEntity(command.doctorProfileId(), command.idempotencyKey(), fingerprint, round.id(), clock.instant()));
```
Sau:
```java
static final String SUBMIT_KEY = "uk_clinical_orders_submit_key";
static final String ROUND_NUMBER = "uk_clinical_orders_round_number";
var head = orders.findFirstByRequestingDoctorIdAndSubmitIdempotencyKey(command.doctorProfileId(), command.idempotencyKey());
if (head.isPresent()) return replay(command, fingerprint, head.get());        // đọc đợt từ các dòng cùng ordering_round_id
// writer.createRound(..., command.idempotencyKey(), fingerprint) ghi khoá vào dòng đầu (round_head = true); không còn save replay
```
Logic retry giữ nguyên; chỉ đổi tên constant.

**B. Liên kết ảnh** (`DiagnosticEvidenceServiceImpl.linkConfirmedStudy`, phần cuối)

Trước:
```java
attempt.setStudyRef(evidence.studyRef());
attempts.saveAndFlush(attempt);
request.setStatus(DiagnosticRequestStatus.RESULT_AVAILABLE);
requests.saveAndFlush(request);
if (request.getServiceOrderId() != null) { orderPort.markResultAvailable(request.getServiceOrderId()); }
recordAudit(evidence.eventId(), request, "LINK_CONFIRMED_STUDY", ...);
enqueue(evidence.eventId(), request, RESULT_READY_EVENT, Map.of(...));
```
Sau:
```java
requests.linkStudy(request.getId(), evidence.studyRef(), clock.instant());   // UPDATE: execution_status = RESULT_AVAILABLE, study_ref, WHERE version = :v
requiredOrderPort().markResultAvailable(request.getId());                   // clinical, cùng transaction
requiredVisitPort().publishResultReady(request.getId(), request.getVisitId(), evidence.eventId()); // visits.imaging_result_ready_at
auditLog.record(AuditEntry.builder("diagnostic_audit_events", "DIAGNOSTIC", "LINK_CONFIRMED_STUDY:ALLOWED", "DIAGNOSTIC_REQUEST")
        /* .entityId(request.getId()) theo API builder của phase 01 */ .build());
// không còn enqueue(): không outbox
```

**C. Phát hành** (`DiagnosticFindingsServiceImpl.complete`, phần cuối)

Trước:
```java
outbox.save(new DiagnosticOutboxJpaEntity(UUID.randomUUID(), eventId, requestId, request.getVisitId(), RELEASE_EVENT, payload, PENDING, 0, Instant.now(), null, null));
saveResult(actor, request, COMPLETE, idempotencyKey, Map.of("eventId", eventId.toString(), "findingsVersion", findingsVersion, "releaseState", "RELEASE_PENDING"));
return new DiagnosticFindingsReleaseView(requestId, findingsVersion, "RELEASE_PENDING");
```
Sau:
```java
results.markCompleted(version.getId(), report.getId(), now);          // một UPDATE: state, completed_at, released_at (trigger cho đúng một lần)
requiredVisitPort().publishFindingsReleased(event);                   // visits.findings_released_at
requiredImagingPort().acceptFindingsRelease(event);                   // imaging_studies.release_event_id, released_*
saveReplayKey(version.getId(), COMPLETE, idempotencyKey, fingerprint);// complete_idempotency_key trên dòng kết quả
return new DiagnosticFindingsReleaseView(requestId, findingsVersion, "RELEASED");
```

**D. Nhận kết quả đã có tại lượt khám** (`VisitImagingCoordinationServiceImpl.recordResultReady`)

Trước:
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordResultReady(UUID requestId, UUID visitId, UUID eventId) {
    record(diagnostic.requireResultReady(requestId, visitId, eventId));   // insertReceipt + findById + so sánh hash
}
```
Sau:
```java
@Transactional(propagation = Propagation.MANDATORY)   // cùng transaction với link ảnh; không REQUIRES_NEW
public void recordResultReady(UUID requestId, UUID visitId, UUID eventId) {
    var event = diagnostic.requireResultReady(requestId, visitId, eventId);   // đọc trạng thái trên clinical_service_orders
    if (visits.markImagingResultReady(visitId, event.patientId(), clock.instant()) == 0
            && !visits.hasImagingResultReady(visitId)) throw new VisitOperationException("EVIDENCE_MISMATCH");
}
// SQL: UPDATE visits SET imaging_result_ready_at = COALESCE(imaging_result_ready_at, :now)
//      WHERE id = :visitId AND patient_profile_id = :patientId
```
Giữ kiểm tra `patientId` như cũ. Không còn `VisitImagingResultReceiptRepository`.

**E. Lưu ảnh lần đầu, không SELECT trước INSERT** (`ImagingUploadRegistrationService.register`)

Trước:
```java
var previous = idempotency.findByActorAccountIdAndRequestIdAndAttemptIdAndOperationTypeAndIdempotencyKey(...);
if (previous.isPresent()) { ... IDEMPOTENCY_CONFLICT nếu sha khác; trả registration cũ }
operations.saveAndFlush(new ImagingIngestOperationJpaEntity(operation));
idempotency.saveAndFlush(new ImagingIdempotencyRecordJpaEntity(...));   // DataIntegrityViolation -> IDEMPOTENCY_CONFLICT
```
Sau:
```java
// register() không @Transactional; bắt lỗi ở đây, không ở trong transaction đã lỗi
try {
    return new Registration(transactions.insertStarted(row).toModel(), true);   // @Transactional: saveAndFlush
} catch (DataIntegrityViolationException failure) {
    if (!hasConstraint(failure, "uk_imaging_studies_upload_scope")) throw failure;
    return new Registration(transactions.findReplay(actorId, requestId, attemptId, key, sha256), false); // @Transactional(readOnly): so sha, lệch -> IDEMPOTENCY_CONFLICT
}
```

**F. Kiểm tra series và instance của DICOMweb** (`ImagingDicomWebScopeResolver`)

Trước:
```java
if (... || series.findByStudyRefAndSeriesInstanceUid(study.getStudyRef(), pathSeriesUid).isEmpty()) return Optional.empty();
```
Sau:
```java
if (... || !studies.containsDicomIndex(study.getId(), probe(pathSeriesUid))) return Optional.empty();

// ImagingStudyRepository
@Query(value = "select exists (select 1 from imaging_studies s where s.id = :id and s.dicom_index @> cast(:probe as jsonb))", nativeQuery = true)
boolean containsDicomIndex(@Param("id") UUID id, @Param("probe") String probeJson);
// probe: {"series":[{"uid":"<uid>"}]} hoặc {"series":[{"uid":"<uid>","instances":[{"sop_uid":"<sop>"}]}]}, dựng bằng ObjectMapper, uid đã qua regex UID
```

**G. Danh sách đợt cho lễ tân** (`ClinicalRoundWorkflowServiceImpl.listReceptionRounds`)

Trước:
```java
var result = rounds.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderedAt").and(Sort.by("id"))));
```
Sau:
```java
// Dòng round_head là đầu đợt; lấy size + 1 để biết hasNext
var heads = orders.findRoundHeads(size + 1, (long) page * size);   // native: WHERE round_head ORDER BY ordered_at DESC, ordering_round_id LIMIT :limit OFFSET :offset
boolean hasNext = heads.size() > size;
var pageHeads = heads.subList(0, Math.min(size, heads.size()));
```
Đợt trả về lấy `roundId = ordering_round_id`, `roundNumber = round_number`, `orderedAt = ordered_at`, `patientId`, `visitId`, `orderedByDoctorId = requesting_doctor_id` từ dòng head.

**H. Gán phòng cho phiếu** (`DiagnosticImagingRequestRegistrationImpl.register`)

Trước:
```java
requests.insertAssigned(UUID.randomUUID(), command.visitId(), ..., command.serviceOrderId());   // INSERT diagnostic_requests
```
Sau:
```java
int changed = requests.assignImagingRoom(command.serviceOrderId(), command.roomId(), service.imagingType(), orderingDoctorAccountId, clock.instant());
// UPDATE clinical_service_orders SET room_id, imaging_type, ordering_doctor_account_id, execution_status = 'PENDING', updated_at, version = version + 1
// WHERE id = :id AND execution_status IS NULL AND order_status = 'ORDERED' AND payment_status = 'PAID' AND visit_id = :visit AND patient_id = :patient AND service_id = :service
if (changed == 0) { /* đọc lại dòng: cùng phòng và khớp dữ liệu thì trả replay; khác thì STATE_CONFLICT / EVIDENCE_MISMATCH như cũ */ }
```
Nhánh "tìm yêu cầu đã có" đầu hàm được thay bằng việc đọc chính dòng phiếu.

### 5.12 Đọc và ghi chéo module

| Từ → đến | Cơ chế | Hiện trạng (product) | Sau phase 03 | Duyệt |
|---|---|---|---|---|
| diagnostic → bảng `clinical_service_orders` | JPA + native | `DiagnosticRequestJpaEntity` trên `diagnostic_requests` | cùng entity trỏ tới `clinical_service_orders` với cột diagnostic (`@SQLRestriction("execution_status IS NOT NULL")`, không map `version`, `updated_at`); ghi bằng native UPDATE có WHERE | NghiaDT (MỚI) |
| diagnostic → clinical (port) | `ClinicalImagingOrderPort` | `requireOpenImagingOrder`, `markResultAvailable`, `cancelOrderIfOpen` | giữ | — |
| visit → diagnostic (port) | `DiagnosticResultEventQueryService.requireResultReady/requireFindingsRelease` | đọc outbox | đọc dòng nguồn | — |
| diagnostic → visit (port) | `VisitDiagnosticPort.publishResultReady/publishFindingsReleased` | `REQUIRES_NEW` | cùng transaction | — |
| imaging → diagnostic (service) | `DiagnosticEvidenceService.linkConfirmedStudy` | giữ | giữ | — |
| diagnostic → imaging (port) | `ImagingStudyPort.confirmIngestedStudy`, `acceptFindingsRelease` | giữ | giữ, đọc `confirmation_event_id`, `release_event_id` | — |
| billing → clinical (port) | `ClinicalRoundBillingPort` | `markRoundPaid(roundId)`, `requirePayableRound(roundId)` | thêm `markOrdersPaid(List<UUID>)`, `roundIdOfOrders(List<UUID>)` | Hùng (phase 08) |
| billing → `invoice_items.service_order_id` | JPA (billing) | không có | cột mới trong bảng billing; không đọc bảng clinical bằng SQL | — |
| clinical → diagnostic (port) | `DiagnosticOrderStatusQueryService`, `DiagnosticImagingRequestRegistration` | giữ | giữ | — |
| imaging → visit | không | không | không | — |

Không thêm SQL đọc/ghi chéo bảng của module khác ngoài dòng MỚI ở bảng đầu. Dòng MỚI đó phải được NghiaDT duyệt (agent-rules 6.4). Nếu không duyệt, phương án thay thế là đưa toàn bộ ghi cột diagnostic qua `ClinicalImagingOrderPort` (thêm khoảng bốn hàm ở `clinical`).

### 5.13 Test

- Không có test migration `upgradeFrom…` nào đọc bảng bị bỏ trong danh sách (đã kiểm), nên không cần `@Disabled` ở phase này.
- Test chạy SQL trực tiếp vào bảng bị bỏ: sửa sang bảng mới, giữ nguyên ý nghĩa assert, không xoá để cho xanh (agent-rules 6.5). `DiagnosticStartImagingTest` không có trên product (chỉ trên `wip/diagnostic-start-imaging`): bỏ khỏi danh sách.
- Test của tính năng bị bỏ: không có trong phase này.
- Test mới bắt buộc (mục 6 "Create"):
  - `ImagingResultLifecyclePostgresIntegrationTest` (e2e): đặt phiếu đã thanh toán, dispatch vào phòng, seed một lần chụp như `DoctorImagingReviewIntegrationTest` (vì product không có đường tạo attempt), upload ảnh tới LINKED, bác sĩ review và complete, rồi kiểm: `visits.imaging_result_ready_at` và `findings_released_at` đã set; `WalkInVisitLifecycleServiceImpl` nhận bằng chứng hoàn tất (clinical và diagnostic) có kết quả; `releaseState = RELEASED`.
  - `DiagnosticActionReplayPostgresIntegrationTest`: mỗi thao tác diagnostic (cancel, room-transfer, review, findings draft, correction, report, complete) có một test gửi trùng cùng khoá, nhận đúng kết quả cũ; cùng khoá khác body thì `IDEMPOTENCY_CONFLICT`.
  - `ImagingUploadSendReplayPostgresIntegrationTest`: upload trùng khoá, SEND trùng khoá.
  - Test UNIQUE: insert trùng `submit_idempotency_key` ra đúng SQLSTATE 23505 với tên `uk_clinical_orders_submit_key`.
  - Test trigger: UPDATE nội dung của phiên bản COMPLETED bị chặn; UPDATE đúng `released_at` trong lần phát hành được phép.

## Related Code Files

Tiền tố đường dẫn: `main:`, `test:`, `db:` như phần Overview.

- Move:
  - `db:migration-staged/V118__final_db_diagnostic_imaging_orders.sql` → `db:migration/V118__final_db_diagnostic_imaging_orders.sql` (`git mv`, cùng PR với code).

- Create (test):
  - `test:diagnostic/service/ImagingResultLifecyclePostgresIntegrationTest.java` (mới).
  - `test:diagnostic/service/DiagnosticActionReplayPostgresIntegrationTest.java` (mới).
  - `test:imaging/service/ImagingUploadSendReplayPostgresIntegrationTest.java` (mới).

- Delete (entity, repository, model, service của bảng bị bỏ):
  - `main:clinical/model/entity/OrderingRoundJpaEntity.java`
  - `main:clinical/model/entity/ClinicalImagingRequestReplayJpaEntity.java`
  - `main:clinical/repository/OrderingRoundRepository.java`
  - `main:clinical/repository/ClinicalImagingRequestReplayRepository.java`
  - `main:diagnostic/model/entity/DiagnosticAttemptJpaEntity.java`
  - `main:diagnostic/model/entity/DiagnosticFindingsVersionJpaEntity.java`
  - `main:diagnostic/model/entity/DiagnosticAuditEventJpaEntity.java`
  - `main:diagnostic/model/entity/DiagnosticHistoryAccessAuditJpaEntity.java`
  - `main:diagnostic/model/entity/DiagnosticIdempotencyRecordJpaEntity.java`
  - `main:diagnostic/model/entity/DiagnosticOutboxJpaEntity.java`
  - `main:diagnostic/model/entity/enums/DiagnosticOutboxStatus.java`
  - `main:diagnostic/model/DiagnosticIdempotencyIdentity.java` (sau khi hết caller)
  - `main:diagnostic/integration/ImagingBillingPort.java` (không có implementation trên product, hết caller sau khi bỏ outbox)
  - `main:diagnostic/repository/DiagnosticAttemptJpaRepository.java`
  - `main:diagnostic/repository/DiagnosticFindingsVersionJpaRepository.java`
  - `main:diagnostic/repository/DiagnosticAuditEventJpaRepository.java`
  - `main:diagnostic/repository/DiagnosticHistoryAccessAuditJpaRepository.java`
  - `main:diagnostic/repository/DiagnosticIdempotencyRecordJpaRepository.java`
  - `main:diagnostic/repository/DiagnosticOutboxJpaRepository.java`
  - `main:imaging/model/entity/ImagingIngestOperationJpaEntity.java` (gộp vào `ImagingStudyJpaEntity`)
  - `main:imaging/model/entity/ImagingStudySeriesJpaEntity.java`
  - `main:imaging/model/entity/ImagingStudyInstanceJpaEntity.java`
  - `main:imaging/model/entity/ImagingUidClaimJpaEntity.java`
  - `main:imaging/model/entity/ImagingUidClaimId.java`
  - `main:imaging/model/entity/ImagingIdempotencyRecordJpaEntity.java`
  - `main:imaging/model/entity/ImagingConfirmationOutboxJpaEntity.java`
  - `main:imaging/model/entity/ImagingReleaseInboxJpaEntity.java`
  - `main:imaging/model/entity/ImagingAuditEventJpaEntity.java`
  - `main:imaging/model/ImagingIdempotencyRecord.java`
  - `main:imaging/repository/ImagingIngestOperationRepository.java` (gộp vào `ImagingStudyRepository`)
  - `main:imaging/repository/ImagingStudySeriesRepository.java`
  - `main:imaging/repository/ImagingStudyInstanceRepository.java`
  - `main:imaging/repository/ImagingUidClaimRepository.java`
  - `main:imaging/repository/ImagingIdempotencyRepository.java`
  - `main:imaging/repository/ImagingConfirmationOutboxRepository.java`
  - `main:imaging/repository/ImagingReleaseInboxRepository.java`
  - `main:imaging/repository/ImagingAuditEventRepository.java`
  - `main:imaging/service/impl/ImagingIdempotencyPersistenceService.java`
  - `main:imaging/service/impl/ImagingConfirmationOutboxPersistenceService.java` (logic acknowledge, retry, reconcile chuyển vào `ImagingIngestOperationPersistenceService`)
  - `main:visit/model/entity/VisitImagingResultReceiptJpaEntity.java`
  - `main:visit/repository/VisitImagingResultReceiptRepository.java`
  - `test:visit/VisitImagingResultReceiptPostgresIntegrationTest.java`

- Modify (main):
  - `clinical`:
    - `main:clinical/model/entity/ServiceOrderJpaEntity.java`: map thêm cột `round_number`, `ordered_at`, `round_head`, `submit_*`, `attempt_id`, `study_ref`, `execution_status` (chỉ đọc); giữ `@Version`.
    - `main:clinical/repository/ServiceOrderRepository.java`: thêm `findFirstByRequestingDoctorIdAndSubmitIdempotencyKey`, `findFirstByVisitIdAndRoundHeadTrueOrderByRoundNumberDesc`, `findRoundHeads(limit, offset)` (native).
    - `main:clinical/service/impl/ClinicalImagingRequestTransactionService.java`: `submit` và `recoverReplay` tra khoá ở dòng head thay cho replay repo; `replay` đọc đợt từ các dòng cùng `ordering_round_id`; bỏ `saveAndFlush(replay)`.
    - `main:clinical/service/impl/ClinicalImagingRequestServiceImpl.java`: đổi hằng `REPLAY_KEY` → `SUBMIT_KEY = "uk_clinical_orders_submit_key"`, `ROUND_NUMBER` → `"uk_clinical_orders_round_number"`; logic retry giữ.
    - `main:clinical/service/impl/ClinicalOrderingTransactionService.java`: `createRound(visitId, patientId, doctorId, snapshots, submitKey, fingerprint)`: không save bảng đợt; dòng đầu có `round_head = true`, `round_number`, `ordered_at`, khoá (null khi không có).
    - `main:clinical/service/impl/ClinicalOrderingServiceImpl.java`: bỏ `OrderingRoundRepository`; `isLatestRoundReadyForReview` đọc dòng head mới nhất của visit; `submit` gọi `createRound(..., null, null)`.
    - `main:clinical/service/impl/ClinicalRoundWorkflowServiceImpl.java`: `listDoctorOrders` lấy `round_number` từ dòng phiếu; `listReceptionRounds` theo mục 5.11 G; `dispatch` đọc đợt từ phiếu (`orders.findByOrderingRoundId`), bỏ `rounds.findById`.
    - `main:clinical/integration/billing/ClinicalRoundBillingAdapter.java`: `requirePayableRound` không dùng bảng đợt (kiểm `orders` không rỗng, lấy visit và patient từ phiếu); `markRoundPaid` đổi thành `markOrdersPaid(List<UUID>)`; thêm `roundIdOfOrders(List<UUID>)`.
  - `diagnostic`:
    - `main:diagnostic/model/entity/DiagnosticRequestJpaEntity.java`: `@Table("clinical_service_orders")`, `@SQLRestriction("execution_status IS NOT NULL")`; đổi cột (`ordering_doctor_account_id`, `execution_status`, `attempt_id`, `cancellation_*`); bỏ `@Version` và `updated_at`; `serviceOrderId` là cột `id` chỉ đọc.
    - `main:diagnostic/model/entity/DiagnosticReportJpaEntity.java`: map các cột mục 5.3 (`service_order_id`, `version_number`, `state`, `findings_text`, `report_*`, `completed_at`, `released_at`, khoá); thêm `markCompleted(...)`.
    - `main:diagnostic/model/DiagnosticAttempt.java`: giữ domain; nguồn dữ liệu là các cột `attempt_id`, `first_use_*`, `study_ref` của phiếu.
    - `main:diagnostic/repository/DiagnosticRequestJpaRepository.java`: thay `insertAssigned` bằng `assignImagingRoom` (UPDATE có điều kiện, mục 5.11 H); `linkStudy`, `updateExecution` (@Modifying, WHERE version); đổi tên bảng trong các `@Query`.
    - `main:diagnostic/repository/DiagnosticReportJpaRepository.java`: thêm truy vấn theo `(service_order_id, version_number)`, bản COMPLETED mới nhất, và tìm theo khoá (draft, correction, upload, complete).
    - `main:diagnostic/service/DiagnosticEvidenceService.java`: bỏ `dispatchDueOutbox`, `DispatchSummary`, `RetryPolicy` nếu hết caller.
    - `main:diagnostic/service/impl/DiagnosticEvidenceServiceImpl.java`: bỏ `outbox`, `deliver`, `dispatchDueOutbox`, `dispatchFindingsRelease`, `requireReleaseAck`, `enqueue`; `linkConfirmedStudy` theo mục 5.11 B; `recordFirstUse` ghi cột attempt của phiếu; `recordAudit` → `AuditLogWriter`.
    - `main:diagnostic/service/impl/DiagnosticFindingsServiceImpl.java`: `saveDraft`, `createCorrection`, `uploadReport`, `review`, `complete` ghi khoá trên dòng (mục 5.6); `complete` theo mục 5.11 C; `audit` → `AuditLogWriter`; `uploadReport` cùng phiên bản đã có báo cáo thì `STATE_CONFLICT`.
    - `main:diagnostic/service/impl/DiagnosticRequestServiceImpl.java`: `cancel` và `transferRoom` ghi khoá trên dòng phiếu; giữ `requiredOrderPort().cancelOrderIfOpen`; `requireLinkableOrder` dùng dòng phiếu thay `existsByServiceOrderIdAndStatusNot`.
    - `main:diagnostic/service/impl/DiagnosticImagingRequestRegistrationImpl.java`: mục 5.11 H.
    - `main:diagnostic/service/impl/DiagnosticStatusServiceImpl.java`: đọc attempt và phiếu từ cột; bỏ `DiagnosticAttemptJpaRepository`.
    - `main:diagnostic/service/impl/DiagnosticOrderStatusQueryServiceImpl.java`: đổi nguồn đọc phiếu.
    - `main:diagnostic/service/impl/DiagnosticTechnicianRequestQueryServiceImpl.java`: đổi nguồn đọc phiếu.
    - `main:diagnostic/service/impl/DiagnosticResultEventQueryServiceImpl.java`: `requireResultReady` và `requireFindingsRelease` kiểm trên dòng nguồn (không còn outbox).
    - `main:diagnostic/service/impl/DiagnosticHistoryServiceImpl.java`: ghi VIEW_HISTORY qua `AuditLogWriter`; đọc kết quả từ `diagnostic_reports`.
    - `main:diagnostic/service/impl/DiagnosticPatientHistoryQueryServiceImpl.java`: như trên.
    - `main:diagnostic/service/impl/DiagnosticViewerAuthorizationServiceImpl.java`: đọc study và kết quả từ dòng mới.
    - `main:diagnostic/service/impl/DiagnosticVisitCompletionEvidenceServiceImpl.java`: bốn truy vấn gộp (requests, attempts, findings, reports) thành hai (orders, results).
  - `imaging`:
    - `main:imaging/model/entity/ImagingStudyJpaEntity.java`: PK `id`; thêm cột mục 5.4; `toModel()`/`fromModel(...)` lấy logic từ `ImagingIngestOperationJpaEntity`; `dicom_index` map `@JdbcTypeCode(SqlTypes.JSON)`.
    - `main:imaging/repository/ImagingStudyRepository.java`: `findByStudyRef`, `findByStudyInstanceUid`, `findByUploadScope`, `findByConfirmationEventId`, `findDueConfirmations(now, limit)`, `containsDicomIndex` (native `@>`), `findByReleaseEventId`.
    - `main:imaging/repository/ImagingReconciliationCaseRepository.java`: truy vấn native đổi tham chiếu sang `imaging_studies(id)`; giữ tên hàm.
    - `main:imaging/security/ImagingDicomWebScopeResolver.java`: mục 5.11 F; bỏ `ImagingStudySeriesRepository`, `ImagingStudyInstanceRepository`.
    - `main:imaging/service/impl/ImagingAuditPersistenceService.java`: ghi qua `AuditLogWriter`; giữ `@Transactional`, `SAFE_METADATA_KEYS`; `correlationId` vào `metadata.correlationRef`.
    - `main:imaging/service/impl/ImagingConfirmationDispatcher.java`: quét `imaging_studies` (`findDueConfirmations`); `eventId` = `confirmation_event_id`; link và bàn giao trong một transaction; bookkeeping ở `REQUIRES_NEW` (mục 5.8, bước 4).
    - `main:imaging/service/impl/ImagingIngestOperationPersistenceService.java`: làm việc trên `imaging_studies` (transition có version, `recordValidation`, `recordStudyReferences`); nhận `acknowledge`, `retry`, `reconcile` từ outbox persistence bị xoá.
    - `main:imaging/service/impl/ImagingReconciliationServiceImpl.java`: `openOperation` dùng `imaging_studies.id`; `hasLinkedEvidence` kiểm `release_event_id` trên dòng thay vì `ImagingReleaseInboxRepository`.
    - `main:imaging/service/impl/ImagingSendService.java`: ghi `confirmation_event_id`, `next_confirmation_attempt_at`, `send_*` trên dòng; bỏ outbox và idempotency save.
    - `main:imaging/service/impl/ImagingStudyPersistenceService.java`: `persistConfirmedStudy` UPDATE một dòng (`study_ref`, `archive_study_ref`, `study_instance_uid`, `dicom_index`, `series_count`, `instance_count`); bỏ series, instance, uid claim.
    - `main:imaging/service/impl/ImagingUploadRegistrationService.java`: mục 5.11 E; bỏ `ImagingIdempotencyRepository`.
    - `main:imaging/service/impl/ImagingUploadOrchestrator.java`: gọi persistence mới; giữ `StudyReferences`.
    - `main:imaging/service/impl/ImagingViewerGrantService.java`: `studies.findById(studyRef)` → `findByStudyRef(studyRef)`.
    - `main:imaging/integration/diagnostic/ImagingStudyContractAdapter.java`: `confirmIngestedStudy` đọc dòng theo `confirmation_event_id`; `acceptFindingsRelease` ghi `release_event_id`, `released_*`, `findings_released_at` trong cùng transaction; bỏ `noRollbackFor` ở đường nội bộ (mục 9); audit → `AuditLogWriter`.
  - `visit`:
    - `main:visit/model/entity/ClinicVisitJpaEntity.java`: thêm `imagingResultReadyAt`, `findingsReleasedAt` (phối hợp với phase 05).
    - `main:visit/repository/ClinicVisitJpaRepository.java`: `markImagingResultReady(visitId, patientId, now)` (UPDATE COALESCE), `hasImagingResultReady(visitId)`.
    - `main:visit/service/impl/VisitImagingCoordinationServiceImpl.java`: mục 5.11 D; `acknowledgeFindingsRelease` ghi `findings_released_at` (MANDATORY); bỏ `VisitImagingResultReceiptRepository`.
    - `main:visit/integration/diagnostic/VisitDiagnosticReadAdapter.java`: giữ ánh xạ lỗi; bỏ nhánh `IDEMPOTENCY_CONFLICT` do receipt (giữ mã hàm).
  - `billing`:
    - `main:billing/model/entity/InvoiceJpaEntity.java`: bỏ cột `clinical_ordering_round_id` và getter; `clinicalRound(id, number, patient, actor, fingerprint)` không nhận roundId.
    - `main:billing/model/entity/InvoiceItemJpaEntity.java`: thêm `serviceOrderId`.
    - `main:billing/repository/InvoiceRepository.java`: bỏ `findFirstByClinicalOrderingRoundId`; thêm truy vấn hoá đơn theo item có `service_order_id` thuộc danh sách.
    - `main:billing/repository/InvoiceItemRepository.java`: thêm `findFirstByServiceOrderIdIn`.
    - `main:billing/service/impl/InvoiceServiceImpl.java`: `createForClinicalRound` lấy danh sách phiếu từ port, tra hoá đơn cũ theo phiếu, ghi `service_order_id` cho từng dòng, bắt `DataIntegrityViolationException` của `uk_invoice_items_service_order` rồi trả hoá đơn cũ; `roundId` của phản hồi lấy từ `roundIdOfOrders`.
    - `main:billing/service/impl/InvoiceSettlementSupport.java`: `markRoundPaid(invoice.getClinicalOrderingRoundId())` → `markOrdersPaid(serviceOrderIdsOf(invoice))`.
    - `main:billing/integration/ClinicalRoundBillingPort.java`: thêm `markOrdersPaid(List<UUID>)`, `roundIdOfOrders(List<UUID>)`; giữ `requirePayableRound(roundId)`.
    - `main:billing/mapper/BillingMapper.java`: `clinicalOrderingRoundId` của phản hồi lấy từ service, không từ entity.
  - Không đổi: `main:billing/model/dto/response/InvoiceResponse.java`, `main:diagnostic/integration/ClinicalImagingOrderPort.java`, `main:diagnostic/integration/clinical/ClinicalImagingOrderAdapter.java`, `main:diagnostic/integration/VisitDiagnosticPort.java`, `main:diagnostic/integration/ImagingStudyPort.java`, `main:diagnostic/integration/FirstUseEvidencePort.java`, `main:imaging/model/entity/ImagingViewerGrantJpaEntity.java`, `main:imaging/controller/*`.

- Modify (db):
  - `db:migration/V118__final_db_diagnostic_imaging_orders.sql` (sau khi Move): thực hiện đủ mục 5.10.

- Modify (test). Các file dưới đây có tham chiếu bảng hoặc class bị đổi; sửa fixture và assert theo bảng mới, giữ ý nghĩa assert. Nhóm "kiểm lại" là các file chỉ đọc `invoices`/`invoice_items` (không đổi cấu trúc gây lỗi), chỉ sửa nếu chạy lỗi:
  - `activitylog/ActivityLogPostgresIntegrationTest.java` (fixture `diagnostic_audit_events` → `audit_logs`, do phase 01)
  - `appointment/AppointmentArrivalT053PostgresIntegrationTest.java` (kiểm lại)
  - `appointment/AppointmentPaymentHoldRealBillingPostgresIntegrationTest.java` (kiểm lại)
  - `appointment/CheckInAppointmentT027PostgresIntegrationTest.java` (kiểm lại)
  - `appointment/PaymentHoldFixtureSupport.java` (kiểm lại)
  - `billing/AppointmentHoldPaymentFlowPostgresTest.java` (kiểm lại)
  - `billing/BillingFixtureCleanupTest.java` (kiểm lại)
  - `billing/BillingInvoicePostgresIntegrationTest.java` (fixture `clinical_ordering_rounds`, `clinical_service_orders`, `diagnostic_requests`)
  - `billing/BillingReadQuerySemanticTest.java` (kiểm lại)
  - `billing/BillingReadServiceTest.java` (kiểm lại)
  - `billing/ClinicalRoundBillingTest.java` (port và entity đổi)
  - `billing/HoldBillingTestSupport.java` (kiểm lại)
  - `clinical/ClinicalClosureCoordinationTest.java`
  - `clinical/ClinicalImagingRequestConcurrencyTest.java`
  - `clinical/ClinicalImagingRequestPostgresIntegrationTest.java` (thêm assert replay submit qua `round_head`)
  - `clinical/ClinicalImagingRequestServiceImplTest.java`
  - `clinical/ClinicalOrderingServiceImplTest.java`
  - `clinical/ClinicalOrderingTransactionPostgresIntegrationTest.java`
  - `clinical/ClinicalPrescriptionArchitectureTest.java` (kiểm lại)
  - `clinical/ClinicalRoundWorkflowTest.java`
  - `clinical/ClinicalVisitCompletionEvidenceServiceImplTest.java`
  - `clinical/DoctorCoreProductionWiringTest.java`
  - `clinical/DoctorImagingReviewIntegrationTest.java` (seed attempt bằng `DiagnosticAttemptJpaEntity` → cột phiếu; `RELEASE_PENDING` → `RELEASED`)
  - `clinical/PastMedicalRecordsPostgresIntegrationTest.java`
  - `clinical/PastMedicalRecordsQuerySemanticTest.java`
  - `clinical/VisitCompletionProductionWiringTest.java`
  - `diagnostic/DiagnosticAssignedImagingWorkAuthorizationTest.java`
  - `diagnostic/DiagnosticPaidRegistrationTest.java`
  - `diagnostic/DiagnosticPatientHistoryQueryServiceImplTest.java`
  - `diagnostic/DiagnosticQueueTransferTest.java`
  - `diagnostic/DiagnosticTechnicianRequestQueryServiceTest.java`
  - `diagnostic/DiagnosticViewerAuthorizationServiceTest.java`
  - `diagnostic/DiagnosticVisitCompletionEvidenceServiceTest.java`
  - `diagnostic/repository/DiagnosticHistoryAuditT015PostgresIntegrationTest.java`
  - `diagnostic/repository/DiagnosticPersistenceT011PostgresIntegrationTest.java`
  - `diagnostic/service/DiagnosticEvidenceT013PostgresIntegrationTest.java`
  - `diagnostic/service/DiagnosticFindingsT014PostgresIntegrationTest.java` (`RELEASE_PENDING` → `RELEASED`, hai chỗ)
  - `diagnostic/service/DiagnosticHistoryT015Test.java`
  - `diagnostic/service/DiagnosticRequestT012PostgresIntegrationTest.java`
  - `diagnostic/service/DiagnosticStatusT015Test.java`
  - `diagnostic/service/DiagnosticTransitionPolicyTest.java`
  - `diagnostic/service/DoctorImagingRegistrationTest.java`
  - `imaging/integration/diagnostic/ImagingStudyContractAdapterReleaseTest.java`
  - `imaging/repository/ImagingPersistenceT014PostgresIntegrationTest.java`
  - `imaging/security/ImagingDicomWebScopeResolverTest.java`
  - `imaging/service/ImagingAuditPersistenceServiceTest.java`
  - `imaging/service/ImagingConfirmationDispatcherTest.java`
  - `imaging/service/ImagingIngestApplicationServiceAuthorizationTest.java`
  - `imaging/service/ImagingSendServiceTest.java`
  - `imaging/service/ImagingUploadRegistrationServiceTest.java`
  - `imaging/service/ImagingViewerGrantServiceTest.java`
  - `imaging/service/impl/ImagingReconciliationServiceTest.java`
  - `imaging/service/impl/ImagingUploadOrchestratorTest.java`
  - `staff/StaffRoomAssignmentQueryServiceTest.java`
  - `visit/WalkInFeeAdmissionPostgresIntegrationTest.java` (kiểm lại)

## Implementation Steps
1. Sau khi phase 01 và phase 05 đã merge vào `final-db`: `git fetch && git checkout -b final-db-p03-diagnostic-imaging origin/final-db`. Kiểm `git log` có commit của 01 và 05.
2. Chốt các câu hỏi chặn Q1, Q2, Q3, Q5, Q7 với NghiaDT và ghi quyết định vào mô tả PR. Đến lúc đó mới sửa billing (Q1), imaging (Q2), transfer (Q3), e2e (Q5), và ghi cột diagnostic trên bảng clinical (Q7).
3. Reset Docker: `docker compose -f docker-compose.final-db.yml down -v && docker compose -f docker-compose.final-db.yml up -d`. Chạy kiểm tra của mục 5.10 bước 1; ghi kết quả.
4. `git mv` file V118 từ `migration-staged/` sang `migration/`. Viết nội dung theo mục 5.2–5.10. Chạy app với profile `final-db` trên DB rỗng: Flyway áp V001–V118 không lỗi; Hibernate `validate` không lỗi.
5. Sửa `clinical` và `billing` (mục 5.2, 5.5, 5.11 A, G, và billing port) rồi xoá class của bảng bị bỏ.
6. Sửa `diagnostic` (mục 5.3, 5.6, 5.8 bước 5–6, 5.11 B, C, H). Diagnostic ghi cột execution bằng native UPDATE có WHERE; không map `version`.
7. Sửa `imaging` (mục 5.4, 5.6, 5.8 bước 2–4, 5.11 E, F). Rà các chỗ `noRollbackFor` và gỡ ở đường nội bộ (mục 9).
8. Sửa `visit` (mục 5.11 D). `REQUIRES_NEW` đổi thành `MANDATORY`.
9. Đổi mọi ghi audit sang `AuditLogWriter`. Kiểm từng call site nằm trong `@Transactional`.
10. Sửa test theo mục "Modify (test)", thêm 3 file test mới (mục 5.13). Đổi `RELEASE_PENDING` thành `RELEASED` ở ba chỗ đã nêu.
11. Chạy `./mvnw test` (env theo `otcms/AGENTS.md`). So với danh sách lỗi có sẵn; mọi lỗi khác phải sửa.
12. Chạy các lệnh grep "phải rỗng" ở mục Success Criteria. So OpenAPI: `node plans/261113-final-db/tools/compare-openapi.mjs plans/261113-final-db/api-baseline.json /tmp/api-after.json`.
13. Viết PR theo mẫu agent-rules mục 8, ghi "Đổi migration: có" nếu đã đổi các câu lệnh của V118 so với bản staged, kèm lý do; ghi "File ngoài danh sách phase" nếu có.

## Success Criteria
- [ ] Grep rỗng (chạy từ `otcms/`): `grep -rnE "OrderingRoundJpaEntity|OrderingRoundRepository|ClinicalImagingRequestReplay|DiagnosticAttemptJpa|DiagnosticOutboxJpa|DiagnosticIdempotencyRecordJpa|DiagnosticAuditEventJpa|DiagnosticHistoryAccessAuditJpa|DiagnosticFindingsVersionJpa|ImagingIngestOperationJpa|ImagingIngestOperationRepository|ImagingStudySeriesJpa|ImagingStudySeriesRepository|ImagingStudyInstanceJpa|ImagingStudyInstanceRepository|ImagingUidClaim|ImagingConfirmationOutboxJpa|ImagingConfirmationOutboxRepository|ImagingReleaseInboxJpa|ImagingReleaseInboxRepository|ImagingIdempotencyRecordJpa|ImagingIdempotencyRepository|ImagingAuditEventJpa|ImagingAuditEventRepository|VisitImagingResultReceipt|ImagingBillingPort|dispatchDueOutbox" backend/src/main/java backend/src/test/java`
- [ ] Grep rỗng: `grep -rnE "\b(clinical_ordering_rounds|clinical_imaging_request_replays|diagnostic_requests|diagnostic_attempts|diagnostic_findings_versions|diagnostic_outbox|diagnostic_idempotency_records|diagnostic_audit_events|diagnostic_history_access_audits|imaging_ingest_operations|imaging_study_series|imaging_study_instances|imaging_uid_claims|imaging_idempotency_records|imaging_confirmation_outbox|imaging_release_inbox|imaging_audit_events|visit_imaging_result_receipts)\b" backend/src/main/java backend/src/test/java`
- [ ] Grep rỗng: `grep -rn "RELEASE_PENDING" backend/src/main/java backend/src/test/java`
- [ ] Grep rỗng: `grep -rn "clinical_ordering_round_id\|getClinicalOrderingRoundId" backend/src/main/java` (trừ khi Q1 chọn giữ cột)
- [ ] Kiểm pg_proc và pg_views (mục 5.10 bước 11) rỗng.
- [ ] `\dt public.*` không còn 18 bảng bỏ ở mục 5.1; `imaging_reconciliation_cases`, `diagnostic_reports`, `imaging_studies`, `clinical_service_orders`, `imaging_viewer_grants`, `clinical_medical_document_payloads` vẫn có.
- [ ] Hibernate `validate` và app profile `final-db` khởi động được.
- [ ] `./mvnw test`: không có lỗi mới ngoài các lỗi có sẵn trong `agent-rules.md` mục 7.
- [ ] OpenAPI: `no differences`.
- [ ] E2E `ImagingResultLifecyclePostgresIntegrationTest` xanh: `visits.imaging_result_ready_at` và `findings_released_at` đã set; `releaseState = RELEASED`.
- [ ] Mỗi thao tác có một test gửi trùng (mục 5.13) xanh; cùng khoá khác body → `IDEMPOTENCY_CONFLICT`.
- [ ] Test UNIQUE: trùng `submit_idempotency_key` ra SQLSTATE 23505, tên `uk_clinical_orders_submit_key`.
- [ ] Test trigger: UPDATE nội dung phiên bản COMPLETED bị chặn; UPDATE `released_at` trong lần phát hành được phép.
- [ ] PR có kết quả thật của `./mvnw test`, OpenAPI, các lệnh grep.

## Risk Assessment
| Rủi ro | Dấu hiệu | Xử lý |
|---|---|---|
| Hai entity cùng ánh xạ `clinical_service_orders` (`ServiceOrderJpaEntity` và `DiagnosticRequestJpaEntity`) | `OptimisticLockException` trong cancel hoặc complete; cột bị ghi đè | Diagnostic không map `version`, `updated_at`; ghi bằng native UPDATE có WHERE; test cancel và complete chạy trong cùng transaction |
| Trên product không có đường tạo lần chụp (attempt). `recordFirstUse` không có caller, `acquisition-starts` chỉ có ở `wip` | E2E không seed được attempt; luồng ảnh không chạy trên product dù code đúng | E2E seed attempt như `DoctorImagingReviewIntegrationTest`; không đưa `startImaging` vào phase (Q5); ghi nhận là lỗi có sẵn |
| `noRollbackFor` ở `acceptFindingsRelease` và `send` khi gọi trong transaction chung | Phát hành ghi COMPLETED nhưng imaging không ghi ack (commit nửa vời) | Gỡ `noRollbackFor` ở đường nội bộ; kiểm điều kiện trước khi ghi (mục 5.8) |
| `AuditLogWriter` là `MANDATORY` | Lỗi "no transaction" khi ghi nhật ký | Mọi call site trong `@Transactional`; `ImagingUploadOrchestrator` gọi qua persistence service |
| Trigger hoặc hàm plpgsql đọc bảng đã DROP | Lỗi `relation ... does not exist` lúc chạy | Bước 1 của mục 5.10 và tests; DROP hàm sau khi DROP bảng |
| Đếm đợt theo `(visit_id, round_number)` không còn là constraint trên mọi dòng | Hai đợt cùng số trong một visit | UNIQUE trên dòng `round_head`; `CoordinationGuardService.acquire()` vẫn serialize |
| Replay đổi phòng với khoá cũ (không phải khoá gần nhất) | Khác kết quả cũ | Q3 |
| Reconciliation cấp sự kiện (không có study) không có chỗ trong `imaging_studies` | Mất case trong hàng đợi admin | Giữ `imaging_reconciliation_cases` (Q2) |
| Đổi `releaseState` và set `imaging_result_ready_at` là hành vi khác product | Test cũ assert chuỗi cũ; người dùng thấy trạng thái khác | Q6; cập nhật test |
| Phase 05 và phase 08 sửa cùng `ClinicVisitJpaEntity`, `InvoiceJpaEntity` | Conflict khi rebase | Làm theo thứ tự merge; người merge sau giải xung đột, chỉ giữ dòng của phase mình |
| Tên constraint quá dài (giới hạn 63 byte của Postgres) | Postgres cắt tên, lệch với code tra cứu tên | Tên mới đều dưới 40 ký tự; kiểm bằng `SELECT length(conname)` trước khi mở PR |

## Đề xuất tách 03a / 03b
Phase vượt 400 dòng và có rủi ro cao ở vùng D2. Đề xuất tách theo kiểu expand và contract, không tách theo module:
- 03a (mở rộng, V118 như đã mô tả): thêm mọi cột và bảng đích (mục 5.2–5.5), copy dữ liệu, code mới đọc và ghi cột mới; giữ các bảng cũ và trigger cũ (chưa DROP). Code cũ còn chạy được.
- 03b (thu gọn, số migration mới): DROP bảng cũ, DROP trigger và hàm (mục 5.10 bước 4–10), xoá class và repository cũ, bỏ `noRollbackFor`, xoá outbox.
- Điều kiện: 03b phải sau 03a. Nếu tách, cần một số migration thứ hai, mà plan đã cố định V118 cho phase này và V120 cho phase 10. Cần NghiaDT quyết (Q11).

## Câu hỏi còn mở

> **ĐÃ CHỐT 13/11 (tiêu chí: giữ API + hành vi cũ, ít đụng phase khác) — GHI ĐÈ các mặc định trong file khi khác:**
> - **Q1 (khác mặc định):** KHÔNG drop `invoices.clinical_ordering_round_id`. Đợt chỉ định ("round") không còn là bảng, mà thành cột nhóm `ordering_round_id uuid` trên `clinical_service_orders` (các phiếu cùng đợt dùng chung UUID, giữ đúng UUID cũ khi chép dữ liệu). `invoices.clinical_ordering_round_id` giữ nguyên cột, chỉ bỏ FK. Kết quả: API `clinicalOrderingRoundId` và idempotency theo đợt giữ nguyên, phase 08 không phải sửa, không cần thêm `markOrdersPaid`/`roundIdOfOrders`. `invoice_items.service_order_id` vẫn thêm như plan.
> - **Q2:** GIỮ `imaging_reconciliation_cases` (case mức sự kiện không có study). Tổng thành **35 bảng = đúng mức tối đa**; plan.md đã cập nhật. Không phase nào được thêm bảng nữa.
> - **Q3, Q6:** theo mặc định.
> - **Q7 (khác mặc định):** KHÔNG map entity thứ hai lên `clinical_service_orders` (agent-rules 6.1: mỗi bảng 1 entity; tránh xung đột optimistic lock). Entity duy nhất `ClinicalServiceOrderJpaEntity` ở package `clinical`; `diagnostic` đọc/ghi các cột thực hiện chụp qua một port do `clinical` cung cấp (`ClinicalServiceOrderExecutionPort`, impl trong `clinical`, `@Transactional(propagation = MANDATORY)`).
> - **`noRollbackFor`:** gỡ ở mọi đường phát hành kết quả (`acceptFindingsRelease`, `send`) — phát hành là tất cả hoặc không có gì.
> - **PK `imaging_studies`:** giữ `id` như đề xuất của file. Giữ `ARCHIVED_NOT_SENT`. Không đưa `startImaging` của nhánh wip vào (sẽ thêm endpoint).
> - **Độ dài:** làm theo cách tách 03a/03b ở mục 10 (2 PR, cùng người).

Các câu bên dưới giữ lại để tra lý do.
1. (Chặn phase 08) `invoices.clinical_ordering_round_id`: mặc định bỏ theo schema-33, thêm `markOrdersPaid` và `roundIdOfOrders` vào port. Phương án B: giữ cột, không FK (đơn giản hơn, không thêm hàm port). Chọn nào cũng phải báo Hùng trước khi sửa `InvoiceJpaEntity`.
2. (Chặn đối soát) `imaging_reconciliation_cases`: mặc định giữ bảng (bảng thứ 34, vẫn trong giới hạn 35), vì case cấp sự kiện không có study. Phương án B: gộp study-level vào `imaging_studies`, còn event-level chỉ ở `audit_logs`; hàng đợi admin thay đổi.
3. Khoá replay của transfer: mặc định chỉ nhớ khoá gần nhất trên phiếu. Chấp nhận thay đổi này?
4. `ImagingBillingPort` không có implementation: mặc định xoá. `FirstUseEvidencePort` và `recordFirstUse` giữ, chỉ đổi nơi ghi (dead code, không đổi hành vi).
5. `startImaging` và endpoint `acquisition-starts` ở `wip/diagnostic-start-imaging` (commit bcbfe794): mặc định KHÔNG đưa vào phase, vì thêm endpoint làm lệch OpenAPI. E2E seed attempt. Đồng ý?
6. `releaseState`: đổi `RELEASE_PENDING` thành `RELEASED` (mặc định đồng ý). FE không dùng chuỗi này (đã kiểm). Có chấp nhận đổi hành vi?
7. Diagnostic map entity lên `clinical_service_orders` (đọc và ghi cột execution). Mặc định chấp nhận, ghi bằng native UPDATE. Phương án: toàn bộ ghi qua `ClinicalImagingOrderPort`, thêm khoảng bốn hàm ở clinical.
8. Giữ `ARCHIVED_NOT_SENT` trong CHECK (schema-33 không liệt kê, nhưng code dùng ở `ImagingSendService`). Giữ cột `state` thay cho `status`. Mặc định: có, có.
9. PK của `imaging_studies` là `id` (operationId), không phải `study_ref` như schema-33. Chấp nhận?
10. Category của `diagnostic_history_access_audits`: schema-33 mục 2.11 ghi HISTORY_ACCESS, phase 01 ghi DIAGNOSTIC. Mặc định theo phase 01.
11. Tách 03a/03b (mục 10): nếu tách, cấp thêm số migration thứ hai.
12. Phụ thuộc phase 01: `AuditEntry` cần cho phép truyền `correlationRef` tường minh (mục 5.9).
13. Phát hiện ngoài phạm vi, không sửa trong phase này: trên product không có đường tạo lần chụp, nên luồng ảnh đến kết quả không chạy được; `FIRST_USE_BILLING_OBLIGATION` không bao giờ được ghi. Có muốn xử lý trong đợt này không? Mặc định: không.
