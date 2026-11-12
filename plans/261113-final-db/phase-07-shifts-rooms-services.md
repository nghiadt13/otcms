---
phase: 7
title: "Ca làm việc, phòng, dịch vụ (V112)"
status: pending
priority: P1
effort: "4h"
owner: Hùng
dependencies: [1]
migration: V112__final_db_shifts_rooms_services.sql
---

# Phase 07: Ca làm việc, phòng, dịch vụ (V112)

## Overview
- Gộp `doctor_shifts` và `staff_work_shifts` thành `work_shifts`. Bảng mới không có cột `kind`: ca bác sĩ và ca nhân viên được phân biệt bằng `accounts.account_type` (xem R2).
- Gộp `room_capabilities` thành cột `rooms.capabilities text[]` có GIN. `room_supported_services` giữ tên (D7).
- Ghi log WORK_SHIFT, phòng, đặt phòng và dịch vụ qua `AuditLogWriter` (phase 01). Chép dữ liệu demo rồi drop 6 bảng: `doctor_shifts`, `staff_work_shifts`, `room_capabilities`, `service_audits`, `room_management_audits`, `room_reservation_audits`.
- KHÔNG drop `staff_resource_audits` ở đây (phase 10, V120). Phase này chỉ ngừng ghi WORK_SHIFT và chép các dòng WORK_SHIFT sang `audit_logs`. Dòng DOCTOR_PROFILE thuộc phase 02, dòng TIME_OFF bị bỏ cùng tính năng nghỉ phép.
- Giữ nguyên: `clinic_services`, `doctor_services`, `room_supported_services`, `room_reservations` (kể cả EXCLUDE chống trùng phòng).
- Owner: Hùng. Merge SAU phase 02 (cả hai cùng sửa `ManageDoctorResourcesServiceImpl`). Các file package `visit` và test `appointment` thuộc Long: sửa tối thiểu và ghi rõ trong PR (mục Related Code Files).
- API không đổi.

## Requirements
- **R1 API giữ nguyên.** Lịch bác sĩ `/api/v1/admin/doctors/{id}/{kind}`. Ca nhân viên `/api/v1/admin/staff-shifts` (GET, POST, PUT `{id}`, DELETE `{id}`, GET `staff-options`). Phòng `/api/v1/admin/visit-rooms`. Dịch vụ `/api/v1/admin/clinic-services`. Nhật ký `/api/v1/admin/activity-logs` (phase 01).
- **R2 Tách hai loại ca theo `accounts.account_type`.**
  - Ca bác sĩ: tài khoản `DOCTOR`. Đọc qua `DoctorWorkShiftRepository`, khoá theo bác sĩ, nên không cần lọc.
  - Ca nhân viên: tài khoản khác `DOCTOR`. MỌI câu SQL phía nhân viên phải có điều kiện `account_type <> 'DOCTOR'` (mục 5b, 5c). Thiếu điều kiện thì ca bác sĩ lọt vào API ca nhân viên và vào "nhân viên đang ở phòng".
  - Tính phòng bận: đếm cả hai loại như cũ.
- **R3 Chống trùng.** EXCLUDE theo người (cả bác sĩ và nhân viên) giữ như cũ. EXCLUDE theo phòng: trước đây chỉ có cho ca bác sĩ, nay áp cho cả hai (Rủi ro 1).
- **R4 Log WORK_SHIFT.** `category` = `STAFF_SHIFT`; `action` = `CREATE_STAFF_SHIFT` / `CANCEL_STAFF_SHIFT` / `UPDATE_STAFF_SHIFT`; `record_type` = `WORK_SHIFT`; `entity_id` = id ca; `record_label` = họ tên bác sĩ lúc ghi (không có thì id ca).
- **R5 CAPACITY_CONFLICT** của đặt phòng ghi đúng một lần cho mỗi (lượt khám, người thao tác, `correlation_id`), như `ON CONFLICT DO NOTHING` cũ.

## Architecture

