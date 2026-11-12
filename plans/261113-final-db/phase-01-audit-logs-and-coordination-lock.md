---
phase: 1
title: "audit_logs + AuditLogWriter, view nhật ký, bỏ transaction_guard"
status: pending
priority: P1
effort: "3h"
owner: NghiaDT
dependencies: [0]
migration: V109__final_db_audit_logs_and_drop_transaction_guard.sql
---

# Phase 01: audit_logs + AuditLogWriter, view nhật ký, bỏ transaction_guard

## Overview
Tạo bảng `audit_logs`, lớp ghi log dùng chung `AuditLogWriter`, viết lại view `activity_log_entries` cho đọc từ `audit_logs`, và bỏ bảng khoá `transaction_guard`.

Phase này **chưa** xoá bảng audit nào của module khác; mỗi phase sau tự chuyển phần ghi log của mình sang `AuditLogWriter` rồi drop bảng audit cũ.

**Merge càng sớm càng tốt:** mọi phase khác cần `AuditLogWriter`.

## Requirements
- API `GET /api/v1/admin/activity-logs` (và xem chi tiết theo id) giữ nguyên request/response.
- Trong lúc chờ các phase sau, nhật ký hệ thống tạm chỉ hiện các bản ghi đã ghi vào `audit_logs`. Đã chấp nhận vì dữ liệu chỉ là demo; phase 10 kiểm lại đủ.
- Khoá điều phối giữ nguyên hành vi: khoá loại trừ trong transaction, `lock_timeout` 2s.

## Architecture

### Bảng `audit_logs` (V109)
```
id uuid PK default gen_random_uuid(),
occurred_at timestamptz NOT NULL default now(),
source varchar(64) NOT NULL,          -- tên bảng audit cũ mà dòng này thay thế, vd 'room_management_audits'; dùng làm tiền tố id trong view
category varchar(32) NOT NULL,        -- giá trị NHẬT KÝ HIỂN THỊ (cột category của view cũ), vd ROOM, VISIT, STAFF_PROFILE
action varchar(64) NOT NULL,          -- giá trị HIỂN THỊ đã ánh xạ, vd CREATE_ROOM (xem bảng ánh xạ bên dưới)
record_type varchar(48) NOT NULL,
entity_id uuid NULL,                  -- record_id của view cũ
record_label text NULL,               -- chụp nhãn lúc ghi (mã phòng, tên dịch vụ, tên bác sĩ…), không join lúc đọc
correction_reason text NULL,
visible_in_activity_log boolean NOT NULL default true,
actor_account_id uuid NULL, actor_type varchar(32) NULL,
before_state jsonb NULL, after_state jsonb NULL, metadata jsonb NULL,
correlation_id varchar(128) NULL, client_ip varchar(64) NULL, user_agent text NULL,
identifier_hmac varchar(128) NULL, outcome varchar(32) NULL,
dedup_key varchar(200) NULL UNIQUE   -- chỉ dùng cho recordIfAbsent (thay các audit cũ "ON CONFLICT DO NOTHING", vd CAPACITY_CONFLICT của phòng)
```
`schema-33-tables.md` mục 2.11 là bản gốc; khi lệch thì **file này là chuẩn** (`record_type` thay `entity_type`, không có `duration_ms`).
- Index:
  - `(source, action, identifier_hmac, occurred_at)`: phase 02 dùng để đếm số lần đăng nhập (rate-limit).
  - `(visible_in_activity_log, occurred_at DESC)`.
  - `(record_type, entity_id)`.
  - `(actor_account_id)`.
- Trigger `reject_audit_log_mutation` chặn UPDATE / DELETE / TRUNCATE (chép thân hàm từ `reject_content_audit_mutation`).
- Không có FK nào (log phải còn khi bản ghi gốc bị xoá).

### View `activity_log_entries` (V109, `CREATE OR REPLACE VIEW`)
```sql
SELECT (CASE a.source
          WHEN 'staff_resource_audits' THEN 'staff' WHEN 'room_management_audits' THEN 'room'
          WHEN 'service_audits' THEN 'service' WHEN 'content_audits' THEN 'content'
          WHEN 'account_management_audits' THEN 'account' WHEN 'appointment_audits' THEN 'appointment'
          WHEN 'visit_audits' THEN 'visit' WHEN 'visit_assignment_audits' THEN 'care'
          WHEN 'room_reservation_audits' THEN 'room_reservation' WHEN 'walk_in_reception_queue_history' THEN 'queue'
          WHEN 'diagnostic_audit_events' THEN 'diagnostic' WHEN 'diagnostic_history_access_audits' THEN 'diagnostic_access'
          ELSE 'log' END) || ':' || a.id AS id,
       a.occurred_at, a.actor_account_id, a.category, a.action, a.record_type,
       a.entity_id AS record_id, a.record_label, a.correction_reason
FROM audit_logs a WHERE a.visible_in_activity_log;
```
`JdbcActivityLogRepository` không phải sửa, vì tên cột của view giữ nguyên. Kiểm lại câu `HIDE_PAYMENT_HOLD_AUDIT` (đang join `appointments`) vẫn chạy.

