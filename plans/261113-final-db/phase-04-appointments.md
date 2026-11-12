---
phase: 4
title: "Lịch hẹn: gộp arrivals và check-in requests vào appointments, audit sang audit_logs"
status: pending
priority: P1
effort: "3h"
owner: Long
dependencies: [1]
migration: V114__final_db_appointments.sql
---

# Phase 04: Lịch hẹn (appointments)

## Overview
Gộp ba bảng vào `appointments` và `audit_logs`:
- `appointment_arrivals` thành 5 cột `arrival_*` trên `appointments`. "Ai đến trước thắng" thành `UPDATE … WHERE arrived_at IS NULL`.
- `appointment_checkin_requests` thành 4 cột `checkin_*` trên `appointments`. Chống bấm trùng bằng UNIQUE và bắt lỗi trùng.
- `appointment_audits` thành `audit_logs` qua `AuditLogWriter` (`source = 'appointment_audits'`).

Không đổi: `EXCLUDE ex_appointments_doctor_occupied_range`, giữ giờ `AWAITING_PAYMENT` 15 phút, trigger `tr_appointments_occupied_until`, toàn bộ API.

Ranh giới với phase khác:
- Phase 02 (NghiaDT) làm nghỉ phép bác sĩ và gộp `doctor_profiles` vào `staff_profiles`. Long KHÔNG sửa phần đó. Rebase sau khi 02 vào `final-db`. Xung đột ở chỗ gọi `DoctorProfile…` thì giữ bản của 02.
- Phase 05 (Long) xử lý `visit_check_ins` và trigger `enforce_appointment_visit_link`. Phase này để nguyên phần đó, vì 05 merge sau 04.
- Phase 07 (Hùng) xử lý `room_reservations` và `room_reservation_audits`. Phase này không đụng tới.

## Requirements
- API giữ nguyên: URL, body, mã HTTP, mã MSG. Endpoint chạm tới: `POST /api/v1/appointments`, `POST /api/v1/visits/{visitId}/follow-up-appointments`, `PATCH /api/v1/appointments/{id}/status`, `POST /api/v1/appointments/{id}/cancel`, `PATCH /api/v1/appointments/{id}/reschedule`, `POST /api/v1/appointments/{id}/reassign`, `POST /api/v1/appointments/pending-hold/cancel`, `POST /api/v1/appointments/{id}/check-in`, `POST /api/v1/appointments/{appointmentId}/arrivals`, `POST /api/v1/appointments/{appointmentId}/arrivals/manual`, `GET /api/v1/appointment-arrivals`, và (gián tiếp qua nhật ký) `GET /api/v1/admin/activity-logs`.
- Check-in gửi trùng (cùng người thao tác, cùng khoá, cùng lịch): trả đúng `visitId` và `checkedInAt` đã lưu, không tạo visit thứ hai.
- Cùng khoá nhưng fingerprint khác: `IDEMPOTENCY_CONFLICT` như cũ.
- Arrival: lần ghi đầu thắng. Lần sau trả bản đã ghi (giữ hành vi `on conflict do nothing` cũ).
- Mọi thay đổi trạng thái lịch ghi đúng một dòng `audit_logs` trong cùng transaction.

## Architecture

### 4.1 Cột mới trên `appointments` (V114)
| Cột | Kiểu | Ràng buộc | Lấy từ (bảng cũ.cột) |
|---|---|---|---|
| `arrived_at` | timestamptz NULL | đi kèm 4 cột arrival | appointment_arrivals.arrived_at |
| `arrival_source` | varchar(24) NULL | IN ('PATIENT_QR','RECEPTIONIST','LEGACY_RECEPTIONIST') | .source |
| `arrival_recorded_at` | timestamptz NULL | | .recorded_at |
| `arrival_recorded_by` | uuid NULL | FK accounts(id) ON DELETE RESTRICT; có index | .actor_account_id |
| `arrival_reason` | varchar(500) NULL | | .reason |
| `checkin_idempotency_key` | varchar(128) NULL | length(trim) 1..128; UNIQUE ghép (`checkin_actor_account_id`, `checkin_idempotency_key`) | appointment_checkin_requests.idempotency_key |
| `checkin_request_fingerprint` | varchar(64) NULL | khớp `^[0-9a-fA-F]{64}$` | .request_fingerprint |
| `checkin_actor_account_id` | uuid NULL | FK accounts(id) ON DELETE RESTRICT | .actor_account_id |
| `checkin_checked_in_at` | timestamptz NULL | | .result_checked_in_at |