### 1. work_shifts (V112)
```sql
CREATE TABLE work_shifts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_profile_id uuid NOT NULL REFERENCES staff_profiles(id) ON DELETE RESTRICT,
    room_id uuid REFERENCES rooms(id) ON DELETE RESTRICT,   -- NULL với ca bác sĩ không gắn phòng (như doctor_shifts)
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_work_shifts_interval CHECK (ends_at > starts_at),
    CONSTRAINT ex_work_shifts_staff_no_overlap EXCLUDE USING gist
        (staff_profile_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&),
    CONSTRAINT ex_work_shifts_room_no_overlap EXCLUDE USING gist
        (room_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&) WHERE (room_id IS NOT NULL)
);
CREATE INDEX ix_work_shifts_room_interval ON work_shifts (room_id, starts_at, ends_at) WHERE room_id IS NOT NULL;
```
- FK `staff_profile_id` đã có chỉ mục: EXCLUDE GiST đứng đầu bằng cột này (quy tắc 4b).
- Chép dữ liệu demo. Cột `doctor_profile_id` của bảng cũ là UUID của `staff_profiles` do phase 02 giữ nguyên:
  ```sql
  INSERT INTO work_shifts (id, staff_profile_id, room_id, starts_at, ends_at, created_at, updated_at)
  SELECT id, doctor_profile_id, room_id, starts_at, ends_at, created_at, created_at FROM doctor_shifts;
  INSERT INTO work_shifts (id, staff_profile_id, room_id, starts_at, ends_at, created_at, updated_at)
  SELECT id, staff_profile_id, room_id, starts_at, ends_at, created_at, updated_at FROM staff_work_shifts;
  ```

### 2. rooms.capabilities
```sql
ALTER TABLE rooms ADD COLUMN capabilities text[] NOT NULL DEFAULT '{}';
UPDATE rooms r SET capabilities = coalesce((SELECT array_agg(DISTINCT c.capability_code ORDER BY c.capability_code)
    FROM room_capabilities c WHERE c.room_id = r.id), '{}');
CREATE INDEX ix_rooms_capabilities ON rooms USING gin (capabilities);
```
- GIN dùng `array_ops` mặc định. Quy tắc 4b ghi `jsonb_path_ops`, nhưng cách đó chỉ áp cho jsonb.
- Không có CHECK từng phần tử (CHECK không dùng được subquery). Độ dài 1–64 vẫn kiểm ở service như hiện tại.

### 3. Chép log cũ sang audit_logs
Dùng chung cho mọi nguồn: `id`, `occurred_at`, `actor_account_id`, `before_state`, `after_state`, `correlation_id` giữ nguyên. `source` = tên bảng cũ. `visible_in_activity_log` = true. `audit_logs.correlation_id` dài 64, còn bảng cũ dài 128, nên kiểm `max(length)` trước (pre-check).

| Nguồn (`source`) | Điều kiện | category | action | record_type | entity_id | record_label | metadata |
|---|---|---|---|---|---|---|---|
| `staff_resource_audits` | `resource_type = 'WORK_SHIFT'` | STAFF_SHIFT | CREATED→CREATE_STAFF_SHIFT; DELETED→CANCEL_STAFF_SHIFT; khác→UPDATE_STAFF_SHIFT | WORK_SHIFT | `resource_id` | `accounts.full_name` của `staff_profiles.account_id`; không có thì `resource_id::text` | NULL |
| `service_audits` | — | SERVICE | `resource_type='DOCTOR_ASSIGNMENT'`→UPDATE_SERVICE; CREATED→CREATE_SERVICE; UPDATED→UPDATE_SERVICE; khác→CHANGE_SERVICE_STATUS | SERVICE | `service_id` | `clinic_services.name` | `{resourceType, resourceId}` |
| `room_management_audits` | — | ROOM | CREATED→CREATE_ROOM; UPDATED→UPDATE_ROOM; khác→CHANGE_ROOM_STATUS | ROOM | `room_id` | `rooms.room_code` | NULL |
| `room_reservation_audits` | — | ROOM | giữ `action` cũ (RESERVED, EXTENDED, COMPLETED, CAPACITY_CONFLICT) | ROOM_RESERVATION | `coalesce(reservation_id, visit_id)` | `visit_id::text` | `{visitId, assignmentId, roomId, stepCode}` |

Sau khi chép, tạo chỉ mục thay UNIQUE cũ:
```sql
CREATE UNIQUE INDEX uq_audit_logs_room_capacity_conflict ON audit_logs (entity_id, actor_account_id, correlation_id)
    WHERE source = 'room_reservation_audits' AND action = 'CAPACITY_CONFLICT';
```
Với CAPACITY_CONFLICT, `reservation_id` NULL nên `entity_id` = `visit_id`. Chỉ mục này tương đương UNIQUE cũ `(visit_id, actor, correlation_id)`.

