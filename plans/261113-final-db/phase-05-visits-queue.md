---
phase: 5
title: "Lượt khám: gộp check-in và walk-in vào visits, hàng chờ thành queue_entries, audit sang audit_logs"
status: pending
priority: P1
effort: "6h"
owner: Long
dependencies: [1, 2, 4]
migration: V115__final_db_visits_queue.sql
---

# Phase 05: Lượt khám, check-in, walk-in, hàng chờ

## Overview
- `visit_check_ins` bỏ: dữ liệu của nó đã có đủ trong `visits`. Không thêm cột `reception_type` và `receptionist_account_id` (lý do ở mục 4.1).
- `walk_in_requests` thành 2 cột trên `visits`: `walk_in_idempotency_key`, `walk_in_request_fingerprint`.
- `walk_in_reception_queue_entries` và `visit_exam_queue` thành một bảng `queue_entries` với `stage` = `RECEPTION` | `EXAM`.
- `walk_in_reception_queue_history`, `visit_audits`, `visit_assignment_audits` thành `audit_logs` qua `AuditLogWriter`.
- `visit_examinations` bỏ: không có code production đọc hay ghi. Chỉ có 3 câu `DELETE` trong test.
- `visit_assignments` giữ nguyên.
- Viết lại 4 hàm trigger và bỏ các hàm, trigger của `visit_check_ins` và của lịch sử hàng chờ.

Ranh giới:
- Phase 04 (Long) merge trước. Đã đổi `appointment_arrivals` và `VisitWorkflowRepository.findRecordedArrival`. Phase này không làm lại.
- Phase 02 (NghiaDT): `staff_profiles` là đích của FK `doctor_profile_id`. Rebase sau khi 02 vào.
- Phase 03 (NghiaDT) merge SAU phase này, vì cả hai sửa `ClinicVisitJpaEntity`. Phase này KHÔNG thêm `imaging_result_ready_at`, `findings_released_at`, `visit_imaging_result_receipts`.
- Phase 07 (Hùng) xử lý `room_reservations` và `room_reservation_audits`. Phase này không đụng. `VisitRoomCapacityServiceImpl.appendConflict` vẫn ghi `room_reservation_audits` cho đến khi phase 07 xong.

## Requirements
- API giữ nguyên: URL, body, mã HTTP, mã MSG. Gồm `POST /api/v1/visits/walk-ins`, `POST /api/v1/visits/{visitId}/cancellation`, `POST /api/v1/visits/{visitId}/closure`, `POST /api/v1/visits/{visitId}/completion`, `POST /api/v1/visits/{visitId}/care-assignments` (cùng các nhánh extensions, capacity-conflicts, completion), `GET /api/v1/exam-queue`, `GET /api/v1/doctor/exam-queue`, `GET /api/v1/reception-queue`, `GET /api/v1/visits/{visitId}/exam-queue-entry`, `GET /api/v1/visits/{visitId}/progress`, `GET /api/v1/reception/on-shift-doctors`. Các endpoint tạo hàng chờ lễ tân: liệt kê bằng `grep -rn "Mapping" backend/src/main/java/fu/fa26/sep490/g58/otcms_be/visit/controller`.
- Walk-in gửi trùng (cùng lễ tân, cùng khoá, cùng nội dung): trả cùng visit. Khác nội dung: `IDEMPOTENCY_CONFLICT`.
- Thứ tự hàng chờ lễ tân (FIFO theo `arrived_at, id`) và vị trí hàng đợi không đổi.
- Số thứ tự khám theo bác sĩ và ngày vẫn là `max + 1` dưới `CoordinationGuardService`.
- Mỗi chuyển trạng thái hàng chờ, mỗi dòng visit audit và care audit ghi đúng một dòng `audit_logs` trong cùng transaction.
- Quy tắc tài khoản đang nằm trong trigger `record_walk_in_reception_queue_transition` (phải ACTIVE và đúng loại) chuyển sang Java, mã lỗi `ACCESS_DENIED`.

## Architecture

### 4.1 Gộp bảng và bằng chứng cho các cột không thêm
| Bảng cũ | Đi đâu | Ghi chú |
|---|---|---|
| `visit_check_ins` | bỏ | `visits` đã có `arrived_at`, `checked_in_at`, `created_by_account_id`, `prior_appointment_id`. V115 kiểm bằng DO block trước khi DROP |
| `walk_in_requests` | `visits.walk_in_idempotency_key`, `visits.walk_in_request_fingerprint` | UNIQUE theo (`created_by_account_id`, khoá) cho `source = 'WALK_IN'` |
| `walk_in_reception_queue_entries` | `queue_entries`, stage `RECEPTION`, giữ `id` | giữ `arrived_at` (FIFO) |
| `visit_exam_queue` | `queue_entries`, stage `EXAM`, `id` mới | `created_at` cũ thành `entered_at` |
| `walk_in_reception_queue_history` | `audit_logs` (source `walk_in_reception_queue_history`) | mục 4.8 |
| `visit_audits` | `audit_logs` (source `visit_audits`) | mục 4.8 |
| `visit_assignment_audits` | `audit_logs` (source `visit_assignment_audits`) | mục 4.8 |
| `visit_examinations` | bỏ | 3 câu DELETE trong test phải xoá |