Không thêm cột `result_visit_id` vì `appointments.visit_id` đã là visit kết quả (FK cũ `fk_appointment_replay_appointment_visit` bắt hai giá trị này bằng nhau). Không thêm `patient_profile_id` của arrival vì đã có `appointments.patient_profile_id` (FK cũ `fk_recorded_arrivals_appointment_patient`).

CHECK (đặt tên `ck_appointments_…`):
- `ck_appointments_arrival_all_or_none`: bốn cột `arrived_at`, `arrival_source`, `arrival_recorded_at`, `arrival_recorded_by` cùng NULL hoặc cùng có giá trị.
- `ck_appointments_arrival_reason`: `RECEPTIONIST` thì `arrival_reason` 1..500 ký tự. `PATIENT_QR` và `LEGACY_RECEPTIONIST` thì `arrival_reason` NULL. Copy từ `ck_appointment_arrivals_actor_reason`.
- `ck_appointments_checkin_all_or_none`: bốn cột `checkin_*` cùng NULL hoặc cùng có giá trị.
- `ck_appointments_checkin_fingerprint`: NULL hoặc khớp regex ở trên.

Index:
- `ix_appointments_arrived_at (arrived_at, id) WHERE arrived_at IS NOT NULL` (thay `ix_appointment_arrivals_arrived`).
- `ix_appointments_arrival_recorded_by (arrival_recorded_by) WHERE arrival_recorded_by IS NOT NULL`. Cột FK mới phải có index (schema-33, mục 4b).
- `checkin_actor_account_id` đứng đầu UNIQUE nên không cần index riêng.

### 4.2 Thứ tự trong V114
1. `ALTER TABLE appointments ADD COLUMN …` (tất cả nullable) và tạo index.
2. Chép dữ liệu demo:
   - Arrivals: `UPDATE appointments a SET arrived_at = r.arrived_at, … FROM appointment_arrivals r WHERE r.appointment_id = a.id`.
   - Check-in: một dòng mỗi lịch, `SELECT DISTINCT ON (appointment_id) … FROM appointment_checkin_requests WHERE command = 'CHECK_IN' ORDER BY appointment_id, created_at`.
   - Audit: `INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label, visible_in_activity_log, actor_account_id, actor_type, before_state, after_state, metadata, correlation_id) SELECT a.id, a.occurred_at, 'appointment_audits', 'APPOINTMENT', a.action, 'APPOINTMENT', a.appointment_id, a.appointment_id::text, true, a.actor_account_id, a.actor_type, …` theo mục 4.4. Giữ `a.id` để mã dòng nhật ký không đổi.
3. Thêm CHECK, UNIQUE, FK, index. Nếu dữ liệu chép vi phạm CHECK thì DỪNG và báo. Không nới CHECK.
4. `DROP FUNCTION reject_append_only_appointment_row_mutation();` (chỉ hai trigger của hai bảng sắp bỏ dùng hàm này).
5. `DROP TABLE appointment_checkin_requests; DROP TABLE appointment_arrivals; DROP TABLE appointment_audits;`. Không dùng `IF EXISTS`.

Trước khi DROP, chạy truy vấn sau. Kết quả phải rỗng, ghi vào PR:
`SELECT proname FROM pg_proc WHERE prosrc ILIKE '%appointment_audits%' OR prosrc ILIKE '%appointment_arrivals%' OR prosrc ILIKE '%appointment_checkin_requests%';`

View `activity_log_entries` đã đọc `audit_logs` từ phase 01. Nếu DROP báo còn đối tượng phụ thuộc thì phase 01 chưa đúng: dừng và báo.