### 4. Drop (cuối V112, không dùng IF EXISTS)
- `DROP TABLE doctor_shifts; DROP TABLE staff_work_shifts; DROP TABLE room_capabilities;`
- `DROP TABLE service_audits; DROP TABLE room_management_audits; DROP TABLE room_reservation_audits;` (trigger `tr_*_immutable` và `tr_*_no_truncate` đi kèm).
- `DROP FUNCTION reject_service_catalog_audit_mutation(); DROP FUNCTION reject_visit_room_management_audit_mutation(); DROP FUNCTION reject_visit_room_reservation_audit_mutation();` Mỗi hàm chỉ dùng bởi một bảng vừa drop. KHÔNG drop `reject_staff_resource_audit_mutation` (còn đến V120).
- Trước khi drop, `SELECT proname FROM pg_proc WHERE prosrc ILIKE '%<tên bảng>%'` phải rỗng cho từng bảng (plan.md mục 4). V108 dump đã kiểm: không hàm nào tham chiếu các bảng này.

### 5. Java: before → after (chỗ khó)
Tên method builder của `AuditEntry` theo phase 01. Các ví dụ dưới giả định chúng trùng tên thành phần record.

**(a) Ca bác sĩ.** Giữ tên field Java, đổi bảng và cột:
```java
// before
@Table(name = "doctor_shifts")
@Column(name = "doctor_profile_id", nullable = false) private UUID doctorProfileId;
// after
@Table(name = "work_shifts")
@Column(name = "staff_profile_id", nullable = false) private UUID doctorProfileId;
```

**(b) SQL ca nhân viên** (`staff/repository/StaffRoomAssignmentRepository.java`, `findCurrentAssignments` và `hasActiveAssignment`). Thêm điều kiện không phải bác sĩ:
```sql
-- before: from staff_work_shifts s join staff_profiles p on p.id = s.staff_profile_id
-- after:  from work_shifts s join staff_profiles p on p.id = s.staff_profile_id
--         join accounts a on a.id = p.account_id and a.account_type <> 'DOCTOR'
```

**(c) StaffShiftAdminService.** Định nghĩa "ca nhân viên" bằng điều kiện dùng chung, áp cho `update`, `delete`, `one`, `existing`:
```sql
AND staff_profile_id IN (SELECT p.id FROM staff_profiles p JOIN accounts a ON a.id = p.account_id
                         WHERE a.account_type <> 'DOCTOR')
```
- `list`: đã có `a.account_type IN ('RECEPTIONIST','LAB_TECHNICIAN','IMAGING_TECHNICIAN')`, chỉ đổi tên bảng.
- `create`: chỉ đổi tên bảng trong INSERT.
- `ensureNoOverlap`: gộp hai câu đếm thành một, giữ `OR room_id` để đếm cả ca bác sĩ trong phòng:
  ```sql
  SELECT count(*) FROM work_shifts
  WHERE (staff_profile_id = ? OR room_id = ?) AND starts_at < ? AND ends_at > ? AND (?::uuid IS NULL OR id <> ?)
  ```

**(d) ManageDoctorResourcesServiceImpl, log WORK_SHIFT.** Giả định: phase 02 đã chuyển DOCTOR_PROFILE sang `AuditLogWriter` và bỏ TIME_OFF. Nếu chưa đúng khi làm, DỪNG và hỏi NghiaDT. Phase 07 chỉ đổi 3 chỗ gọi WORK_SHIFT (`createShift`, `deleteShift`, `updateShift`) và xoá `StaffResourceManagementAuditRepository` khỏi field và hai constructor:
```java
// before (createShift)
audit(doctorId, actor.accountId(), "WORK_SHIFT", id, "CREATED", null, intervalJson(startsAt, endsAt), correlationId);
// after
auditLog.record(AuditEntry.builder("staff_resource_audits", "STAFF_SHIFT", shiftAction("CREATED"), "WORK_SHIFT")
        .entityId(id).recordLabel(doctorName(doctorId, id)).actorAccountId(actor.accountId())
        .beforeState(null).afterState(Map.of("startsAt", startsAt.toString(), "endsAt", endsAt.toString()))
        .build());
```
- Truyền `Map` chứ không truyền chuỗi `intervalJson()`: chuỗi bị ghi thành JSON lồng, không phải object.
- `shiftAction`: CREATED→CREATE_STAFF_SHIFT; DELETED→CANCEL_STAFF_SHIFT; khác→UPDATE_STAFF_SHIFT.
- `doctorName`: `identity.findDoctorNames(List.of(doctorId)).get(doctorId)`, không có thì `id.toString()`. `DoctorProfileIdentityQueryService` là public API của staff (phase 02 giữ).
- SQL `ensureRoomAvailable` (dòng ~236): hai câu đếm `doctor_shifts` và `staff_work_shifts` gộp thành một câu đếm `work_shifts WHERE room_id = ? AND starts_at < ? AND ends_at > ? AND (?::uuid IS NULL OR id <> ?)`.
- `ManageDoctorResourcesServiceImpl` không còn gọi `StaffResourceManagementAuditRepository` cho WORK_SHIFT.

