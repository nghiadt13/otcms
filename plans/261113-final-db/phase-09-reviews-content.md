---
phase: 9
title: "Đánh giá, nội dung web, thông báo chung: gộp announcements, chuyển nhật ký sang audit_logs"
status: pending
priority: P1
effort: "3h"
owner: Hùng
dependencies: [1]
migration: V113__final_db_reviews_content.sql
---

# Phase 09: Đánh giá, nội dung web, thông báo chung

## Overview
- Gộp `announcements` vào `website_contents` (`type = 'ANNOUNCEMENT'`, thêm `audience`, `recipient_role`).
- Chuyển `content_audits` và `patient_review_moderation_history` sang `audit_logs` qua `AuditLogWriter`, rồi bỏ hai bảng cùng hàm trigger của chúng.
- Giữ nguyên: `patient_reviews`, `notifications`, `website_images` và mọi API website, thông báo, đánh giá.

**Phụ thuộc:** phase 01 đã merge. `DROP TABLE content_audits` lỗi nếu view `activity_log_entries` còn đọc bảng này (V108 đọc), nên V113 chỉ chạy sau khi V109 viết lại view. Q2 cần `AuditEntry.occurredAt` từ phase 01.

**Ảnh bìa (Q1, chặn):** plan.md mục 2 ghi `website_images` gộp vào `website_contents`. Làm đúng như vậy thì không còn chỗ giữ ảnh giữa lúc tải lên và lúc gắn vào nội dung, và API tải ảnh phải đổi. Phase này mặc định giữ `website_images` (phương án A của Q1).

## Requirements
- `/api/v1/public/announcements`, `/api/v1/public/announcements/{id}`, `/api/v1/admin/announcements…`: request và response giữ nguyên; lọc theo audience và phạm vi PUBLIC, PATIENT, STAFF như cũ.
- `/api/v1/admin/website-contents`, `/api/v1/public/website-content`, `/about`, `/articles/{id}`: không bao giờ trả dòng `ANNOUNCEMENT`.
- `POST` hoặc `PUT /api/v1/admin/website-contents` với `type = ANNOUNCEMENT`: cùng kết quả như giá trị `type` lạ hiện nay (`INVALID_CONTENT`).
- `GET /api/v1/admin/website-contents/{id thông báo}`: như id không tồn tại.
- Nhật ký nội dung hiện đúng như cũ ở `GET /api/v1/admin/activity-logs` (nguồn `content_audits`). Kiểm duyệt đánh giá không hiện (`visible = false`).
- Thông báo, nhật ký, đánh giá và lịch sử kiểm duyệt cùng commit hoặc cùng rollback (test `failedPrivateDeliveryRollsBackAnnouncementAndAuditAtomically`, `notificationInsertFailureRollsBackReviewAndHistory`).
- Lịch sử kiểm duyệt chỉ lưu các trường bảng cũ đang lưu: action, trạng thái trước và sau, `reply_text`, actor, thời điểm.

## Architecture

### 1. `website_contents` nhận thông báo chung
- Thêm `audience varchar(16) NULL`, `recipient_role varchar(32) NULL`.
- `website_contents_type_check` thêm `'ANNOUNCEMENT'`.
- `ck_website_contents_announcement` thay các CHECK của `announcements` (`announcements_check`, `announcements_title_check`, `announcements_body_check`), và thêm `status = 'PUBLISHED'` cho dòng thông báo.
- Index `ix_website_contents_announcement_public (published_at DESC, id) WHERE type = 'ANNOUNCEMENT' AND audience = 'PUBLIC'` thay `ix_announcements_public`.
- Dòng chép từ `announcements`: `summary = ''`, `status = 'PUBLISHED'`, `cover_image_id = NULL`, `updated_at = published_at`, `updated_by = created_by`; giữ `id`, `version`, `audience`, `recipient_role`.
- `website_contents.cover_image_id` và FK tới `website_images` giữ nguyên (Q1 phương án A).