Không thêm `reception_type`: giá trị suy ra từ `visits.source` (`SCHEDULED` là `APPOINTMENT_CHECK_IN`, `WALK_IN` là `WALK_IN`). Trigger cũ `enforce_initial_reception_type` bắt buộc hai giá trị này khớp nhau.

Không thêm `receptionist_account_id`: đã kiểm trong code. Có 2 nơi ghi `visit_check_ins`, và trong cả 2 nơi giá trị đều trùng với `created_by_account_id` của visit:
- `VisitWorkflowRepository.insertCheckInVisit`: `created_by_account_id` và `receptionist_account_id` cùng là `actorId`.
- `CreateWalkInVisitServiceImpl`: `ClinicVisitJpaEntity.walkIn(…, actorAccountId, …)` và `VisitInitialReceptionJpaEntity.walkIn(visitId, actorAccountId, …)`.

Lưu ý: khi check-in qua QR, actor là tài khoản bệnh nhân, nên cột tên "receptionist" thực tế là người thao tác. Giữ hành vi cũ.

### 4.2 Bảng `queue_entries` (V115)
```
queue_entries (
  id uuid PK DEFAULT gen_random_uuid(),
  visit_id uuid NOT NULL FK visits(id) ON DELETE RESTRICT,
  stage varchar(16) NOT NULL,          -- RECEPTION | EXAM
  status varchar(32) NOT NULL,         -- RECEPTION: WAITING, LEFT_RECEPTION
                                       -- EXAM: PENDING_PAYMENT, WAITING, IN_EXAMINATION, AWAITING_CONCLUSION, COMPLETED, CANCELLED
  arrived_at timestamptz NULL,         -- RECEPTION: bằng visits.arrived_at, dùng cho FIFO
  entered_at timestamptz NOT NULL,     -- RECEPTION: lúc vào hàng; EXAM: created_at cũ
  left_at timestamptz NULL, exit_reason varchar(24) NULL,      -- RECEPTION
  doctor_profile_id uuid NULL FK staff_profiles(id), room_id uuid NULL FK rooms(id),
  service_id uuid NULL FK clinic_services(id), clinic_date date NULL, queue_number integer NULL,
  queued_at timestamptz NULL, started_at timestamptz NULL, awaiting_at timestamptz NULL, finished_at timestamptz NULL  -- EXAM
)
```
CHECK:
- `ck_queue_entries_stage`: `stage IN ('RECEPTION','EXAM')`.
- `ck_queue_entries_reception` (khi stage = RECEPTION): `arrived_at` NOT NULL; doctor, room, service, clinic_date, queue_number NULL; WAITING thì `left_at` và `exit_reason` NULL; LEFT_RECEPTION thì `left_at` NOT NULL và `exit_reason IN ('CARE_STARTED','CANCELLED','VISIT_CLOSED')`. Copy từ `ck_walk_in_reception_queue_exit`.
- `ck_queue_entries_exam` (khi stage = EXAM): `doctor_profile_id` và `clinic_date` NOT NULL; `arrived_at`, `left_at`, `exit_reason` NULL; `status` thuộc 6 giá trị; PENDING_PAYMENT thì `queue_number` NULL; CANCELLED tuỳ ý; các trạng thái còn lại thì `queue_number > 0`. Copy từ `ck_visit_exam_queue_number`.

Index và UNIQUE (đặt tên như sau, tên cũ trong ngoặc):
- `uq_queue_entries_reception_active_visit (visit_id) WHERE stage = 'RECEPTION' AND status = 'WAITING'` (thay `uk_walk_in_reception_queue_active_visit`).
- `uq_queue_entries_exam_visit (visit_id) WHERE stage = 'EXAM'` (thay PK của `visit_exam_queue`).
- `uq_queue_entries_exam_number (doctor_profile_id, clinic_date, queue_number) WHERE stage = 'EXAM' AND queue_number IS NOT NULL` (thay `uk_visit_exam_queue_number`).
- `uq_queue_entries_exam_one_in_exam (doctor_profile_id) WHERE stage = 'EXAM' AND status = 'IN_EXAMINATION'` (thay `uk_visit_exam_queue_one_in_exam`).
- `ix_queue_entries_reception_fifo (arrived_at, id) WHERE stage = 'RECEPTION' AND status = 'WAITING'`.
- `ix_queue_entries_exam_date (clinic_date, doctor_profile_id) WHERE stage = 'EXAM'`.
- Index cho FK (schema-33, mục 4b): `ix_queue_entries_visit (visit_id, stage)`, `ix_queue_entries_doctor (doctor_profile_id) WHERE doctor_profile_id IS NOT NULL`, `ix_queue_entries_room (room_id) WHERE room_id IS NOT NULL`, `ix_queue_entries_service (service_id) WHERE service_id IS NOT NULL`.

Quy tắc bắt buộc cho người làm: mọi câu SQL chạm `queue_entries` phải có `stage = '…'`. Trước đây `status = 'WAITING'` là đủ vì bảng chỉ có hàng lễ tân. Nay hàng khám cũng có `WAITING`, thiếu stage là lẫn hai hàng chờ.