**(e) Dịch vụ.** Thêm `ServiceCatalogAuditRecorder` (mục Related Code Files) bọc `AuditLogWriter`, áp bảng ánh xạ mục 3. Thay 6 chỗ `audits.append(...)` (AuditClinicServiceMutation 1 chỗ, Activate 1, Deactivate 1, ManageDoctorServiceAssignments 2):
```java
// before (ActivateClinicServiceServiceImpl)
audits.append(UUID.randomUUID(), serviceId, actor.accountId(), "CLINIC_SERVICE", serviceId,
        "UPDATED", "{\"active\":false}", "{\"active\":true}", clock.instant(), correlationId);
// after
recorder.record(serviceId, serviceName, actor.accountId(), "CLINIC_SERVICE", serviceId,
        "UPDATED", Map.of("active", false), Map.of("active", true));
```
- Chuỗi JSON cũ (`"{\"active\":false}"`, `"{}"`, `"{\"doctorProfileId\":…}"`) đổi thành `Map`, để jsonb giữ đúng cấu trúc object.
- `correlationId` lấy từ MDC như phase 01. Giữ tham số `correlationId` ở chữ ký service để không đổi API nội bộ (có thể báo unused).
- `serviceName` là tên dịch vụ đã đọc trong hàm.
- `clock` của service chỉ bỏ nếu không còn dùng.

**(f) Phòng: capabilities.** `visit/model/entity/VisitRoomJpaEntity.java`:
```java
// before
@ElementCollection
@CollectionTable(name = "room_capabilities", joinColumns = @JoinColumn(name = "room_id"))
@Column(name = "capability_code", nullable = false, length = 64)
private Set<String> capabilities = new LinkedHashSet<>();
// after (quy tắc 6.1: text[] = @JdbcTypeCode(SqlTypes.ARRAY) + List<String>)
@JdbcTypeCode(SqlTypes.ARRAY)
@Column(name = "capabilities", nullable = false, columnDefinition = "text[]")
private List<String> capabilities = new ArrayList<>();
```
- Constructor và `update(...)`: `this.capabilities = new ArrayList<>(new LinkedHashSet<>(capabilities));` (khử trùng như Set cũ).
- `getCapabilities()` giữ `Set.copyOf(capabilities)`, nên các caller không đổi.
- `visit/repository/VisitRoomJpaRepository.java`:
  - `imagingCapability`: `exists (select 1 from room_capabilities c where c.room_id = r.id and c.capability_code = 'IMAGING_ROOM')` → `('IMAGING_ROOM' = any(r.capabilities))`. Giữ alias `"imagingCapability"`.
  - `snapshot`: `(select jsonb_agg(c.capability_code order by c.capability_code) from room_capabilities c where c.room_id = r.id)` → `(select jsonb_agg(c order by c) from unnest(r.capabilities) as c)`, giữ `coalesce(…, '[]'::jsonb)`.
  - Xoá `appendManagementAudit` (native insert vào `room_management_audits`).
- `visit/repository/VisitRoomReservationRepository.java`, `findAvailableRoom`: bỏ `join room_capabilities c …`, đổi thành `where r.active = true and :capability = any(r.capabilities)`.

**(g) Phòng: log.**
```java
// ManageVisitRoomServiceImpl.appendAudit, before
String afterState = rooms.snapshot(roomId);
rooms.appendManagementAudit(UUID.randomUUID(), roomId, actorAccountId, action, beforeState, afterState, occurredAt, correlationId);
// after
JsonNode after = objectMapper.readTree(rooms.snapshot(roomId));
auditLog.record(AuditEntry.builder("room_management_audits", "ROOM", roomAction(action), "ROOM")
        .entityId(roomId).recordLabel(after.path("roomCode").asText()).actorAccountId(actorAccountId)
        .beforeState(beforeState == null ? null : objectMapper.readTree(beforeState)).afterState(after)
        .build());
```
- Phải parse JSON (`JsonNode`). Truyền thẳng `String` sẽ ghi thành chuỗi JSON lồng. `readTree` ném `JsonProcessingException` (checked): bọc trong helper `readJson` của service.
- `roomAction`: CREATED→CREATE_ROOM; UPDATED→UPDATE_ROOM; khác→CHANGE_ROOM_STATUS.
- `ObjectMapper` là bean Spring. `AuditLogWriter` và `ObjectMapper` vào constructor của `ManageVisitRoomServiceImpl`.