### 4.3 Trigger và hàm
| Đối tượng | Xử lý |
|---|---|
| `tr_appointment_audits_immutable`, `tr_appointment_command_replays_immutable` | bỏ cùng bảng |
| `reject_append_only_appointment_row_mutation()` | DROP FUNCTION |
| `ck_appointment_audits_checkin_attestation` | bỏ. Điều kiện "check-in phải có `verifiedPatientProfileId`" đã được `CheckInAppointmentTransaction` kiểm (`VALIDATION_FAILED`) và ghi vào `metadata` |
| `ct_appointments_visit_link` → `enforce_appointment_visit_link()` | giữ nguyên ở phase này (phase 05 viết lại) |
| `tr_appointments_occupied_until`, `ex_appointments_doctor_occupied_range`, `ck_appointments_payment_deadline`, `ix_appointments_awaiting_deadline` | giữ nguyên |

### 4.4 Ánh xạ audit sang `AuditLogWriter`
Quy ước cho mọi dòng `appointment_audits`:
- `source = 'appointment_audits'`, `category = 'APPOINTMENT'`, `record_type = 'APPOINTMENT'`, `entity_id = appointmentId`, `record_label = appointmentId.toString()`, `visible_in_activity_log = true`, `correction_reason = null` (view cũ trả NULL nên hiển thị không đổi).
- `before_state = {status, doctorProfileId, startTime, endTime}` (null khi CREATED). `after_state = {status, doctorProfileId, startTime, endTime, visitId, sourceVisitId}`. `metadata = {reason, verifiedPatientProfileId}`, bỏ key có giá trị null.
- `after_state->>'status'` thay cho cột `new_status` cũ.

| # | Chỗ ghi cũ (repository → nơi gọi) | action | actor_type | status cũ → mới |
|---|---|---|---|---|
| 1 | `insertCreationAudit` → `CreateAppointmentServiceImpl` (`save`) | CREATED | `booking.actorType().name()` | — → `initialStatus` |
| 2 | `insertFollowUpCreationAudit` → `CreateFollowUpAppointmentServiceImpl` (`saveFollowUp`) | CREATED | DOCTOR | — → CONFIRMED; có `sourceVisitId`, `reason` |
| 3 | `insertCancellationAudit` → `CancelAppointmentServiceImpl.cancel` | CANCELLED | `command.actor().name()` | CONFIRMED → CANCELLED (giữ CONFIRMED cố định như SQL cũ) |
| 4 | `insertLifecycleAudit` → `transition` (`CancelAppointmentServiceImpl` dòng 155, `UpdateAppointmentStatusServiceImpl` dòng 92) | CANCELLED nếu target là CANCELLED, còn lại STATUS_CHANGED | tham số `actorType` | status hiện tại → target |
| 5 | `insertNoShowAudit` → `MarkAppointmentNoShowTransaction` (dòng 78), `MarkNoShowAfterShiftTransaction` (dòng 58) | STATUS_CHANGED | RECEPTIONIST hoặc SYSTEM | CONFIRMED → NO_SHOW |
| 6 | `insertStatusAudit` → `AppointmentHoldExpiryTransaction` (dòng 37) | STATUS_CHANGED | `actor.actorType()` | AWAITING_PAYMENT → PAYMENT_EXPIRED |
| 7 | `insertStatusAudit` → `ConfirmAppointmentPaymentServiceImpl` (dòng 51) | STATUS_CHANGED | SYSTEM | AWAITING_PAYMENT → CONFIRMED |
| 8 | `insertReassignmentAudit` → `ReassignAppointmentServiceImpl` (dòng 83) | DOCTOR_REASSIGNED | kết quả `findAccountType(actor)` | bác sĩ cũ → bác sĩ mới |
| 9 | `insertRescheduleAudit` → `RescheduleAppointmentServiceImpl` (dòng 98) | RESCHEDULED | tham số `actorType` | start/end cũ → mới |
| 10 | `insertCheckInAudit` → `CheckInAppointmentTransaction` (dòng 115) | STATUS_CHANGED | RECEPTIONIST (cố định như SQL cũ) | CONFIRMED → CHECKED_IN; `visitId` vào `after_state` |