### 4.3 Thay đổi bảng `visits` (V115)
- `ADD COLUMN walk_in_idempotency_key varchar(128) NULL`, `ADD COLUMN walk_in_request_fingerprint varchar(64) NULL`.
- CHECK: `ck_clinic_visits_walk_in_key_all_or_none` (hai cột cùng NULL hoặc cùng có); `ck_clinic_visits_walk_in_key_source` (có khoá thì `source = 'WALK_IN'`); `ck_clinic_visits_walk_in_key_length` (1..128 ký tự sau trim); `ck_clinic_visits_walk_in_fingerprint` (khớp `^[0-9a-fA-F]{64}$`).
- UNIQUE: `uk_clinic_visits_walk_in_idempotency (created_by_account_id, walk_in_idempotency_key) WHERE walk_in_idempotency_key IS NOT NULL`.

### 4.4 Thứ tự trong V115
1. `CREATE TABLE queue_entries` và toàn bộ CHECK, UNIQUE, index ở mục 4.2.
2. `ALTER TABLE visits` như mục 4.3.
3. Chép dữ liệu demo:
   - `UPDATE visits v SET walk_in_idempotency_key = r.idempotency_key, walk_in_request_fingerprint = r.request_fingerprint FROM walk_in_requests r WHERE r.visit_id = v.id AND r.receptionist_account_id = v.created_by_account_id`.
   - Guard trước khi DROP. Nếu có dòng lệch thì RAISE EXCEPTION, không DROP:
     `DO $$ BEGIN IF EXISTS (SELECT 1 FROM visit_check_ins r JOIN visits v ON v.id = r.visit_id WHERE r.arrived_at IS DISTINCT FROM v.arrived_at OR r.checked_in_at IS DISTINCT FROM v.checked_in_at OR r.receptionist_account_id IS DISTINCT FROM v.created_by_account_id OR r.prior_appointment_id IS DISTINCT FROM v.prior_appointment_id) THEN RAISE EXCEPTION 'visit_check_ins khác visits' USING ERRCODE = '23514'; END IF; END $$;`
   - Hàng chờ: `INSERT INTO queue_entries (id, visit_id, stage, status, arrived_at, entered_at, left_at, exit_reason) SELECT id, visit_id, 'RECEPTION', status, arrived_at, entered_at, left_at, exit_reason FROM walk_in_reception_queue_entries;` và tương tự cho `EXAM` từ `visit_exam_queue` (`gen_random_uuid()`, `exam_status` vào `status`, `created_at` vào `entered_at`).
   - Audit: chép `walk_in_reception_queue_history` (join `walk_in_reception_queue_entries` để lấy `visit_id`), `visit_audits`, `visit_assignment_audits` vào `audit_logs` theo mục 4.8. Giữ `id` gốc.
   - Sau khi chép, tạo UNIQUE partial chống trùng CAPACITY_CONFLICT (thay index cũ `uq_visit_care_assignment_conflict_correlation`): `CREATE UNIQUE INDEX uq_audit_logs_visit_capacity_conflict ON audit_logs (source, record_label, actor_account_id, correlation_id) WHERE source = 'visit_assignment_audits' AND action = 'CAPACITY_CONFLICT';`.
4. `CREATE OR REPLACE FUNCTION` cho 4 hàm ở mục 4.5, và tạo lại trigger trên `queue_entries`.
5. `DROP TABLE walk_in_reception_queue_history; DROP TABLE walk_in_reception_queue_entries; DROP TABLE visit_exam_queue; DROP TABLE visit_check_ins; DROP TABLE walk_in_requests; DROP TABLE visit_audits; DROP TABLE visit_assignment_audits; DROP TABLE visit_examinations;` Không dùng `IF EXISTS`. Bảng history phải drop trước bảng entries (FK).
6. `DROP FUNCTION` 8 hàm: `enforce_checked_in_appointment_reception_evidence()`, `enforce_checked_in_appointment_reception_for_visit(uuid)`, `enforce_initial_reception_type()`, `record_walk_in_reception_queue_transition()`, `reject_direct_walk_in_reception_queue_history_insert()`, `reject_walk_in_reception_queue_history_mutation()`, `reject_append_only_visit_audit_mutation()`, `reject_visit_care_assignment_audit_mutation()`.

Trước khi DROP, sau khi đã viết lại 4 hàm, chạy truy vấn sau. Kết quả phải rỗng, ghi vào PR:
`SELECT proname FROM pg_proc WHERE prosrc ILIKE ANY (ARRAY['%visit_check_ins%','%walk_in_requests%','%walk_in_reception_queue_entries%','%walk_in_reception_queue_history%','%visit_exam_queue%','%visit_audits%','%visit_assignment_audits%','%visit_examinations%']);`