**(h) Đặt phòng: CAPACITY_CONFLICT.** `VisitRoomReservationRepository.appendAudit` (native insert `… on conflict do nothing`, dòng ~166-180) đổi sang `auditLog.recordIfAbsent(AuditEntry.builder("room_reservation_audits", "ROOM", action, "ROOM_RESERVATION").entityId(reservationId != null ? reservationId : visitId).recordLabel(visitId.toString()).actorAccountId(actorAccountId).metadata(Map.of("visitId", visitId, "assignmentId", assignmentId, "roomId", roomId, "stepCode", stepCode)).build())`.
- `recordIfAbsent` CHƯA có trong phase 01. Chặn mục này: Câu hỏi 1.
- `metadata` dùng `LinkedHashMap`, không dùng `Map.of`: `roomId` và `reservationId` có thể NULL (CAPACITY_CONFLICT không có phòng và không có reservation), mà `Map.of` ném NPE với giá trị null.
- KHÔNG thay bằng `record()` rồi bắt `DataIntegrityViolationException`: Postgres đánh dấu transaction lỗi nên cả thao tác đặt phòng hỏng theo.
- `VisitRoomReservationRepository` (`@Repository`, hiện chỉ có `EntityManager`) nhận thêm `AuditLogWriter` qua constructor.

**(i) Xoá entity và repository audit cũ.** Xoá `ServiceCatalogManagementAuditJpaEntity` và `ServiceCatalogManagementAuditRepository`. Xoá `StaffResourceManagementAuditJpaEntity` và `StaffResourceManagementAuditRepository` chỉ khi `grep -rn StaffResourceManagementAudit backend/src/main/java` rỗng (sau phase 02).

## Related Code Files
**Create**
- `backend/src/main/resources/db/migration/V112__final_db_shifts_rooms_services.sql`: `git mv` từ `migration-staged/`, sửa theo mục 1–4.
- `servicecatalog/service/ServiceCatalogAuditRecorder.java`: một method `record(serviceId, serviceName, actorAccountId, resourceType, resourceId, action, before, after)`, dùng `AuditLogWriter`, áp ánh xạ mục 3 (source `service_audits`, category `SERVICE`, record_type `SERVICE`).

**Modify (main) — package của Hùng**
- `staff/model/entity/DoctorWorkShiftJpaEntity.java`: `@Table` và `@Column` (5a).
- `staff/repository/StaffRoomAssignmentRepository.java`: hai câu SQL (5b).
- `staff/service/StaffShiftAdminService.java`: SQL `update`, `delete`, `one`, `existing`, `create`, `ensureNoOverlap` và tên bảng trong `list` (5c).
- `staff/service/impl/ManageDoctorResourcesServiceImpl.java`: ba chỗ WORK_SHIFT, một câu SQL `ensureRoomAvailable`, field và hai constructor (5d). File này phase 02 cũng sửa: merge sau 02.
- `servicecatalog/service/AuditClinicServiceMutation.java`, `servicecatalog/service/impl/ActivateClinicServiceServiceImpl.java`, `servicecatalog/service/impl/DeactivateClinicServiceServiceImpl.java`, `servicecatalog/service/impl/ManageDoctorServiceAssignmentsServiceImpl.java`: thay `audits.append` và constructor (5e).

**Modify (main) — file của Long (package `visit`), ghi rõ trong PR và chờ Long duyệt**
- `visit/model/entity/VisitRoomJpaEntity.java` (5f).
- `visit/repository/VisitRoomJpaRepository.java` (5f).
- `visit/repository/VisitRoomReservationRepository.java` (5f, 5h).
- `visit/service/impl/ManageVisitRoomServiceImpl.java` (5g).

**Delete**
- `servicecatalog/model/entity/ServiceCatalogManagementAuditJpaEntity.java`, `servicecatalog/repository/ServiceCatalogManagementAuditRepository.java`.
- `staff/model/entity/StaffResourceManagementAuditJpaEntity.java`, `staff/repository/StaffResourceManagementAuditRepository.java` (điều kiện ở 5i).

**Không đổi (đã kiểm: chỉ đọc qua `DoctorWorkShift` hoặc `DoctorWorkShiftRepository`, khoá theo bác sĩ)**
- `staff/model/DoctorWorkShift.java`, `staff/repository/DoctorWorkShiftRepository.java`, `staff/service/impl/DoctorScheduleServiceImpl.java`, `staff/service/impl/StaffAvailabilityQueryServiceImpl.java`.
- **Reader ca bác sĩ trong package `appointment` (Long): không sửa code main.** Slot engine đi qua `StaffAvailabilityProviderQueryService` → `StaffAvailabilityQueryServiceImpl` → `DoctorWorkShiftRepository` (entity, đổi bảng ở 5a). Không có SQL trực tiếp trong `appointment`. Phần sửa tối thiểu của package này là các test SQL nêu ở mục Test (9 file). Các reader khác cũng đi qua service: `appointment/service/impl/MarkNoShowAfterShiftTransaction.java`, `visit/service/impl/CreateWalkInVisitServiceImpl.java`, `visit/service/impl/VisitExamQueueServiceImpl.java`, `visit/service/impl/VisitExamQueueReadServiceImpl.java`.
- `auth/config/DemoDoctorSeeder.java` (dùng constructor entity, không đổi).
- `visit/service/impl/VisitRoomCapacityServiceImpl.java` (giữ tên `appendConflict`), `visit/service/impl/ImagingRoomQueueServiceImpl.java` (đọc `VisitRoomJpaRepository`, không đổi chữ ký).