Dòng 8: `findAccountType` giữ subselect đọc `accounts.account_type` như cũ. Đây là đọc chéo đã có, nêu cho NghiaDT duyệt.

Helper: `appointment/mapper/AppointmentAuditEntries.java`, mỗi dòng bảng trên một hàm static. Dùng `LinkedHashMap` cho state (`Map.of` không nhận null).

### 4.5 Java: trước → sau (bốn chỗ khó nhất)

(A) Check-in, `CheckInAppointmentTransaction.checkIn`. Trước:
```java
appointments.completeCheckIn(current, command.actorAccountId(), command.verifiedPatientProfileId(),
        key, requestFingerprint, receptionResult.visitId(), checkedInAt, command.correlationId());
```
`completeCheckIn` cũ ghi `appointments`, `appointment_audits` và `appointment_checkin_requests`. Sau:
```java
try {
    appointments.completeCheckIn(current, command.actorAccountId(), key, requestFingerprint,
            receptionResult.visitId(), checkedInAt);
} catch (DataIntegrityViolationException duplicateKey) {
    // Khoá (người thao tác, khoá) đã gắn với lịch khác: không trả kết quả của lịch khác.
    throw new AppointmentBookingException("IDEMPOTENCY_CONFLICT", duplicateKey);
}
auditLogWriter.record(AppointmentAuditEntries.checkedIn(current, command.actorAccountId(),
        command.verifiedPatientProfileId(), receptionResult.visitId(), checkedInAt, command.correlationId()));
```
Replay ở đầu hàm vẫn gọi `appointments.findReplay(actor, appointmentId, key)`. `findCheckInReplayRows` đọc từ `appointments`:
`select checkin_request_fingerprint, visit_id, checkin_checked_in_at from appointments where id = :appointment and checkin_actor_account_id = :actor and checkin_idempotency_key = :key`.
Không SELECT lại trong cùng transaction sau khi bắt `DataIntegrityViolationException`, vì Postgres đã abort transaction. Exception ném ra làm rollback nên không có vấn đề.

(B) SQL ghi trạng thái, `AppointmentCheckInRepository.markCheckedIn`: thêm 4 cột vào SET của câu UPDATE cũ:
`… set status = 'CHECKED_IN', visit_id = :visit, updated_at = :updated, checkin_idempotency_key = :key, checkin_request_fingerprint = :fingerprint, checkin_actor_account_id = :actor, checkin_checked_in_at = :checkedInAt where id = :appointment and patient_profile_id = :patient and status = 'CONFIRMED' and visit_id is null`.
Kết quả khác 1 dòng vẫn ném `IllegalStateException` như cũ.

(C) Arrival, first-wins, `visit/repository/AppointmentArrivalRepository.claimIfUnrecorded`:
`update appointments set arrived_at = :arrivedAt, arrival_source = :source, arrival_reason = cast(:reason as varchar), arrival_recorded_by = :actor, arrival_recorded_at = clock_timestamp() where id = :id and patient_profile_id = :patient and arrived_at is null`.
`AppointmentArrivalClaimStoreImpl.record`: nếu trả 0 dòng thì gọi `find(id)`. Không có thì ném `ARRIVAL_RECORD_UNAVAILABLE`. Khác patient thì ném `ARRIVAL_RECORD_CONFLICT`. Hành vi cũ: `insertIfAbsent` rồi `findById`.

(D) `AppointmentOccupancyRepository.findVisitLinkEvidence`, subselect `completed_at`. Trước:
`(select max(aa.occurred_at) from appointment_audits aa where aa.appointment_id = a.id and aa.action = 'STATUS_CHANGED' and aa.new_status = 'COMPLETED')`.
Sau:
`(select max(al.occurred_at) from audit_logs al where al.source = 'appointment_audits' and al.entity_id = a.id and al.action = 'STATUS_CHANGED' and al.after_state->>'status' = 'COMPLETED')`.