### 2. Nhật ký trong `audit_logs` (bảng do phase 01 tạo)
| Nguồn cũ | category | action | record_type | entity_id | record_label | visible | before / after |
|---|---|---|---|---|---|---|---|
| `content_audits` | `category` cũ (CONTENT / ANNOUNCEMENT) | `action` cũ | = category | `record_id` | `record_label` | true | NULL |
| `patient_review_moderation_history` | REVIEW | `action` cũ | PATIENT_REVIEW | `review_id` | NULL | false | before `{status: from_status, replyText: null}`, after `{status: to_status, replyText: reply_text}` |

Cả hai: `source` là tên bảng cũ, `actor_account_id` là actor, `actor_type = 'ACCOUNT'`, `occurred_at` là thời điểm cũ.

### 3. Java: trước → sau

**`JpaClinicContentRepository`** (`content/repository/`)
- Constructor: bỏ `ClinicAnnouncementRepository announcements` và `ContentAuditRepository audits`; thêm `AuditLogWriter auditLog`.
- `contents(...)`: thêm `builder.notEqual(root.get("type"), ContentType.ANNOUNCEMENT)`.
- `content(id, publishedOnly)`: thêm `entity.getType() != ContentType.ANNOUNCEMENT &&` vào filter.
- `announcements(...)`: đổi sang `Specification<WebsiteContentJpaEntity>`, thêm `builder.equal(root.get("type"), ContentType.ANNOUNCEMENT)`. Các predicate khác giữ nguyên tên field (`audience`, `recipientRole`, `publishedAt`, `title`).
- `announcement(id, publicOnly)`: `contents.findById(id).filter(e -> e.getType() == ContentType.ANNOUNCEMENT && (!publicOnly || "PUBLIC".equals(e.getAudience())))`.
- `insert(ClinicAnnouncement)`: thay `announcements.saveAndFlush(new ClinicAnnouncementJpaEntity(...))` bằng:
```java
var entity = contents.saveAndFlush(new WebsiteContentJpaEntity(value.id(), ContentType.ANNOUNCEMENT, value.title(), "",
        value.body(), "PUBLISHED", null, value.publishedAt(), value.publishedAt(), value.createdBy(), value.createdBy(),
        null, value.audience(), value.recipientRole()));
return mapper.announcement(entity, authors.names(List.of(entity.getCreatedBy())).get(entity.getCreatedBy()));
```
- `audit(...)`: thay `audits.saveAndFlush(new ContentAuditJpaEntity(...))` bằng:
```java
auditLog.record(AuditEntry.builder("content_audits", category, action, category)
        .entityId(recordId).recordLabel(recordLabel).actorAccountId(actor).actorType("ACCOUNT")
        .visibleInActivityLog(true).occurredAt(at).build());
```
- `image(...)`: không đổi.

**`WebsiteContentJpaEntity`**: thêm sau `version`: `@Column(length = 16) private String audience;` và `@Column(length = 32) private String recipientRole;`. Với `@AllArgsConstructor`, constructor thành 14 tham số.

**`ContentType`**: thêm `ANNOUNCEMENT`. Enum là bắt buộc để Hibernate đọc được dòng `type = 'ANNOUNCEMENT'`.

**`ContentInputValidator.contentType(String)`**: sau `ContentType.valueOf(type)`, nếu kết quả là `ANNOUNCEMENT` thì ném `ContentOperationException(INVALID_CONTENT)`, cùng nhánh với giá trị lạ.

**`ClinicContentEntityMapper.announcement(...)`**: tham số đổi từ `ClinicAnnouncementJpaEntity` sang `WebsiteContentJpaEntity`. Các getter giữ nguyên.

**`ReviewServiceImpl`**
- Constructor: thay `PatientReviewHistoryRepository history` bằng `AuditLogWriter audit` (cùng vị trí); bỏ field `history`.
- Thay ba lệnh `history.save(new PatientReviewHistoryJpaEntity(...))` (dòng 73 `SUBMIT`, dòng 127 `action`, dòng 139 `REPLY` hoặc `EDIT_REPLY`) bằng `recordModeration(...)`. Giữ thứ tự: `reviews.saveAndFlush(review)` trước, rồi mới ghi nhật ký. Người thua đua bị lỗi version trước khi có dòng audit.
```java
private void recordModeration(PatientReviewJpaEntity review, String action, ReviewStatus from, UUID actor, Instant now) {
    String replyText = "REPLY".equals(action) || "EDIT_REPLY".equals(action) ? review.getReplyText() : null;
    audit.record(AuditEntry.builder("patient_review_moderation_history", "REVIEW", action, "PATIENT_REVIEW")
            .entityId(review.getId()).actorAccountId(actor).actorType("ACCOUNT").visibleInActivityLog(false)
            .beforeState(new ModerationState(from == null ? null : from.name(), null))
            .afterState(new ModerationState(review.getStatus().name(), replyText))
            .occurredAt(now).build());
}
public record ModerationState(String status, String replyText) { }
```
`SUBMIT` gọi với `from = null`.