**Test (Modify), giữ nguyên ý nghĩa assert (quy tắc 6.5)**
- SQL trên `doctor_shifts` (đổi thành `work_shifts`, `doctor_profile_id` thành `staff_profile_id`), 18 file: `appointment/` AppointmentAvailabilityT019, AppointmentBookingT022, AppointmentChangeAvailabilityT020, CreateFollowUpAppointmentT030, MarkNoShowAfterShiftJob, PaymentHoldFixtureSupport, ReassignAppointmentT026, RescheduleAppointmentT024, SlotAvailabilityT018 (Long); `notification/NotificationPostgresIntegrationTest`; `patient/DependentsR3PostgresIntegrationTest` (cùng file với phase 06); `persistence/T032AdminResourceMutationPostgresIntegrationTest`; `staff/DoctorProfileT011PostgresIntegrationTest`; `visit/` CreateWalkInVisitT043PostgresIntegrationTest, VisitCareCapacityT031PostgresIntegrationTest, VisitExamFlowPostgresIntegrationTest, VisitExamQueuePostgresIntegrationTest, WalkInFeeAdmissionPostgresIntegrationTest (Long).
- SQL trên `staff_work_shifts`, `room_capabilities`, `room_management_audits`, `room_reservation_audits`, `service_audits`, `staff_resource_audits`: `activitylog/ActivityLogPostgresIntegrationTest` (đọc `source='service_audits'` trong `audit_logs`), `servicecatalog/ClinicServicePostgresIntegrationTest`, `staff/StaffRoomAssignmentPostgresIntegrationTest`, `visit/ImagingRoomQueuePostgresIntegrationTest`, `visit/VisitRoomAdminT031PostgresIntegrationTest`, `visit/VisitT012PostgresIntegrationTest`, và `persistence/T032…` đã nêu ở trên.
- Bàn giao từ phase 01: hai test `catalogCreateAndUpdateKeepLegacyAuditUntilPhase07MovesTheWriter` và `creatingRoomKeepsLegacyAuditUntilPhase07MovesTheWriter` đang xác nhận writer legacy còn tồn tại, chưa hiện trong view mới. Sau khi chuyển writer, đổi assert sang `audit_logs`, khôi phục kỳ vọng API có 2 dòng service/1 dòng room cùng action đúng; đổi tên test tương ứng. Không giữ kỳ vọng danh sách rỗng sau phase 07.
- Đổi constructor hoặc mock kiểu: `servicecatalog/ClinicServiceHandlerTest` (mock và hai constructor), `servicecatalog/ManageDoctorServiceAssignmentsT032Test` (constructor), `staff/ManageDoctorResourcesT032Test` (constructor và mock `StaffResourceManagementAuditRepository`).
- Cần kiểm khi chạy test, có thể không cần đổi: `appointment/service/impl/MarkNoShowAfterShiftTransactionTest`, `diagnostic/DiagnosticPaidRegistrationTest` (mock `VisitRoomJpaRepository`), `staff/StaffRoomAssignmentQueryServiceTest`, `visit/CreateWalkInVisitT043Test`, `visit/VisitClinicalEvidenceRepairTest`, `visit/VisitExamQueueReadServiceImplTest`, `visit/VisitExamQueueServiceImplTest`, `visit/VisitRoomCapacityT031Test`, `visit/VisitWorkflowCheckInServiceImplTest`.
- Thêm 4 test vào file có sẵn (không tạo file mới): (1) ca bác sĩ không xuất hiện là phòng bận của nhân viên và `StaffShiftAdminService` không sửa/xoá được ca bác sĩ; (2) CAPACITY_CONFLICT gửi trùng chỉ có một dòng; (3) tạo và xoá ca nhân viên sinh đúng action `CREATE_STAFF_SHIFT` / `CANCEL_STAFF_SHIFT` và `record_label` là họ tên; (4) đổi trạng thái dịch vụ ghi `audit_logs` với `source='service_audits'` và action đúng bảng ánh xạ.