### 4.6 Chống gửi trùng check-in
- Khoá là UNIQUE ghép `(checkin_actor_account_id, checkin_idempotency_key)`, không phải UNIQUE đơn. Lý do: giữ phạm vi cũ theo người thao tác, nên hai lễ tân dùng cùng chuỗi khoá không ảnh hưởng nhau.
- Chỗ khác cũ: cùng người, cùng khoá, khác lịch thì trả `IDEMPOTENCY_CONFLICT`. Cũ thì tạo check-in mới. Khoá đến từ header `Idempotency-Key` (`AppointmentBookingController`). Luồng khác của FE dùng `crypto.randomUUID()` nên trường hợp này không xảy ra khi client đúng. Chưa xác minh luồng check-in của FE (xem câu hỏi 5).
- Không còn bảng replay. Replay đọc cột của chính lịch hẹn.

## Related Code Files
Đường dẫn tương đối, đã kiểm trên `origin/product` (21e304cd): production là `backend/src/main/java/fu/fa26/sep490/g58/otcms_be/`, test là `backend/src/test/java/fu/fa26/sep490/g58/otcms_be/`, migration là `backend/src/main/resources/db/`.

### Create
- `appointment/mapper/AppointmentAuditEntries.java`: static factory cho 10 dòng ở mục 4.4. Dùng `AuditEntry.builder(...)` và các setter đúng như `common/audit/AuditEntry.java` do phase 01 tạo. Nếu Builder thiếu setter cần dùng thì DỪNG và hỏi, không tự thêm.

### Delete
- `appointment/model/entity/AppointmentAuditJpaEntity.java` (gồm cả `created(...)`, không ai gọi).
- `appointment/repository/AppointmentAuditJpaRepository.java`.
- `visit/model/entity/AppointmentArrivalJpaEntity.java`.

### Modify: appointment (repository)
- `appointment/repository/AppointmentCheckInRepository.java`: bỏ `insertCheckInAudit`, `insertCheckInReplay`. Sửa `findCheckInReplayRows`, `markCheckedIn`. `completeCheckIn(current, actor, key, fingerprint, visitId, checkedInAt)` chỉ gọi `markCheckedIn`.
- `appointment/repository/AppointmentCancellationRepository.java`: bỏ `insertCancellationAudit`. `cancel` chỉ UPDATE rồi đọc lại.
- `appointment/repository/AppointmentJpaRepository.java`: bỏ `insertCreationAudit`, `insertFollowUpCreationAudit`. `save`, `saveFollowUp` chỉ `saveAndFlush` và đọc lại.
- `appointment/repository/AppointmentLifecycleRepository.java`: bỏ `insertLifecycleAudit`. `transition` chỉ UPDATE.
- `appointment/repository/AppointmentNoShowRepository.java`: bỏ `insertNoShowAudit`. `markNoShow` chỉ UPDATE.
- `appointment/repository/AppointmentPaymentHoldRepository.java`: bỏ `insertStatusAudit`.
- `appointment/repository/AppointmentReassignmentRepository.java`: bỏ `insertReassignmentAudit`. Thêm `findAccountType(UUID actor)`.
- `appointment/repository/AppointmentRescheduleRepository.java`: bỏ `insertRescheduleAudit`.
- `appointment/repository/AppointmentOccupancyRepository.java`: `findVisitLinkEvidence` (mục 4.5 D).

### Modify: appointment (service). Gọi `AuditLogWriter` trong cùng transaction. Mười hàm này đều đã có `@Transactional`.
- `appointment/service/impl/CheckInAppointmentTransaction.java`: mục 4.5 A, thêm `AuditLogWriter` vào constructor.
- `appointment/service/impl/CancelAppointmentServiceImpl.java`: dòng 155 (transition) và 169 (cancel).
- `appointment/service/impl/CreateAppointmentServiceImpl.java`: dòng 182.
- `appointment/service/impl/CreateFollowUpAppointmentServiceImpl.java`: dòng 148.
- `appointment/service/impl/UpdateAppointmentStatusServiceImpl.java`: dòng 92.
- `appointment/service/impl/MarkAppointmentNoShowTransaction.java`: dòng 78.
- `appointment/service/impl/MarkNoShowAfterShiftTransaction.java`: dòng 58.
- `appointment/service/impl/ReassignAppointmentServiceImpl.java`: dòng 83, dùng `findAccountType`.
- `appointment/service/impl/RescheduleAppointmentServiceImpl.java`: dòng 98.
- `appointment/service/impl/AppointmentHoldExpiryTransaction.java`: dòng 37.
- `appointment/service/impl/ConfirmAppointmentPaymentServiceImpl.java`: dòng 51.