### Bảng ánh xạ cho các phase sau (giá trị phải ghi để nhật ký hiện y như cũ)

| Bảng cũ (`source`) | Phase | category | action ghi vào | record_type | entity_id | record_label |
|---|---|---|---|---|---|---|
| staff_resource_audits (resource_type DOCTOR_PROFILE) | 02 | STAFF_PROFILE | CREATED→`CREATE_STAFF_PROFILE`, khác→`UPDATE_STAFF_PROFILE` | DOCTOR_PROFILE | resource_id | họ tên bác sĩ (accounts.full_name) |
| staff_resource_audits (WORK_SHIFT) | 07 | STAFF_SHIFT | CREATED→`CREATE_STAFF_SHIFT`, DELETED→`CANCEL_STAFF_SHIFT`, khác→`UPDATE_STAFF_SHIFT` | WORK_SHIFT | resource_id | họ tên bác sĩ |
| staff_resource_audits (loại khác) | 02/07 | như trên | như trên | resource_type | resource_id | — ; `visible_in_activity_log = false` |
| room_management_audits | 07 | ROOM | CREATED→`CREATE_ROOM`, UPDATED→`UPDATE_ROOM`, khác→`CHANGE_ROOM_STATUS` | ROOM | room_id | room_code |
| service_audits | 07 | SERVICE | resource_type DOCTOR_ASSIGNMENT→`UPDATE_SERVICE`; CREATED→`CREATE_SERVICE`; UPDATED→`UPDATE_SERVICE`; khác→`CHANGE_SERVICE_STATUS` | SERVICE | service_id | tên dịch vụ |
| content_audits | 09 | giá trị `category` cũ | giá trị `action` cũ | = category | record_id | record_label cũ |
| account_management_audits | 02 | ACCOUNT | action cũ | ACCOUNT | account_id | account_id dạng chữ |
| appointment_audits | 04 | APPOINTMENT | action cũ | APPOINTMENT | appointment_id | appointment_id dạng chữ |
| visit_audits | 05 | VISIT | action cũ | VISIT | visit_id | visit_id dạng chữ |
| visit_assignment_audits | 05 | VISIT | action cũ | VISIT_ASSIGNMENT | assignment_id, không có thì visit_id | visit_id dạng chữ |
| room_reservation_audits | 07 | ROOM | action cũ | ROOM_RESERVATION | reservation_id, không có thì visit_id | visit_id dạng chữ |
| walk_in_reception_queue_history | 05 | VISIT | reason, không có thì to_status | VISIT | visit_id của lượt chờ | visit_id dạng chữ |
| diagnostic_audit_events | 03 | DIAGNOSTIC | `action || ':' || outcome` | DIAGNOSTIC_REQUEST | request_id, không có thì visit_id | id đó dạng chữ |
| diagnostic_history_access_audits | 03 | DIAGNOSTIC | `VIEW_HISTORY` | scope_type | scope_id | scope_id dạng chữ |
| auth_audits | 02 | AUTH | action cũ | AUTH | account_id | — ; `visible = false`; điền identifier_hmac, client_ip, user_agent, outcome |
| account_consents (lịch sử) | 02 | CONSENT | action cũ | ACCOUNT | account_id | — ; `visible = false` |
| imaging_audit_events | 03 | IMAGING | action cũ | IMAGING_STUDY | — (study_ref vào metadata) | — ; `visible = false` |
| patient_review_moderation_history | 09 | REVIEW | action cũ | PATIENT_REVIEW | review_id | — ; `visible = false` |