## Implementation Steps
1. Kiểm điều kiện đầu vào: phase 01 và 02 đã merge (`AuditLogWriter`, `DoctorProfileIdentityQueryService.findDoctorNames`, `staff_profiles` chứa bác sĩ, `staff_resource_audits` FK đã trỏ `staff_profiles`). Câu hỏi 1 và 2 đã có trả lời.
2. Pre-check dữ liệu demo trên Docker sau V001–V111 và seed. Mọi kết quả phải như ghi, nếu không thì DỪNG:
   - Ca khác loại chồng lấn trong cùng phòng (sẽ vi phạm EXCLUDE theo phòng). Phải rỗng:
     ```sql
     SELECT 1 FROM doctor_shifts d JOIN staff_work_shifts s ON s.room_id = d.room_id
     WHERE tstzrange(d.starts_at, d.ends_at, '[)') && tstzrange(s.starts_at, s.ends_at, '[)');
     ```
   - Độ dài `correlation_id` của các bảng cũ phải ≤ 64: `SELECT max(length(correlation_id))` trên `service_audits`, `room_management_audits`, `room_reservation_audits`, và `staff_resource_audits` (chỉ WORK_SHIFT).
   - Trùng khoá CAPACITY_CONFLICT. Phải rỗng: `SELECT visit_id, actor_account_id, correlation_id FROM room_reservation_audits WHERE action='CAPACITY_CONFLICT' GROUP BY 1,2,3 HAVING count(*) > 1`.
   - Đếm để đối chiếu sau khi chép: số dòng của từng nguồn WORK_SHIFT, service_audits, room_management_audits, room_reservation_audits.
3. Tạo nhánh `final-db-p07-shifts-rooms-services` từ `final-db`.
4. `git mv` V112 sang `db/migration/`. Sửa SQL theo mục 1–4, thứ tự: tạo `work_shifts`, chép; thêm cột và chép `capabilities`; chép audit; tạo chỉ mục CAPACITY_CONFLICT; drop.
5. Reset DB Docker, chạy app profile `final-db` đến V112 không lỗi. Kiểm: `\dt` không còn 6 bảng; `\d work_shifts` có GiST và btree; `\d rooms` có `capabilities` và GIN.
6. Làm ca làm việc: 5a, 5b, 5c, 5d.
7. Làm phòng: 5f, 5g, 5h (cần Câu hỏi 1).
8. Làm dịch vụ: tạo `ServiceCatalogAuditRecorder`, 5e.
9. Xoá entity và repository audit cũ (5i).
10. Sửa test theo mục Test. Thêm 4 test mới.
11. Chạy grep trong Success Criteria, `./mvnw test`, so OpenAPI. Mở PR theo mẫu agent-rules mục 8. Ghi "Đổi migration: không". Ghi vào "File ngoài danh sách phase đã sửa": các file package visit và test appointment.

## Success Criteria
- [ ] `grep -rn "doctor_shifts\|staff_work_shifts\|room_capabilities\|service_audits\|room_management_audits\|room_reservation_audits" backend/src/main/java` rỗng.
- [ ] `grep -rln "doctor_shifts\|staff_work_shifts\|room_capabilities\|service_audits\|room_management_audits\|room_reservation_audits" backend/src/test` rỗng.
- [ ] `grep -rln "ServiceCatalogManagementAudit\|appendManagementAudit" backend/src/main/java backend/src/test` rỗng. `grep -rln StaffResourceManagementAudit backend/src/main/java backend/src/test` rỗng (nếu xoá ở 5i).
- [ ] Mọi câu SQL trên `work_shifts` trong `staff/` có điều kiện không phải bác sĩ, trừ `ensureRoomAvailable`, `ensureNoOverlap` và các reader đã nêu là cố ý đếm cả hai loại. Kiểm bằng `grep -rn "work_shifts" backend/src/main/java/fu/fa26/sep490/g58/otcms_be/staff`.
- [ ] `\dt` đúng bảng mới. `SELECT source, count(*) FROM audit_logs GROUP BY 1` khớp số đã đếm trước khi drop.
- [ ] Test mới đạt (4 test ở mục Test). `./mvnw test`: chỉ còn lỗi có sẵn (agent-rules mục 7).
- [ ] So OpenAPI: no differences.
- [ ] Đi tay trên giao diện: tạo, sửa, xoá ca nhân viên; tạo ca bác sĩ có phòng; xem nhật ký (ROOM, SERVICE, STAFF_SHIFT); đặt phòng và gặp CAPACITY_CONFLICT.