### 4. Migration V113 (theo thứ tự)
```sql
-- V113__final_db_reviews_content.sql (phase 09). Yêu cầu: V109 đã chạy (audit_logs và view mới).
-- 1. website_contents nhận thông báo chung
ALTER TABLE website_contents
    ADD COLUMN audience varchar(16),
    ADD COLUMN recipient_role varchar(32),
    DROP CONSTRAINT website_contents_type_check,
    ADD CONSTRAINT website_contents_type_check
        CHECK (type IN ('ABOUT_PAGE', 'HEALTH_ARTICLE', 'CLINIC_INFO', 'ANNOUNCEMENT')),
    ADD CONSTRAINT ck_website_contents_announcement CHECK (
        (type = 'ANNOUNCEMENT') = (audience IS NOT NULL)
        AND (audience IS NULL OR audience IN ('PUBLIC', 'PATIENT', 'STAFF'))
        AND (COALESCE(audience, '') = 'STAFF') = (recipient_role IS NOT NULL)
        AND (recipient_role IS NULL OR recipient_role IN
             ('ORTHOPEDIC_DOCTOR', 'RECEPTIONIST', 'IMAGING_TECHNICIAN', 'LAB_TECHNICIAN'))
        AND (type <> 'ANNOUNCEMENT' OR (status = 'PUBLISHED'
             AND length(btrim(title)) > 0 AND length(btrim(body)) > 0)));
CREATE INDEX ix_website_contents_announcement_public
    ON website_contents (published_at DESC, id) WHERE type = 'ANNOUNCEMENT' AND audience = 'PUBLIC';

-- 2. Chép thông báo cũ (dữ liệu demo)
INSERT INTO website_contents (id, type, title, summary, body, status, cover_image_id, published_at, updated_at,
                              created_by, updated_by, version, audience, recipient_role)
SELECT id, 'ANNOUNCEMENT', title, '', body, 'PUBLISHED', NULL, published_at, published_at,
       created_by, created_by, version, audience, recipient_role
FROM announcements;

-- 3. Chép nhật ký cũ sang audit_logs
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id, record_label,
                        visible_in_activity_log, actor_account_id, actor_type)
SELECT id, occurred_at, 'content_audits', category, action, category, record_id, record_label,
       true, actor_account_id, 'ACCOUNT'
FROM content_audits;
INSERT INTO audit_logs (id, occurred_at, source, category, action, record_type, entity_id,
                        visible_in_activity_log, actor_account_id, actor_type, before_state, after_state)
SELECT id, occurred_at, 'patient_review_moderation_history', 'REVIEW', action, 'PATIENT_REVIEW', review_id,
       false, actor_account_id, 'ACCOUNT',
       jsonb_build_object('status', from_status, 'replyText', NULL::text),
       jsonb_build_object('status', to_status, 'replyText', reply_text)
FROM patient_review_moderation_history;

-- 4. Bỏ bảng cũ, rồi bỏ hàm trigger không còn ai dùng (trigger đi cùng bảng)
DROP TABLE content_audits;
DROP FUNCTION reject_content_audit_mutation();
DROP TABLE patient_review_moderation_history;
DROP FUNCTION reject_patient_review_history_mutation();
DROP TABLE announcements;
```