### 4.5 Trigger và hàm
| Đối tượng | Xử lý |
|---|---|
| `enforce_appointment_visit_link()` (gắn `ct_appointments_visit_link`, `ct_clinic_visits_appointment_link`) | viết lại: bỏ khối `SELECT EXISTS (… visit_check_ins …)`. Điều kiện `visit_source = 'SCHEDULED'` đã có trong hàm và đủ thay thế, vì SCHEDULED chỉ tạo bởi check-in. Giữ các RAISE khác |
| `enforce_initial_reception_type()`, `enforce_checked_in_appointment_reception_evidence()`, `enforce_checked_in_appointment_reception_for_visit()` | bỏ. Điều kiện nằm ở `ck_clinic_visits_source_link`, `ck_appointments_visit_status_link` và `enforce_appointment_visit_link` |
| `enforce_walk_in_reception_queue_visit()` | viết lại trên `queue_entries`, chỉ khi `NEW.stage = 'RECEPTION'`. Giữ: visit tồn tại, `source = 'WALK_IN'`, INSERT phải WAITING, `arrived_at` = `visits.arrived_at`. Gắn BEFORE INSERT OR UPDATE OF visit_id, arrived_at, status, entered_at, left_at, exit_reason |
| `protect_walk_in_reception_queue_entry_lifecycle()` | viết lại, chỉ khi `OLD.stage = 'RECEPTION'`. Giữ bất biến id/visit/arrived/entered và WAITING chỉ được sang LEFT_RECEPTION. Gắn BEFORE UPDATE trên `queue_entries` |
| `protect_queued_visit_source_and_arrival()` (gắn `tr_visits_protect_reception_queue_identity`) | viết lại: đọc `queue_entries WHERE stage = 'RECEPTION'` |
| `record_walk_in_reception_queue_transition()` | bỏ. Quy tắc tài khoản chuyển sang Java (mục 4.7), lịch sử ghi bằng `AuditLogWriter` |
| `reject_direct_walk_in_reception_queue_history_insert()`, `reject_walk_in_reception_queue_history_mutation()`, `reject_append_only_visit_audit_mutation()`, `reject_visit_care_assignment_audit_mutation()` | bỏ. Bất biến của `audit_logs` do trigger của phase 01 đảm nhận |
| View `activity_log_entries` | không sửa ở phase này (phase 01 đã viết lại) |

### 4.6 Java: trước → sau (bốn chỗ khó nhất)

(A) Check-in: `VisitWorkflowRepository.insertCheckInVisit` bỏ câu `insert into visit_check_ins …`. Chỉ còn `insert into visits …`. `hasConsistentCheckInEvidence` viết lại:
Trước: `select exists (select 1 from visits v join visit_check_ins r on r.visit_id = v.id where … r.reception_type = 'APPOINTMENT_CHECK_IN' and r.prior_appointment_id is null and r.receptionist_account_id is not null and r.checked_in_at = v.checked_in_at and r.arrived_at = v.arrived_at)`
Sau: `select exists (select 1 from visits v where v.id = :visitId and v.originating_appointment_id = :appointmentId and v.patient_profile_id = :patientId and v.source = 'SCHEDULED')`

(B) Walk-in: `CreateWalkInVisitServiceImpl.create`.
Trước: `replays.findByReceptionistAccountIdAndIdempotencyKey(actor, key)` rồi `stored.getRequestFingerprint()`, `stored.getVisitId()`. Khi tạo: `receptions.save(VisitInitialReceptionJpaEntity.walkIn(…))`, `audits.save(VisitAuditJpaEntity.walkInCreated(…))`, `queueEntries.setAuditActor(…)`, `setAuditCorrelation(…)`, `queueEntries.saveAndFlush(WalkInReceptionQueueEntryJpaEntity.waiting(…))`, `replays.saveAndFlush(WalkInReceptionReplayJpaEntity.create(…))`.
Sau:
```java
var replay = visits.findByCreatedByAccountIdAndWalkInIdempotencyKey(command.actorAccountId(), key);
if (replay.isPresent()) {
    if (!fingerprint.equals(replay.get().getWalkInRequestFingerprint())) throw new VisitOperationException("IDEMPOTENCY_CONFLICT");
    return result(replay.get());
}
// ... kiểm bác sĩ, dịch vụ như cũ ...
requireActiveReceptionist(command.actorAccountId());   // thay quy tắc ACTIVE của trigger cũ
ClinicVisitJpaEntity visit = visits.saveAndFlush(ClinicVisitJpaEntity.walkIn(visitId, patientId, priorAppointmentId,
        command.actorAccountId(), arrivedAt, key, fingerprint));
auditLogWriter.record(VisitAuditEntries.walkInCreated(command.actorAccountId(), visitId, arrivedAt, command.correlationId()));
QueueEntryJpaEntity entry = queueEntries.saveAndFlush(QueueEntryJpaEntity.reception(visitId, arrivedAt, arrivedAt));
auditLogWriter.record(VisitAuditEntries.queueEntered(visitId, entry.getId(), command.actorAccountId(), command.correlationId(), arrivedAt));
// ... examQueue.createPendingPayment / enqueueWaiting như cũ ...
```
Không còn bảng replay để ghi. `ClinicVisitJpaEntity.walkIn` nhận thêm `walkInIdempotencyKey` và `walkInRequestFingerprint`. `requireActiveReceptionist` dùng `ReceptionistAccountAccessService.isActiveReceptionistAccount` (như `ExitWalkInReceptionQueueServiceImpl`), lỗi `ACCESS_DENIED`.

(C) Thoát hàng chờ: `ExitWalkInReceptionQueueServiceImpl.exit`.
Trước: `entries.setAuditActor(…); entries.setAuditCorrelation(…); for (var entryId : entries.findWaitingEntryIdsForUpdate(visitId)) { if (entries.leaveReception(entryId, occurredAt, reason) != 1) throw … }`
Sau:
```java
for (UUID entryId : entries.findWaitingReceptionIdsForUpdate(visitId)) {
    if (entries.leaveReception(entryId, occurredAt, reason) != 1) throw new VisitOperationException("VISIT_RECEPTION_QUEUE_CONFLICT");
    auditLogWriter.record(VisitAuditEntries.queueLeft(visitId, entryId, reason, actorAccountId, correlationId, occurredAt));
}
```
SQL: `select id from queue_entries where visit_id = :visitId and stage = 'RECEPTION' and status = 'WAITING' for update`; `update queue_entries set status = 'LEFT_RECEPTION', left_at = :leftAt, exit_reason = :reason where id = :entryId and stage = 'RECEPTION' and status = 'WAITING'`.