### Modify: visit package (ghi chéo, nêu cho NghiaDT duyệt theo agent-rules 6.4)
- `visit/repository/AppointmentArrivalRepository.java`: từ `JpaRepository<AppointmentArrivalJpaEntity>` thành `@Repository` dùng `EntityManager`, cùng kiểu `VisitWorkflowRepository`. Có `claimIfUnrecorded`, `findArrival(id)`, `findArrivals(from, to, offset, limit)` (order by `arrived_at, id`), `findRecordedArrivalEvidence(id, patient, start, end)` (thay truy vấn cũ của `VisitInitialReceptionJpaRepository`).
- `visit/repository/VisitInitialReceptionJpaRepository.java`: bỏ `findRecordedArrivalEvidence`. Phần còn lại xoá ở phase 05.
- `visit/repository/VisitWorkflowRepository.java`: `findRecordedArrival` đọc `appointments`: `select patient_profile_id, arrived_at from appointments where id = :appointmentId and arrived_at is not null`.
- `visit/service/impl/AppointmentArrivalClaimStoreImpl.java`: `record` theo mục 4.5 C. `find` và `list` đọc `Object[]` từ `AppointmentArrivalRepository`. Bỏ import entity cũ.
- `visit/service/impl/RecordedArrivalQueryServiceImpl.java`: inject `AppointmentArrivalRepository` thay `VisitInitialReceptionJpaRepository`.

### Modify: migration
- `backend/src/main/resources/db/migration-staged/V114__final_db_appointments.sql` chuyển (`git mv`) sang `backend/src/main/resources/db/migration/`. Không sửa V001–V108.

### Modify: test
Tìm dòng cần sửa bằng `grep -n "appointment_audits\|appointment_arrivals\|appointment_checkin_requests\|insert.*Audit\|AppointmentAuditJpa\|AppointmentArrivalJpa" <file>`.

Integration test có SQL trên bảng bị bỏ. Sửa sang `appointments` hoặc `audit_logs`, giữ nguyên ý nghĩa assert:
- `appointment/AppointmentArrivalT053PostgresIntegrationTest.java`, `appointment/AppointmentAwaitingPaymentV106PostgresIntegrationTest.java`, `appointment/AppointmentBookingT022PostgresIntegrationTest.java`, `appointment/AppointmentLifecycleT029PostgresIntegrationTest.java`, `appointment/AppointmentPaymentHoldBookingPostgresIntegrationTest.java`, `appointment/AppointmentPaymentHoldLifecyclePostgresIntegrationTest.java`, `appointment/AppointmentT013PostgresIntegrationTest.java`, `appointment/CancelAppointmentT025PostgresIntegrationTest.java`, `appointment/CheckInAppointmentT027PostgresIntegrationTest.java`, `appointment/CreateFollowUpAppointmentT030PostgresIntegrationTest.java`, `appointment/MarkAppointmentNoShowT028PostgresIntegrationTest.java`, `appointment/MarkNoShowAfterShiftJobPostgresIntegrationTest.java`, `appointment/PaymentHoldFixtureSupport.java`, `appointment/ReassignAppointmentT026PostgresIntegrationTest.java`, `appointment/RescheduleAppointmentT024PostgresIntegrationTest.java`, `notification/NotificationPostgresIntegrationTest.java`.
- `visit/CreateWalkInVisitT043PostgresIntegrationTest.java`, `visit/VisitCareCapacityT031PostgresIntegrationTest.java`, `visit/VisitExamFlowPostgresIntegrationTest.java`, `visit/VisitT012PostgresIntegrationTest.java`: chỉ sửa phần `appointment_audits` hoặc `appointment_arrivals`. Phần visit do phase 05 làm.