### 5. Đã kiểm, không đổi
- **Announcements:** 18 file Java nhắc `announcements` hoặc `ClinicAnnouncement` (13 main, 5 test). Sửa: `JpaClinicContentRepository`, `ClinicContentEntityMapper`, `ContentInputValidator`, `ClinicContentPostgresIntegrationTest`, `LayeredArchitectureTest`. Xoá: `ClinicAnnouncementJpaEntity`, `ClinicAnnouncementRepository`. Không đổi (chỉ URL, record, hoặc interface giữ chữ ký): `config/SecurityConfig`, `AdminAnnouncementController`, `PublicClinicContentController`, `ContentApiExceptionHandler`, `ClinicContentDtoMapper`, `ClinicAnnouncement`, `ClinicContentRepository`, `ClinicContentService`, `ClinicContentServiceImpl`, `ClinicContentControllerTest`, `ClinicContentServiceImplTest`, `ScopedExceptionMappingTest`.
- **Ảnh:** `WebsiteImageValidator`, `WebsiteImageRepository`, `WebsiteImageJpaEntity`, `ClinicContentServiceImpl` không đổi (phương án A).
- **`notifications`:** FK duy nhất là `notifications_recipient_account_id_fkey` tới `accounts` (không bị bỏ). Không tham chiếu bảng nào bị bỏ (đã kiểm trên V108). Không đổi.
- **`patient_reviews`:** không đổi.

## Related Code Files
Đường dẫn Java tương đối với `backend/src/main/java/fu/fa26/sep490/g58/otcms_be/`. Test tương đối với `backend/src/test/java/fu/fa26/sep490/g58/otcms_be/`. Migration tương đối với `backend/src/main/resources/`.
- Modify:
  - `content/model/entity/WebsiteContentJpaEntity.java`: thêm `audience`, `recipientRole`.
  - `content/model/ContentType.java`: thêm `ANNOUNCEMENT`.
  - `content/service/impl/ContentInputValidator.java`: `contentType()` chặn `ANNOUNCEMENT`.
  - `content/repository/JpaClinicContentRepository.java`: constructor, `contents`, `content`, `announcements`, `announcement`, `insert(ClinicAnnouncement)`, `audit`.
  - `content/mapper/ClinicContentEntityMapper.java`: `announcement(...)` nhận `WebsiteContentJpaEntity`.
  - `review/service/impl/ReviewServiceImpl.java`: constructor, ba chỗ ghi lịch sử, `recordModeration`, record `ModerationState`. Thêm import `common.audit.AuditEntry`, `common.audit.AuditLogWriter`.
- Delete:
  - `content/model/entity/ClinicAnnouncementJpaEntity.java`, `content/repository/ClinicAnnouncementRepository.java`
  - `content/model/entity/ContentAuditJpaEntity.java`, `content/repository/ContentAuditRepository.java`
  - `review/model/entity/PatientReviewHistoryJpaEntity.java`, `review/repository/PatientReviewHistoryRepository.java`
- Move: `db/migration-staged/V113__final_db_reviews_content.sql` → `db/migration/`.
- Modify (test):
  - `content/ClinicContentPostgresIntegrationTest.java`: L102 đếm `content_audits` → `audit_logs WHERE source = 'content_audits' AND entity_id = ?`. L122 insert thông báo → insert `website_contents` (`type='ANNOUNCEMENT'`, `status='PUBLISHED'`, `summary=''`, `updated_at`, `updated_by`, `audience`, `recipient_role`). L127, L134, L174, L180 đếm `announcements` → `website_contents WHERE type = 'ANNOUNCEMENT'`. L175, L181 đếm `content_audits` → `audit_logs WHERE source = 'content_audits'`.
  - `content/LayeredArchitectureTest.java`: L50 bỏ `ClinicAnnouncementJpaEntity`; L54 bỏ `ClinicAnnouncementRepository` và `ContentAuditRepository`.
  - `content/ContentAuditMigrationOrderTest.java`: thêm `@Disabled("final-db: content_audits đã gộp vào audit_logs")` ở cấp class (đã lỗi có sẵn).
  - `review/ReviewServiceTest.java`: L27 mock `AuditLogWriter`; L35 constructor; L76-78, L130-133, L156-158: `ArgumentCaptor<AuditEntry>`, `verify(audit).record(...)`, đọc `action()`, `beforeState()`, `afterState()` (`ModerationState.status()`).
  - `review/ReviewControllerTest.java`: L125 thay `mock(PatientReviewHistoryRepository.class)` bằng `mock(AuditLogWriter.class)`.
  - `review/ReviewTextValidationHttpTest.java`: L53 mock; L68 constructor.
  - `review/ReviewPostgresIntegrationTest.java` (`@Transactional` cả lớp): mọi câu SQL trên `patient_review_moderation_history` (L101-103, L136-149, L247, L263-274, L285, L314, L320, L386, L415) đổi sang `audit_logs WHERE source = 'patient_review_moderation_history' AND entity_id = ?` (`review_id` thành `entity_id`). L314 và L320 vẫn phải ném lỗi (trigger phase 01). L353-360: đếm lại bảng, FK và index của bảng còn lại sau V113, ghi số mới.
  - `notification/NotificationPostgresIntegrationTest.java`: L380 đếm `patient_review_moderation_history` → `audit_logs WHERE source = 'patient_review_moderation_history' AND actor_account_id = ?`.