(D) Hàng khám: `VisitExamQueueJpaRepository.ROW_COLUMNS` đổi thành `… q.status as exam_status, … q.entered_at as created_at, … from queue_entries q join visits v on v.id = q.visit_id left join rooms r on r.id = q.room_id where q.stage = 'EXAM'`. Giữ đúng thứ tự cột và alias, vì `ExamQueueItemMapper` đọc theo chỉ số. Các derived method (`countBy…`, `existsBy…`) và JPQL `findMaxQueueNumber` viết lại bằng `@Query` có `stage = 'EXAM'`, vì derived query không thêm được điều kiện stage. `VisitExamQueueServiceImpl` thay `queue.findById(visitId)`, `existsById`, `save` bằng `findExamByVisitId`, `existsExamByVisitId` và `save(QueueEntryJpaEntity)`.

Hàng lễ tân: `VisitReceptionReadRepository` có 4 chỗ đọc `walk_in_reception_queue_entries` (quanh dòng 22, 61, 77, 83). Đổi sang `queue_entries` và thêm `stage = 'RECEPTION'` vào từng chỗ. Chỗ dòng 61 (`left join … q.status = 'WAITING'`) là chỗ dễ sót nhất.

### 4.7 Kiểm tài khoản thay trigger
- Tạo walk-in (WAITING): thêm `requireActiveReceptionist` vào `CreateWalkInVisitServiceImpl`. `WalkInReceptionQueueServiceImpl.createWaitingEntry` đã kiểm sẵn.
- Thoát hàng (CARE_STARTED, CANCELLED, VISIT_CLOSED): `ExitWalkInReceptionQueueServiceImpl` đã kiểm vai trò và trạng thái (DOCTOR cho CARE_STARTED, RECEPTIONIST cho còn lại). Không đổi.
- Trigger cũ khoá dòng tài khoản bằng `FOR SHARE`. Bỏ khoá này; `CoordinationGuardService` đã tuần tự hoá các thao tác này.

### 4.8 Ánh xạ audit sang `AuditLogWriter`
Helper: `visit/mapper/VisitAuditEntries.java`, một hàm static cho mỗi dòng dưới đây.

| source | category | action | record_type | entity_id | record_label | state và metadata |
|---|---|---|---|---|---|---|
| `visit_audits` | VISIT | WALK_IN_CREATED, WALK_IN_CANCELLED, WALK_IN_CLOSED, VISIT_CLOSED | VISIT | visit_id | visit_id dạng chữ | actor = `actor_account_id` |
| `visit_assignment_audits` | VISIT | STARTED, EXTENDED, COMPLETED, CAPACITY_CONFLICT | VISIT_ASSIGNMENT | `COALESCE(assignment_id, visit_id)` | visit_id dạng chữ | before/after = `{expectedEnd}`; metadata = `{stepCode}` |
| `walk_in_reception_queue_history` | VISIT | WAITING khi tạo, CARE_STARTED / CANCELLED / VISIT_CLOSED khi thoát | VISIT | visit_id | visit_id dạng chữ | metadata = `{queueEntryId, fromStatus, toStatus}` |

Category là VISIT, không phải QUEUE như plan.md mục 4 ghi. Lý do: phase 01 và view hiện tại đều dùng VISIT, nên nhật ký hiện y như cũ. Xem câu hỏi 2.

Care audit ở `ManageVisitCareAssignmentServiceImpl` (7 chỗ `audits.append` và `audits.appendConflict`) và `VisitWorkflowServiceImpl.appendCareAudit` đổi sang `VisitAuditEntries.careAssignment(…)`. Hàm `appendConflict` cũ dùng `on conflict do nothing`, xem câu hỏi 3.

## Related Code Files
Đường dẫn tương đối, đã kiểm trên `origin/product` (21e304cd): production là `backend/src/main/java/fu/fa26/sep490/g58/otcms_be/`, test là `backend/src/test/java/fu/fa26/sep490/g58/otcms_be/`, migration là `backend/src/main/resources/db/`.

### Create
- `visit/model/entity/QueueEntryJpaEntity.java`: entity duy nhất cho `queue_entries`, cả hai stage. Factory `reception(visitId, arrivedAt, enteredAt)`, `pendingPayment(…)`, `waiting(…)` và các mutator cũ của hàng khám.
- `visit/mapper/VisitAuditEntries.java`: static factory cho mục 4.8.

### Delete
- `visit/model/entity/VisitInitialReceptionJpaEntity.java`, `visit/repository/VisitInitialReceptionJpaRepository.java`.
- `visit/model/entity/WalkInReceptionReplayJpaEntity.java`, `visit/repository/WalkInReceptionReplayJpaRepository.java`.
- `visit/model/entity/WalkInReceptionQueueEntryJpaEntity.java` (thay bằng `QueueEntryJpaEntity`).
- `visit/model/entity/WalkInReceptionQueueHistoryJpaEntity.java`, `visit/repository/WalkInReceptionQueueHistoryJpaRepository.java`.
- `visit/model/entity/VisitExamQueueJpaEntity.java` (thay bằng `QueueEntryJpaEntity`).
- `visit/model/entity/VisitAuditJpaEntity.java`, `visit/repository/VisitAuditJpaRepository.java`.
- `visit/repository/VisitCareAssignmentAuditRepository.java`.