## Risk Assessment
1. **EXCLUDE theo phòng cho ca nhân viên là ràng buộc mới ở DB.** Service đã chặn trước (`ensureNoOverlap`, và `guard.acquire()` ở create/update), nên chỉ race mới vi phạm. Khi vi phạm, Postgres ném exclusion violation, không phải `STAFF_SHIFT_CONFLICT`. Chấp nhận nếu NghiaDT đồng ý (Câu hỏi 3).
2. **Quên điều kiện không phải bác sĩ** sẽ lộ ca bác sĩ qua API nhân viên và làm sai phòng bận. Test (1) bắt lỗi này. Reviewer đối chiếu bằng grep ở Success Criteria.
3. **Hai loại ca chung một bảng** nên mọi câu SQL mới phải nhớ điều kiện `account_type`. Schema-33 mục 2.3 nói không cần cột `kind`, đúng về cấu trúc nhưng đẩy việc lọc xuống query.
4. **Không có `occurredAt` trong `AuditEntry`** (phase 01). Thời điểm log lấy từ DB `now()`, còn code cũ dùng `Clock`. Test nào so sánh `occurred_at` với Clock cố định phải đổi (Câu hỏi 2).
5. **CAPACITY_CONFLICT** cần `recordIfAbsent`, không có thì không merge (Câu hỏi 1).
6. **`correlation_id` dài hơn 64** làm migration lỗi khi chép. Pre-check ở mục 2.
7. **Chồng merge với phase 02** ở `ManageDoctorResourcesServiceImpl`, `DoctorProfileIdentityQueryService` và test chung. Merge sau 02, rebase, giữ dòng của mình.
8. **File của Long** (`visit/`, test `appointment/`): sửa chỉ những dòng nêu ở mục này. Theo `otcms/AGENTS.md`, phải nói trước với Long và user.
9. **`staff_resource_audits` còn đến V120.** Dòng WORK_SHIFT tồn tại ở hai nơi cho đến V120. Nhật ký chỉ đọc `audit_logs` (phase 01) nên không trùng. Phase 10 drop bảng này.
10. **GIN cho `text[]`** dùng `array_ops` mặc định. Quy tắc 4b ghi `jsonb_path_ops`, sai kiểu cho cột này.

## Câu hỏi còn mở

> **Bổ sung 13/11 (sau khi viết V112):** (1) EXCLUDE theo phòng chặn cả ca nhân viên trùng ca nhân viên cùng phòng; khớp luật đang có ở `StaffShiftAdminService.ensureNoOverlap` (đếm `staff_profile_id=? OR room_id=?` + ca bác sĩ cùng phòng), nên hành vi không đổi; bước pre-check dữ liệu phải kiểm cả trùng cùng loại trong một phòng. (2) Truy vấn lọc năng lực phòng dùng `r.capabilities @> ARRAY[:capability]` (GIN dùng được), KHÔNG dùng `:capability = any(r.capabilities)`. (3) `actor_type` của dòng audit: `AuditLogWriter` tự điền `ACCOUNT` khi có `actorAccountId` mà không truyền `actorType` (phase 01).

> **ĐÃ CHỐT 13/11:** Q1 có: phase 01 đã thêm `AuditLogWriter.recordIfAbsent` + cột `dedup_key`. Q2 có: `AuditEntry.occurredAt`. Q3 chặn ở DB như mục 1, vì service đã chặn đúng luật này (`ensureNoOverlap`), hành vi không đổi; bắt buộc có test "ca nhân viên không thấy ca bác sĩ" cho mọi câu SQL phía nhân viên. Q4 NghiaDT báo Long trước khi giao; Hùng chỉ sửa đúng dòng liệt kê. Q5 phase 02 giữ `DoctorProfileIdentityQueryService.findDoctorNames`.

Các câu bên dưới giữ lại để tra lý do.
1. **(Chặn mục 5h)** Thêm `AuditLogWriter.recordIfAbsent(AuditEntry)` (`INSERT … ON CONFLICT DO NOTHING`) vào phase 01? Đề xuất: có. Phase 01 là file của NghiaDT, phase này không tự sửa.
2. **(Chặn đối chiếu thời gian)** Thêm `occurredAt` (mặc định `now()`) vào `AuditEntry` để giữ thời điểm `Clock` cũ? Đề xuất: có.
3. Chồng lấn ca nhân viên và ca bác sĩ trong cùng phòng: chặn ở DB như mục 1 (đề xuất), hay chỉ chặn ở service như trước?
4. Long đồng ý cho sửa 4 file package `visit` và các dòng test `appointment` nêu ở mục này? Theo `AGENTS.md`, phải nói trước.
5. `record_label` ca bác sĩ lấy qua `DoctorProfileIdentityQueryService.findDoctorNames`. Phase 02 có giữ API này không?