## Implementation Steps
1. Tạo nhánh `final-db-p09-reviews-content` từ `final-db` (phase 01 đã merge).
2. Chốt Q1 với NghiaDT. Nếu chọn B: dừng, không làm nhánh ảnh, báo lại để viết lại phase này.
3. Chốt Q2 (phase 01 có `occurredAt`). Chưa có thì làm bước 4–8 trước, nhưng không chạy test review.
4. `git mv` V113 sang `db/migration/`. So với mục 4; nếu staged lệch thì sửa staged và ghi "Đổi migration".
5. Sửa `WebsiteContentJpaEntity`, `ContentType`, `ContentInputValidator`, `ClinicContentEntityMapper`.
6. Sửa `JpaClinicContentRepository` theo mục 3.
7. Sửa `ReviewServiceImpl` theo mục 3.
8. Xoá sáu file ở mục Related Code Files.
9. Sửa test theo "Modify (test)". Không xoá test để xanh. `ContentAuditMigrationOrderTest` chỉ thêm `@Disabled`.
10. Reset DB Docker, chạy các truy vấn trong Success Criteria, rồi `./mvnw test`.
11. `compare-openapi.mjs`: không có khác biệt ở API thông báo, nội dung web, đánh giá.
12. Đi tay: tạo thông báo PUBLIC và STAFF; xem `/announcements` công khai; tạo bài viết có ảnh bìa; duyệt một đánh giá rồi mở nhật ký hoạt động.
13. Mở PR theo mẫu agent-rules §8. Ghi "Đổi migration" và số bảng sau đợt (34 với phương án A).

## Success Criteria
- [ ] `grep -rnE "ClinicAnnouncement(JpaEntity|Repository)|ContentAudit(JpaEntity|Repository)|PatientReviewHistory" backend/src/main/java backend/src/test` → rỗng.
- [ ] `grep -rnE "(from|into|update|join|table) (announcements|content_audits|patient_review_moderation_history)\b" backend/src/main/java backend/src/test` → rỗng.
- [ ] Sau V113: `SELECT proname FROM pg_proc WHERE prosrc ILIKE '%content_audits%' OR prosrc ILIKE '%announcements%' OR prosrc ILIKE '%patient_review_moderation_history%';` → 0 dòng.
- [ ] `\dt` không còn `announcements`, `content_audits`, `patient_review_moderation_history`. Ghi số bảng (34 với phương án A).
- [ ] `ClinicContentPostgresIntegrationTest`, `ReviewPostgresIntegrationTest`, `ReviewServiceTest`, `NotificationPostgresIntegrationTest` pass. `historyCannotBeUpdated` và `historyCannotBeDeleted` vẫn ném lỗi.
- [ ] Test mới hoặc đi tay: `type=ANNOUNCEMENT` tới `POST /api/v1/admin/website-contents` cho cùng kết quả như type lạ (`INVALID_CONTENT`); danh sách website không có dòng `ANNOUNCEMENT`; `GET` theo id thông báo như id không tồn tại.
- [ ] `./mvnw test`: chỉ còn lỗi có sẵn. `ContentAuditMigrationOrderTest` nay `@Disabled`, ghi trong PR.
- [ ] So OpenAPI: no differences.
- [ ] File ngoài danh sách: không. `NotificationPostgresIntegrationTest` dùng chung với phase khác: chỉ sửa đúng L380.