### Modify: entity và repository
- `visit/model/entity/ClinicVisitJpaEntity.java`: thêm 2 field mapped (`walkInIdempotencyKey`, `walkInRequestFingerprint`) và getter. `walkIn(…)` nhận thêm 2 tham số. Phase 03 sẽ sửa cùng file, merge sau.
- `visit/repository/ClinicVisitJpaRepository.java`: thêm `findByCreatedByAccountIdAndWalkInIdempotencyKey(UUID, String)`.
- `visit/repository/WalkInReceptionQueueEntryJpaRepository.java`: đổi sang `JpaRepository<QueueEntryJpaEntity, UUID>`. Bỏ `setAuditActor`, `setAuditCorrelation`. Đổi `findWaitingEntryIdsForUpdate` thành `findWaitingReceptionIdsForUpdate`, và `leaveReception` theo mục 4.6 C.
- `visit/repository/VisitExamQueueJpaRepository.java`: đổi sang `JpaRepository<QueueEntryJpaEntity, UUID>`. ROW_COLUMNS, derived method và `findMaxQueueNumber` theo mục 4.6 D.
- `visit/repository/VisitWorkflowRepository.java`: `insertCheckInVisit` bỏ insert `visit_check_ins`. `hasConsistentCheckInEvidence` theo 4.6 A. Bỏ `appendCareAudit`.
- `visit/repository/VisitReceptionReadRepository.java`: 4 chỗ SQL (mục 4.6 hàng lễ tân).

### Modify: service
- `visit/service/impl/CreateWalkInVisitServiceImpl.java`: mục 4.6 B và 4.7. Bỏ `receptions`, `audits`, `replays`. Thêm `AuditLogWriter`, `ReceptionistAccountAccessService`.
- `visit/service/impl/ExitWalkInReceptionQueueServiceImpl.java`: mục 4.6 C.
- `visit/service/impl/WalkInReceptionQueueServiceImpl.java`: bỏ `setAuditActor`, `setAuditCorrelation` (dòng 63–64). Dòng 70 đổi tên constraint sang `uq_queue_entries_reception_active_visit`. Ghi `queueEntered` sau khi tạo.
- `visit/service/impl/WalkInVisitLifecycleServiceImpl.java`: `closeAndRecord` (dòng 235–239) gọi `AuditLogWriter` với `VisitAuditEntries.walkInCancelled / walkInClosed / visitClosed`. Bỏ `VisitAuditJpaRepository audits`.
- `visit/service/impl/ManageVisitCareAssignmentServiceImpl.java`: 7 chỗ `audits.append` và `audits.appendConflict` (dòng 96, 128, 141, 171, 196, 214, 248). Bỏ `VisitCareAssignmentAuditRepository`.
- `visit/service/impl/VisitWorkflowServiceImpl.java`: `appendCareAudit` (dòng 145–160) gọi `AuditLogWriter`.
- `visit/service/impl/VisitExamQueueServiceImpl.java`: đổi các lời gọi repository và entity (mục 4.6 D). Giữ nguyên interface `VisitExamQueueService`.
- `visit/service/impl/VisitExamQueueReadServiceImpl.java`: kiểm lại, chỉ đổi nếu gọi method repository đã đổi tên.
- `visit/mapper/ExamQueueItemMapper.java`: kiểm, không đổi nếu thứ tự cột giữ nguyên.

### Modify: migration
- `backend/src/main/resources/db/migration-staged/V115__final_db_visits_queue.sql` chuyển (`git mv`) sang `db/migration/`. Không sửa V001–V108 hay V109–V114.

### Modify: test
Tìm dòng cần sửa bằng `grep -n "visit_check_ins\|walk_in_requests\|walk_in_reception_queue\|visit_exam_queue\|visit_audits\|visit_assignment_audits\|visit_examinations\|VisitExamQueueJpaEntity\|VisitAuditJpaEntity\|WalkInReception\|VisitInitialReception" <file>`.