### `AuditLogWriter` (package `common.audit`)
```java
public record AuditEntry(String source, String category, String action, String recordType,
        UUID entityId, String recordLabel, String correctionReason, boolean visibleInActivityLog,
        UUID actorAccountId, String actorType, Object beforeState, Object afterState, Map<String, Object> metadata,
        String clientIp, String userAgent, String identifierHmac, String outcome,
        Instant occurredAt,   // null → Instant.now(clock) lúc ghi; truyền vào khi code cũ lấy giờ từ Clock (test cần thứ tự)
        String dedupKey) {
    public static Builder builder(String source, String category, String action, String recordType) { ... }
    // Builder: mỗi thành phần còn lại có 1 setter cùng tên, trả về Builder, ví dụ
    //   AuditEntry.builder("room_management_audits", "ROOM", "CREATE_ROOM", "ROOM")
    //       .entityId(room.getId()).recordLabel(room.getRoomCode()).actorAccountId(actorId)
    //       .afterState(snapshot).occurredAt(now).build();
    // visibleInActivityLog mặc định true.
}
public interface AuditLogWriter {
    void record(AuditEntry entry);            // @Transactional(propagation = MANDATORY)
    boolean recordIfAbsent(AuditEntry entry); // MANDATORY; bắt buộc có dedupKey; native INSERT … ON CONFLICT (dedup_key) DO NOTHING; true nếu đã chèn
}
```
- `AuditLogWriterImpl` dùng `AuditLogJpaEntity` + `AuditLogRepository`. `correlation_id` lấy từ MDC (`CorrelationIdFilter` đã đặt).
- `beforeState` / `afterState` chuyển thành JSON bằng `ObjectMapper` của Spring. Trước khi ghi, gọi `SensitiveDataRedactor` (có sẵn trong `common.security`).
- `MANDATORY`: gọi ngoài transaction là lỗi ngay. Mục đích là ép log ghi cùng transaction với thao tác.
- `AuditLogRepository` thêm sẵn hàm đếm cho rate-limit của phase 02: `long countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(String source, String action, String outcome, String identifierHmac, Instant since)` (cần lọc `outcome`, vd LOGIN_IP/ATTEMPT, RATE_LIMIT_ATTEMPT).

### Khoá điều phối
`CoordinationGuardRepository.lockGuardRow()`:
```java
entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key)").setParameter("key", COORDINATION_LOCK_KEY).getSingleResult();
return true;
```
- `COORDINATION_LOCK_KEY` là hằng `long`, ví dụ `4_903_490_001L`. Ghi comment giải thích đây là khoá dùng chung cho các transaction ảnh hưởng tới đặt lịch.
- `lock_timeout` 2s vẫn áp dụng cho khoá advisory, nên `CoordinationGuardServiceImpl` không phải sửa.
- V109 drop trigger + hàm `prevent_integration_coordination_guard_mutation`, rồi drop bảng `transaction_guard`.

## Related Code Files
- Create:
  - `common/audit/AuditEntry.java`
  - `common/audit/AuditLogWriter.java`
  - `common/audit/impl/AuditLogWriterImpl.java`
  - `common/audit/model/AuditLogJpaEntity.java`
  - `common/audit/repository/AuditLogRepository.java`
- Create (test):
  - `common/audit/AuditLogWriterIntegrationTest.java`: ghi 1 dòng; UPDATE/DELETE bị trigger chặn; gọi ngoài transaction thì lỗi; `recordIfAbsent` 2 lần cùng `dedupKey` → 1 dòng, lần 2 trả false; `occurredAt` truyền vào được giữ nguyên.
  - `activitylog/ActivityLogViewOverAuditLogsTest.java`: ghi 1 dòng `room_management_audits` / `CREATE_ROOM`, rồi API trả đúng `id = room:<uuid>`, action, recordLabel.
- Modify: `common/repository/CoordinationGuardRepository.java` (chỉ hàm `lockGuardRow`).
- Move: `db/migration-staged/V109__…sql` → `db/migration/`.
- Modify (test): các test đang INSERT/SELECT `transaction_guard`. Tìm bằng `grep -rln transaction_guard backend/src/test`.

## Implementation Steps
1. Tạo nhánh `final-db-p01-audit-logs` từ `final-db`.
2. Chuyển V109 sang `db/migration`. Reset DB Docker, chạy app để chắc chắn khởi động được.
3. Viết các class trong `common/audit` như mục Architecture.
4. Sửa `lockGuardRow`.
5. Viết 2 test mới, sửa các test dùng `transaction_guard`.
6. Chạy toàn bộ `./mvnw test` và so OpenAPI.

## Success Criteria
- [ ] `grep -rn "transaction_guard" backend/src/main` rỗng.
- [ ] 2 test mới đạt; `./mvnw test` chỉ còn lỗi có sẵn.
- [ ] So OpenAPI: no differences.
- [ ] Gọi `GET /api/v1/admin/activity-logs` bằng tài khoản Admin trả 200, không lỗi SQL.

## Risk Assessment
- **Các bảng audit cũ vẫn được ghi cho tới khi phase sở hữu chuyển sang**, nên nhật ký tạm thiếu dòng. Đã chấp nhận; phase 10 kiểm đủ 13 nguồn.
- **Khoá advisory không bị chặn bởi `FOR UPDATE NOWAIT`.** Nếu có code nào gọi `transaction_guard` với `NOWAIT`, grep bước Success sẽ bắt được.