## Risk Assessment
- **Ảnh bìa (Q1):** chọn B buộc đổi API tải ảnh. Chọn A làm lệch plan.md: 34 bảng thay vì 33.
- **Thứ tự thời gian (Q2):** `ReviewPostgresIntegrationTest` là `@Transactional`, nên mọi `now()` trong một test trùng nhau. Nếu `occurred_at` do DB ghi thì `order by occurred_at, id` sai thứ tự. Vì vậy phase này cần `occurredAt` từ service clock.
- **Lộ thông báo ra API website:** `ANNOUNCEMENT` là giá trị enum mới, phải chặn ở ba chỗ: `ContentInputValidator.contentType`, `contents()`, `content(id)`. Thiếu một chỗ là thông báo xuất hiện ở API nội dung.
- **Phụ thuộc view:** nếu phase 01 chưa viết lại `activity_log_entries`, `DROP TABLE content_audits` lỗi `other objects depend on it`.
- **Mất FK của lịch sử đánh giá:** lịch sử kiểm duyệt không còn FK tới `patient_reviews` (audit không có FK theo thiết kế). Lịch sử vẫn còn, không hiện trong nhật ký.
- **Bất biến:** trigger `tr_content_audits_immutable`, `tr_content_audits_no_truncate` và `patient_review_history_append_only` mất cùng bảng. Bất biến (UPDATE, DELETE, TRUNCATE) do trigger `reject_audit_log_mutation` của phase 01 đảm nhận.
- **Test đếm FK và index:** `migrationTablesForeignKeysAndIndexesExist` đếm trên hai bảng vừa bỏ. Đếm lại trên DB, không giữ số cũ.
- **Hibernate `validate`:** `WebsiteContentJpaEntity` có cột mới, enum `ContentType` phải khớp CHECK. Chạy app profile `final-db` để kiểm.

## Câu hỏi còn mở

> **ĐÃ CHỐT 13/11:** Q1 chọn A: GIỮ `website_images` để giữ API tải ảnh, nên tổng thành 34 bảng (≤ 35); plan.md đã cập nhật. Q2 đã có `occurredAt` trong phase 01. Q3 đồng ý `ContentType.ANNOUNCEMENT`. Q4 đúng. Q5 đúng: ngoại lệ dải số migration của đợt refactor.

Các câu bên dưới giữ lại để tra lý do.
1. **(CHẶN) Ảnh bìa.** `POST /api/v1/admin/website-contents/images` lưu ảnh trước và trả `imageUrl`. Sau đó `POST` hoặc `PUT` nội dung gửi lại `imageUrl`, và `WebsiteImageValidator.imageId` kiểm `store.image(id)`. Ảnh tồn tại trước nội dung, nên cần chỗ giữ tạm.
   - **A (phase này làm theo, giữ API):** giữ `website_images`. Tổng 34 bảng (≤ 35). Đảo phần `website_images` của quyết định D6 (12/11). Cần duyệt và sửa plan.md mục 2.
   - **B (schema-33 mục 2.10):** gộp `cover_image bytea` và `cover_image_type` vào `website_contents`. Phải đổi API tải ảnh (FE gửi ảnh cùng lúc tạo nội dung), vi phạm "API giữ nguyên". Chỉ làm nếu NghiaDT cho đổi API.
2. **(CHẶN bước test review) `occurredAt`.** Phase 01 chưa có `occurredAt` trong `AuditEntry`. Đề nghị phase 01 thêm `Instant occurredAt` (mặc định `Instant.now(clock)`).
3. `ContentType.ANNOUNCEMENT` với ba chỗ chặn (đề xuất). Cách khác là đổi cột `type` thành String, sửa nhiều file hơn. Xác nhận.
4. `ContentAuditMigrationOrderTest` `@Disabled`: đã nằm trong danh sách lỗi có sẵn của AGENTS.md. Xác nhận.
5. Dải migration: AGENTS.md giao Hùng V044–V069; phase giao V113. Xác nhận ngoại lệ.