Integration test có SQL trên bảng bị bỏ. Sửa sang `visits`, `queue_entries` hoặc `audit_logs`, giữ ý nghĩa assert:
- `appointment/AppointmentArrivalT053PostgresIntegrationTest.java`, `appointment/AppointmentLifecycleT029PostgresIntegrationTest.java` (xoá 2 câu `delete from visit_examinations`, dòng 115 và 128), `appointment/AppointmentT015PostgresIntegrationTest.java`, `appointment/CheckInAppointmentT027PostgresIntegrationTest.java`, `appointment/CreateFollowUpAppointmentT030PostgresIntegrationTest.java` (xoá câu delete dòng 91), `appointment/MarkAppointmentNoShowT028PostgresIntegrationTest.java`, `appointment/MarkNoShowAfterShiftJobPostgresIntegrationTest.java`, `appointment/SlotAvailabilityT018PostgresIntegrationTest.java`.
- `persistence/T017PersistenceCheckpointPostgresIntegrationTest.java`, `persistence/T032AdminResourceMutationPostgresIntegrationTest.java`.
- `visit/CreateWalkInVisitT043PostgresIntegrationTest.java`, `visit/VisitCareCapacityT031PostgresIntegrationTest.java`, `visit/VisitExamFlowPostgresIntegrationTest.java`, `visit/VisitExamQueuePostgresIntegrationTest.java`, `visit/VisitReceptionReadT049PostgresIntegrationTest.java`, `visit/VisitT012PostgresIntegrationTest.java` (xoá câu delete dòng 36), `visit/WalkInFeeAdmissionPostgresIntegrationTest.java`, `visit/WalkInReceptionQueueT045PostgresIntegrationTest.java`.
- `visit/WalkInQueueT048MigrationPostgresIntegrationTest.java`: migration dừng ở V102, giữ nếu vẫn xanh. Nếu đỏ thì `@Disabled` theo agent-rules 6.5.
- Không sửa: `clinical/DoctorCoreArchitectureTest.java`. Test này chỉ kiểm chuỗi `visit_examinations` không xuất hiện trong mã nguồn, vẫn đúng sau khi bỏ bảng.

Unit test dựng service hoặc mock repository có class đã đổi hoặc bị xoá (sửa constructor, thêm mock `AuditLogWriter`):
- `visit/CreateWalkInVisitT043Test.java`, `visit/VisitCareCapacityT031Test.java`, `visit/VisitClinicalEvidenceRepairTest.java`, `visit/VisitClosureServiceTest.java`, `visit/VisitExamQueueServiceImplTest.java`, `visit/VisitExamQueueReadServiceImplTest.java`, `visit/VisitWorkflowCheckInServiceImplTest.java`, `visit/WalkInVisitDoctorCompletionServiceTest.java`, `visit/VisitClinicalCareContextServiceImplTest.java`, `visit/VisitPatientHistoryQueryServiceImplTest.java`, `clinical/VisitClinicalContextProductionWiringTest.java`, `clinical/VisitCompletionProductionWiringTest.java`, `clinical/DoctorCoreProductionWiringTest.java`, `clinical/DoctorImagingReviewIntegrationTest.java` (kiểm: chỉ sửa nếu tham chiếu class đã đổi), `visit/VisitDiagnosticArchitectureTest.java` (kiểm: chỉ sửa nếu kiểm tên class đã bỏ).

Test mới (agent-rules 6.3), thêm vào file có sẵn:
- `CreateWalkInVisitT043PostgresIntegrationTest`: `duplicateWalkInWithSameKeyReturnsSameVisit` (gửi hai lần, cùng `visitId`, một dòng `visits`) và `sameKeyFromAnotherReceptionistCreatesOwnVisit` (lễ tân khác dùng cùng khoá, có visit riêng).
- `VisitReceptionReadT049PostgresIntegrationTest`: `receptionQueueIgnoresExamRowsWithWaitingStatus` (có một hàng khám `WAITING` cùng visit, hàng lễ tân không bị đếm nhầm).

## Implementation Steps
1. Kiểm phase 01, 02, 04 đã vào `final-db`. Kiểm `AuditLogWriter` có hỗ trợ insert bỏ qua trùng (xem câu hỏi 3). Tạo nhánh `final-db-p05-visits-queue` từ `final-db` mới nhất.
2. Đọc `migration-staged/V115__final_db_visits_queue.sql` và đối chiếu mục 4.2 đến 4.5. Nếu lệch thì sửa, ghi "Đổi migration".
3. Trước khi sửa Java: `git grep -n "walk_in_reception_queue\|visit_exam_queue\|visit_check_ins\|walk_in_requests\|visit_audits\|visit_assignment_audits" -- backend/src/main/java` để ghi lại số dòng cần đổi.
4. `git mv` V115 sang `db/migration/`. Reset Docker. Chạy app profile `final-db`: Flyway tới V115, app khởi động được (`ddl-auto: validate`).
5. Tạo `QueueEntryJpaEntity` và `VisitAuditEntries`. Sửa `ClinicVisitJpaEntity` và `ClinicVisitJpaRepository`.
6. Sửa repository và service theo mục 4.6, 4.7 và 4.8. Xoá các file ở mục Delete.
7. Viết lại tài khoản kiểm tra (4.7) và đổi tên constraint.
8. Chạy grep ở Success Criteria. Sửa tới khi rỗng.
9. Sửa test theo danh sách. Thêm 4 test mới.
10. `./mvnw test` trong `otcms/backend`. Báo số test chạy và số lỗi.
11. Chạy app, so OpenAPI bằng `tools/compare-openapi.mjs` như phase 04.
12. Dán kết quả vào PR theo mẫu agent-rules mục 8.