Unit test dựng service hoặc mock repository có hàm đã bỏ (sửa constructor, thêm mock `AuditLogWriter`):
- `appointment/CheckInAppointmentServiceImplT027Test.java`, `appointment/CheckInAppointmentServiceImplTest.java`, `appointment/CancelAppointmentServiceImplT025Test.java`, `appointment/CreateAppointmentServiceImplTest.java`, `appointment/CreateFollowUpAppointmentT030Test.java`, `appointment/MarkAppointmentNoShowT028Test.java`, `appointment/ReassignAppointmentServiceImplT026Test.java`, `appointment/RescheduleAppointmentServiceImplT024Test.java`, `appointment/UpdateAppointmentStatusT029Test.java`, `appointment/ReleaseAppointmentPaymentHoldServiceImplTest.java`, `appointment/service/impl/MarkNoShowAfterShiftTransactionTest.java`.

Giữ nguyên nếu vẫn xanh (test migration dừng ở V026): `appointment/CheckInAuditT046MigrationPostgresIntegrationTest.java`. Nếu đỏ thì đánh `@Disabled("final-db: appointment_audits đã gộp vào audit_logs")` theo agent-rules 6.5.

Test mới (bắt buộc, agent-rules 6.3), thêm vào file có sẵn, không tạo file mới:
- `CheckInAppointmentT027PostgresIntegrationTest`: `duplicateCheckInWithSameKeyReturnsStoredVisit` (gửi hai lần cùng khoá, nhận cùng `visitId`, một dòng `visits`) và `sameKeyOnAnotherAppointmentIsConflict` (cùng người, khoá dùng lại trên lịch khác, nhận `IDEMPOTENCY_CONFLICT`).
- `AppointmentArrivalT053PostgresIntegrationTest`: `secondArrivalKeepsFirstRecord` (lần 2 trả bản ghi của lần 1, `arrived_at` không đổi).

## Implementation Steps
1. Kiểm phase 01 đã vào `final-db` (có `common/audit/AuditLogWriter`). Nếu phase 02 chưa vào thì chờ, hoặc rebase sau khi 02 vào. Tạo nhánh `final-db-p04-appointments` từ `final-db` mới nhất.
2. Đọc `migration-staged/V114__final_db_appointments.sql` và so với mục 4.1 đến 4.3. Nếu lệch thì sửa trong PR và ghi "Đổi migration".
3. `git mv` V114 sang `db/migration/`. Reset Docker theo agent-rules mục 4. Chạy app với profile `final-db`: Flyway chạy tới V114 và app khởi động được (`ddl-auto: validate`).
4. Tạo `AppointmentAuditEntries`. Thêm `AuditLogWriter` vào 10 chỗ gọi (bảng 4.4). Xoá các hàm `insert…Audit` không còn dùng.
5. Sửa check-in theo mục 4.5 A và B, và `findCheckInReplayRows`.
6. Sửa arrival theo mục 4.5 C, và các file visit ở trên.
7. Sửa `findVisitLinkEvidence` theo mục 4.5 D.
8. Xoá ba file entity và repository ở mục Delete.
9. Chạy các grep ở Success Criteria. Sửa tới khi rỗng.
10. Sửa test theo danh sách. Thêm ba test mới.
11. `./mvnw test` trong `otcms/backend` với env theo `otcms/AGENTS.md`. Báo số test chạy và số lỗi. Lỗi có sẵn theo AGENTS.md.
12. Chạy app, `curl -s http://localhost:8080/v3/api-docs > <file tạm>`, rồi `node plans/261113-final-db/tools/compare-openapi.mjs plans/261113-final-db/api-baseline.json <file tạm>`.
13. Dán kết quả vào PR theo mẫu ở agent-rules mục 8.