## Success Criteria
- [ ] `grep -rn "visit_check_ins\|walk_in_requests\|walk_in_reception_queue_entries\|walk_in_reception_queue_history\|visit_exam_queue\|visit_audits\|visit_assignment_audits\|visit_examinations" backend/src/main/java` rỗng.
- [ ] `grep -rn "VisitInitialReception\|WalkInReceptionReplay\|WalkInReceptionQueueEntryJpaEntity\|WalkInReceptionQueueHistory\|VisitExamQueueJpaEntity\|VisitAuditJpaEntity\|VisitAuditJpaRepository\|VisitCareAssignmentAuditRepository\|setAuditActor\|setAuditCorrelation" backend/src/main/java` rỗng.
- [ ] `grep -rn "uk_walk_in_reception\|uk_visit_exam_queue\|uk_visit_care_assignment" backend/src/main/java` rỗng.
- [ ] `grep -rn "queue_entries" backend/src/main/java` chỉ ra các dòng đều có `stage =`. Kiểm bằng mắt.
- [ ] `grep -rln "visit_examinations\|visit_check_ins\|visit_exam_queue\|walk_in_reception_queue\|walk_in_requests\|visit_audits\|visit_assignment_audits" backend/src/test` chỉ còn `DoctorCoreArchitectureTest`.
- [ ] Truy vấn `pg_proc` ở mục 4.4 rỗng.
- [ ] Trên dữ liệu demo sau V115: số dòng `audit_logs` theo từng `source` khớp số dòng bảng cũ đo trước khi chạy (ghi PR).
- [ ] `./mvnw test`: chỉ còn lỗi có sẵn theo AGENTS.md.
- [ ] Hai test mới của walk-in và một test mới của hàng lễ tân đạt.
- [ ] So OpenAPI: no differences.
- [ ] Đi tay: lễ tân tạo walk-in, vào hàng, khám bắt đầu, hàng khám hiển thị đúng; nhật ký admin có dòng `queue:` và `care:` như cũ.

## Risk Assessment
- Lẫn hai hàng chờ do thiếu `stage`: mối nguy lớn nhất. Chặn bằng grep và test hàng lễ tân (mục Test).
- Trigger viết lại sai làm mất bất biến: ví dụ đổi `source` của visit đã có hàng chờ. Chặn bằng test `protect…` hiện có và kiểm `ct_clinic_visits_appointment_link`.
- Mất khoá `FOR SHARE` trên tài khoản (trigger cũ). Chấp nhận, vì guard tuần tự hoá các thao tác.
- `appendConflict` dùng `on conflict do nothing`. Nếu `AuditLogWriter` không hỗ trợ insert bỏ qua trùng thì DỪNG (câu hỏi 3). Không tự thêm SQL riêng vào `common/audit`.
- Phase 03 sửa cùng `ClinicVisitJpaEntity` và entity `Visit`: merge sau. Xung đột thì giữ dòng của phase 05 cho cột walk-in và giữ dòng của 03 cho cột imaging.
- Ghi chéo: `visit` đọc và ghi cột trên `appointments` (phase 04). Đã nêu ở phase 04.
- Hàm dùng `CREATE OR REPLACE` nhưng sai chữ ký gây lỗi lúc chạy, không lỗi lúc migrate. Chặn bằng test từng luồng.
- Thay đổi tên method repository kéo theo nhiều file test. Đếm trước khi làm (mục 3 của Implementation Steps).

## Câu hỏi còn mở

> **ĐÃ CHỐT 13/11 (tiêu chí: giữ API + hành vi cũ):** UNIQUE ghép (người thao tác, khoá) để giữ phạm vi cũ. Giữ `LEGACY_RECEPTIONIST` trong CHECK. Các câu có "đề xuất/mặc định" trong file: làm theo đề xuất (không thêm `reception_type`/`receptionist_account_id`, giữ `arrived_at`, bỏ `enforce_initial_reception_type` và các hàm check-in evidence, QR check-in giữ actor_type cũ). Nhật ký hàng chờ dùng category `VISIT` (theo phase 01; plan.md đã sửa). Conflict "on conflict do nothing" dùng `AuditLogWriter.recordIfAbsent` + `dedupKey` (phase 01 đã thêm). Khoá check-in FE gửi gì: agent kiểm `frontend/src` lúc làm; không phải UUID thì giữ kiểu cột theo FE, không đổi FE.

Các câu bên dưới giữ lại để tra lý do.
1. Không thêm `reception_type` và `receptionist_account_id` (schema-33 ghi có). Lý do: suy ra từ `source` và `created_by_account_id`, đã kiểm trong code. Xác nhận.
2. Category của lịch sử hàng chờ: plan.md mục 4 ghi `QUEUE`. Phase 01 và view hiện tại ghi `VISIT`. Phase này dùng `VISIT` để nhật ký hiện y như cũ. Xác nhận.
3. `appendConflict` cần insert bỏ qua trùng (`on conflict do nothing`) qua `AuditLogWriter`. Nếu phase 01 chưa có hàm này thì cần thêm vào `common/audit`. File đó thuộc phase 01 của NghiaDT, nên hỏi trước khi sửa.
4. Giữ `arrived_at` trên `queue_entries` (schema-33 không liệt kê). FIFO và vị trí hàng đợi đọc theo cột này.
5. Mã khoá walk-in là UNIQUE ghép (người tạo, khoá), không phải UNIQUE đơn như brief. Cùng câu hỏi với phase 04. Xác nhận.
6. Phụ thuộc: plan.md ghi phase 05 phụ thuộc 01 và "merge sau 04". Phase này thêm phụ thuộc vào 02, vì FK `doctor_profile_id` trỏ tới `staff_profiles`.
7. Bỏ hẳn `enforce_initial_reception_type` và các hàm check-in evidence, vì điều kiện đã nằm ở CHECK và trigger của `visits`. Xác nhận.