## Success Criteria
- [ ] `grep -rn "appointment_audits\|appointment_arrivals\|appointment_checkin_requests" backend/src/main/java` rỗng.
- [ ] `grep -rn "AppointmentAuditJpa\|AppointmentArrivalJpa\|insertCheckInAudit\|insertCheckInReplay\|insertCreationAudit\|insertFollowUpCreationAudit\|insertCancellationAudit\|insertLifecycleAudit\|insertNoShowAudit\|insertStatusAudit\|insertReassignmentAudit\|insertRescheduleAudit" backend/src/main/java` rỗng.
- [ ] `grep -rn "reject_append_only_appointment_row_mutation" backend/src/main/java` rỗng.
- [ ] Truy vấn `pg_proc` ở mục 4.2 rỗng.
- [ ] Sau V114 trên dữ liệu demo: `SELECT count(*) FROM audit_logs WHERE source = 'appointment_audits'` bằng số dòng `appointment_audits` đo trước khi chạy V114 (ghi PR).
- [ ] `./mvnw test`: chỉ còn lỗi có sẵn theo AGENTS.md.
- [ ] Ba test mới đạt. Gửi trùng check-in trả đúng `visitId` cũ.
- [ ] So OpenAPI: no differences.
- [ ] Admin gọi `GET /api/v1/admin/activity-logs` trả 200. Dòng nhật ký lịch hẹn vẫn có id dạng `appointment:<uuid>`.

## Risk Assessment
- Thiếu một chỗ gọi `AuditLogWriter` thì không có exception báo ngay. Chặn bằng bảng 4.4 (10 dòng) và test ở mỗi API.
- Đổi hành vi khoá trùng (mục 4.6): cùng người, cùng khoá, khác lịch thì trả `IDEMPOTENCY_CONFLICT`. Ghi vào PR.
- Postgres abort transaction sau `DataIntegrityViolationException`: không đọc lại trong cùng transaction.
- Ghi chéo: `visit` ghi cột trên `appointments` (mục 4.5 C). `appointment` đọc `accounts.account_type` (bảng 4.4 dòng 8). Đã có từ trước; nêu rõ để NghiaDT duyệt.
- Phase 02 cùng sửa `AppointmentJpaEntity` và các chỗ gọi `DoctorProfile…`: rebase sau 02. Xung đột thì giữ dòng của 02 ở phần `DoctorProfile`.
- Dữ liệu demo vi phạm CHECK mới: DỪNG và báo, không nới CHECK.

## Câu hỏi còn mở

> **ĐÃ CHỐT 13/11 (tiêu chí: giữ API + hành vi cũ):** UNIQUE ghép (người thao tác, khoá) để giữ phạm vi cũ. Giữ `LEGACY_RECEPTIONIST` trong CHECK. Các câu có "đề xuất/mặc định" trong file: làm theo đề xuất (không thêm `reception_type`/`receptionist_account_id`, giữ `arrived_at`, bỏ `enforce_initial_reception_type` và các hàm check-in evidence, QR check-in giữ actor_type cũ). Nhật ký hàng chờ dùng category `VISIT` (theo phase 01; plan.md đã sửa). Conflict "on conflict do nothing" dùng `AuditLogWriter.recordIfAbsent` + `dedupKey` (phase 01 đã thêm). Khoá check-in FE gửi gì: agent kiểm `frontend/src` lúc làm; không phải UUID thì giữ kiểu cột theo FE, không đổi FE.

Các câu bên dưới giữ lại để tra lý do.
1. Xác nhận UNIQUE ghép `(checkin_actor_account_id, checkin_idempotency_key)`. Schema-33 ghi "checkin_idempotency_key UNIQUE" (đơn).
2. Giữ `LEGACY_RECEPTIONIST` trong CHECK (dữ liệu demo cũ có thể chứa) hay bỏ và lọc khi chép?
3. `checkin_checked_in_at` trùng `visits.checked_in_at`. Giữ để replay không đọc chéo sang `visits`, hay bỏ và để replay join `visits`?
4. Check-in qua QR ghi `actor_type = RECEPTIONIST` dù người thao tác là bệnh nhân (hành vi cũ, ngoài phạm vi). Chỉ báo, không sửa.
5. Xác nhận FE check-in gửi `Idempotency-Key` là UUID mỗi lần (chưa kiểm được luồng lễ tân).
