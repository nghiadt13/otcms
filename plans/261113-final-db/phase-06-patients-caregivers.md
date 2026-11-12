---
phase: 6
title: "Hồ sơ BN, tiền sử, người thân (V111)"
status: pending
priority: P1
effort: "3h"
owner: Hùng
dependencies: [2]
migration: V111__final_db_patients_caregivers.sql
---

# Phase 06: Hồ sơ bệnh nhân, tiền sử, người thân (V111)

## Overview
- Gộp `patient_medical_histories` vào `patient_profiles` (8 cột). `patient_caregivers` giữ nguyên.
- Gộp `care_link_otp_challenges` (purpose `CARE_LINK`) và `dependent_add_requests` (purpose `DEPENDENT_ADD`) vào `verification_codes`, bảng do phase 02 tạo trong V110.
- Owner: Hùng. Chỉ làm sau khi phase 02 đã merge (mục 1). Thứ tự merge theo `plan.md` mục 3: sau 02 và 07, trước 05. Sau 07 vì cả hai cùng sửa `DependentsR3PostgresIntegrationTest`.
- API và hành vi giữ nguyên: URL, body, mã HTTP, mã MSG. FE không sửa.

## Requirements
- **R1 Tiền sử.** `GET` và `PUT /api/v1/patient-profiles/{id}/medical-history` giữ nguyên. Response có `version`. FE gửi lại `version` khi lưu (`features/patient-profiles/hooks/useMedicalHistory.ts`). `version = 0` nghĩa là tạo mới; khác 0 thì so khớp, lệch thì `STALE_VERSION`.
- **R1b Cột riêng.** Dùng cột `medical_history_version`, KHÔNG dùng chung `patient_profiles.version`. Lý do: `patient_profiles.version` là khoá lạc quan của `PatientProfileDirectoryServiceImpl.update`. Nếu dùng chung, sửa hồ sơ cơ bản làm tiền sử báo `STALE_VERSION` và ngược lại, tức là đổi hành vi API. Lệch với schema-33 mục 2.2, xem Câu hỏi 1.
- **R2 Người thân theo R3.** BR-16: chỉ quản lý, xem, thanh toán khi liên kết đã `CONFIRMED`; kết thúc thì mất quyền nhưng giữ hồ sơ khám. BR-17: liên kết hồ sơ đã có cần OTP gửi tới email trên hồ sơ, hoặc kiểm giấy tờ tại quầy. BR-19: chỉ xem, thanh toán cho bản thân và người thân đã được duyệt. UC-13 (R3 mục 2.3.4). Phase này KHÔNG đổi logic đó; chỉ đổi nơi lưu OTP.
- **R3 Payload.** `payload` của `DEPENDENT_ADD` (họ tên, ngày sinh, giới tính, CCCD, quan hệ) chỉ tồn tại khi còn cần: NULL khi đã dùng (`consumed_at`) hoặc đã hết hạn (`expires_at <= now`). Hệ quả với "Gửi lại mã": Câu hỏi 2.
- **R4 Không lộ dữ liệu nhạy cảm.** OTP, `code_hash`, CCCD và `payload` không đi vào audit, log hay response. Phase này không thêm audit mới.
- **R5 Rate-limit.** Giới hạn gửi lại giữ nguyên, nhưng đếm theo `purpose`. Đếm `CARE_LINK` không được tính `EMAIL_VERIFY` hay `PASSWORD_RESET` của cùng tài khoản.

## Architecture

### 1. Điều kiện đầu vào (DỪNG nếu thiếu)
- Phase 02 đã merge. `verification_codes` có: `id, account_id, purpose, link_id, target_email, code_hash, payload jsonb, failed_attempts, expires_at, consumed_at, superseded_at, created_at`.
- V110 (phase 02) phải có các ràng buộc sau. Phase 06 không sửa V110. Thiếu thì báo NghiaDT.
  - `UNIQUE (link_id) WHERE purpose = 'CARE_LINK' AND consumed_at IS NULL AND superseded_at IS NULL`. Thay cho `uk_care_link_otp_current` (V049).
  - `UNIQUE (account_id, purpose) WHERE purpose <> 'CARE_LINK' AND consumed_at IS NULL AND superseded_at IS NULL`.
  - Proposal (`db-target-schema-proposal.md` mục 2.1) ghi `UNIQUE (account_id, purpose)` cho mọi purpose. Điều đó SAI với `CARE_LINK`: một người nhận có thể có nhiều mã hiện hành trên nhiều link (`prepareOtp` chỉ thay mã của cùng một link).
  - Index `(purpose, link_id, created_at)` và `(purpose, account_id, created_at)` cho đếm rate-limit.
  - FK `link_id → patient_caregivers(id)`, `account_id → accounts(id)`.
  - CHECK `failed_attempts BETWEEN 0 AND 5`, `expires_at > created_at` (test `DependentsR3PostgresIntegrationTest` ~dòng 536-541 assert các ràng buộc này).
- Entity `VerificationCodeJpaEntity` (đường dẫn theo phase 02) có các field: `accountId, purpose, linkId, codeHash, expiresAt, failedAttempts, consumedAt, supersededAt, createdAt`. Nếu chưa có `payload`, phase 06 thêm `@JdbcTypeCode(SqlTypes.JSON) DependentAddPayload payload`.

### 2. patient_profiles: cột tiền sử (V111)
| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `conditions`, `allergies`, `procedures`, `injuries` | varchar(2000) | NOT NULL DEFAULT '' |
| `orthopedic_history` | varchar(2000) | NOT NULL DEFAULT '' |
| `medical_history_updated_at` | timestamptz | NULL |
| `medical_history_updated_by` | uuid | NULL, FK `accounts(id)` ON DELETE RESTRICT, index `ix_patient_profiles_medical_history_updated_by` (quy tắc 4b: FK phải có index) |
| `medical_history_version` | bigint | NOT NULL DEFAULT 0, CHECK `>= 0` |

- CHECK `ck_patient_profiles_medical_history_state`: `((medical_history_updated_at IS NULL) = (medical_history_version = 0)) AND ((medical_history_updated_at IS NULL) = (medical_history_updated_by IS NULL))`.
- V108 dump: không có trigger hay hàm nào trên `patient_profiles` hoặc `patient_medical_histories`.

Migration:
1. `ALTER TABLE patient_profiles ADD COLUMN ...` theo bảng trên.
2. Chép dữ liệu demo:
   ```sql
   UPDATE patient_profiles p
   SET conditions = h.conditions, allergies = h.allergies, procedures = h.procedures,
       injuries = h.injuries, orthopedic_history = h.orthopedic_history,
       medical_history_updated_at = h.updated_at, medical_history_updated_by = h.updated_by_account_id,
       medical_history_version = h.version
   FROM patient_medical_histories h WHERE h.patient_profile_id = p.id;
   ```
   Lệnh này không đụng `patient_profiles.version`.
3. `DROP TABLE patient_medical_histories;` (FK và index đi kèm).

### 3. verification_codes: purpose CARE_LINK và DEPENDENT_ADD
| Nguồn | purpose | Ánh xạ cột |
|---|---|---|
| `care_link_otp_challenges` | `CARE_LINK` | `id`; `recipient_account_id → account_id`; `link_id`; `otp_hash → code_hash`; `expires_at, failed_attempts, consumed_at, superseded_at, created_at` giữ nguyên; `payload` NULL; `target_email` NULL |
| `dependent_add_requests` | `DEPENDENT_ADD` | `id`; `requester_account_id → account_id`; `link_id` NULL; `otp_hash → code_hash`; cột thời gian giữ nguyên; `payload` như dưới |

`payload` khi chép (chỉ `DEPENDENT_ADD`), đúng quy tắc R3:
```sql
CASE WHEN d.consumed_at IS NULL AND d.expires_at > now() THEN
  jsonb_build_object('fullName', d.full_name, 'dateOfBirth', d.date_of_birth, 'gender', d.gender,
                     'nationalId', d.national_id, 'identityDocumentType', d.identity_document_type,
                     'relationship', d.relationship)
END
```

Ràng buộc và index thêm trong V111:
- `CHECK (payload IS NULL OR purpose = 'DEPENDENT_ADD')`
- `CHECK (payload IS NULL OR consumed_at IS NULL)`
- `CREATE INDEX ix_verification_codes_payload_expiry ON verification_codes (expires_at) WHERE payload IS NOT NULL;`

Trước khi chép, kiểm dữ liệu demo. Kết quả phải rỗng, nếu không thì DỪNG (vi phạm UNIQUE của V110):
```sql
SELECT requester_account_id FROM dependent_add_requests
WHERE consumed_at IS NULL AND superseded_at IS NULL GROUP BY 1 HAVING count(*) > 1;
```

Sau khi chép: `DROP TABLE care_link_otp_challenges; DROP TABLE dependent_add_requests;`. Không có trigger hay hàm nào tham chiếu hai bảng này (đã kiểm V108 dump).

### 4. Java: before → after (chỗ khó)
**(a) Tiền sử đọc thẳng từ entity cha.** `PatientProfileJpaEntity` (package `auth/model/entity`) thêm 8 field. Cả 8 field có `insertable = false, updatable = false`: Hibernate không ghi chúng, chỉ đọc. Ghi tiền sử đi qua native query ở (b). Như vậy việc lưu hồ sơ cơ bản không ghi đè tiền sử bằng giá trị cũ.
```java
@Column(nullable = false, length = 2000, insertable = false, updatable = false) private String conditions;
@Column(nullable = false, length = 2000, insertable = false, updatable = false) private String allergies;
@Column(nullable = false, length = 2000, insertable = false, updatable = false) private String procedures;
@Column(nullable = false, length = 2000, insertable = false, updatable = false) private String injuries;
@Column(name = "orthopedic_history", nullable = false, length = 2000, insertable = false, updatable = false) private String orthopedicHistory;
@Column(name = "medical_history_updated_at", insertable = false, updatable = false) private Instant medicalHistoryUpdatedAt;
@Column(name = "medical_history_updated_by", insertable = false, updatable = false) private UUID medicalHistoryUpdatedBy;
@Column(name = "medical_history_version", nullable = false, insertable = false, updatable = false) private long medicalHistoryVersion;
```
Không đụng `@Version long version` của hồ sơ.

**(b) Repository tiền sử** (`patient/repository/PatientMedicalHistoryRepository.java`): đổi generic thành `JpaRepository<PatientProfileJpaEntity, UUID>`. Giữ tên `insertInitial` và `updateIfVersion` và chữ ký, để service không đổi lời gọi. Hai câu SQL:
```sql
-- insertInitial (thay "INSERT … ON CONFLICT (patient_profile_id) DO NOTHING")
UPDATE patient_profiles SET conditions=:conditions, allergies=:allergies, procedures=:procedures,
       injuries=:injuries, orthopedic_history=:orthopedic, medical_history_updated_at=:now,
       medical_history_updated_by=:actor, medical_history_version=1
WHERE id=:profile AND medical_history_updated_at IS NULL
-- updateIfVersion
UPDATE patient_profiles SET conditions=:conditions, allergies=:allergies, procedures=:procedures,
       injuries=:injuries, orthopedic_history=:orthopedic, medical_history_updated_at=:now,
       medical_history_updated_by=:actor, medical_history_version=medical_history_version+1
WHERE id=:profile AND medical_history_version=:expected
```
Giữ `@Modifying(flushAutomatically = true, clearAutomatically = true)`.

**(c) Service tiền sử** (`PatientMedicalHistoryServiceImpl`):
```java
// before
return histories.findById(profileId).map(this::response).orElseGet(MedicalHistoryResponse::empty);
// after: chưa có tiền sử = updated_at NULL → trả empty() như cũ
return histories.findById(profileId).filter(p -> p.getMedicalHistoryUpdatedAt() != null)
        .map(this::response).orElseGet(MedicalHistoryResponse::empty);
```
`response(PatientProfileJpaEntity p)`: `row.getUpdatedByAccountId()` → `p.getMedicalHistoryUpdatedBy()`, `row.getUpdatedAt()` → `p.getMedicalHistoryUpdatedAt()`, `row.getVersion()` → `p.getMedicalHistoryVersion()`. Các getter tiền sử khác giữ tên. Chữ ký public và `update()` không đổi.

**(d) Người thân: giữ tên file, đổi entity và thêm purpose.** `CareLinkOtpChallengeRepository` và `DependentAddRequestRepository` đổi kiểu sang `VerificationCodeJpaEntity`. Mọi JPQL thêm `purpose`:
```java
// before
@Query("select c from CareLinkOtpChallengeJpaEntity c where c.linkId = :linkId and c.consumedAt is null and c.supersededAt is null")
// after
@Query("select c from VerificationCodeJpaEntity c where c.purpose = 'CARE_LINK' and c.linkId = :linkId and c.consumedAt is null and c.supersededAt is null")
```
Các method đếm theo tên derived đổi tên vì entity mới: `countByRecipientAccountIdAndCreatedAtAfter` → `countRecipientSince` (JPQL: `purpose='CARE_LINK' and accountId=:accountId and createdAt > :since`); `countByLinkIdAndCreatedAtAfter` → `countLinkSince`; `countByRequesterAccountIdAndCreatedAtAfter` → `countRequestsSince` (`purpose='DEPENDENT_ADD'`). Các lời gọi trong `DependentProfileServiceImpl.prepareOtp` và `DependentAddOtpServiceImpl.issue` đổi theo.

`DependentProfileServiceImpl` (chỉ các chỗ dùng OTP người thân):
```java
// before (prepareOtp)
var challenge = new CareLinkOtpChallengeJpaEntity();
challenge.setRecipientAccountId(profile.accountId());
challenge.setOtpHash(otp.hash("DELIVERY_PENDING"));
// after
var challenge = new VerificationCodeJpaEntity();
challenge.setPurpose("CARE_LINK");
challenge.setAccountId(profile.accountId());
challenge.setCodeHash(otp.hash("DELIVERY_PENDING"));
```
Trong `verify()`: `getRecipientAccountId()` → `getAccountId()`, `getOtpHash()` → `getCodeHash()`. Các lệnh `findCurrentForUpdate`, `findByIdForUpdate`, `activateIfCurrent` giữ tên, chỉ đổi điều kiện bên trong.

**(e) Payload trong `DependentAddOtpServiceImpl`.** Chỗ dễ sai: `toRequest(row)` phải chạy TRƯỚC khi xoá payload, vì `verify` còn cần dữ liệu để gọi `dependents.add`.
```java
// verify, nhánh đã hết hạn: xoá payload rồi commit, sau đó trả null như cũ
if (!row.getExpiresAt().isAfter(now)) { row.setPayload(null); requests.saveAndFlush(row); return null; }
if (row.getConsumedAt() != null || row.getSupersededAt() != null
        || row.getFailedAttempts() >= EmailOtpService.MAX_FAILED_ATTEMPTS) return null;
// verify, mã đúng
AddDependentRequest confirmed = toRequest(row);   // đọc trước
row.setConsumedAt(now); row.setPayload(null);     // rồi mới xoá
requests.saveAndFlush(row);
return confirmed;
```
- `resend`: sau kiểm `consumedAt`, thêm `if (row.getPayload() == null) throw new CareRelationshipException("CARE_LINK_NOT_AVAILABLE");` (Câu hỏi 2).
- `issue`: sau `guard.acquire()` và `now`, gọi `requests.clearExpiredPayloads(now)` (JPQL `update VerificationCodeJpaEntity c set c.payload = null where c.payload is not null and c.expiresAt <= :now`). Khi tạo dòng: `row.setPurpose("DEPENDENT_ADD"); row.setPayload(new DependentAddPayload(request.fullName(), request.dateOfBirth(), request.gender(), request.nationalId(), request.identityDocumentType(), request.relationship().name()));`.
- `toRequest(row)`: đọc từ `row.getPayload()` (`p.fullName()`, `p.dateOfBirth()`, `p.gender()`, `p.nationalId()`, `p.identityDocumentType()`, `Relationship.valueOf(p.relationship())`).

**(f) Record mới** `patient/model/DependentAddPayload.java`. Đặt ở package `patient` theo quy tắc "jsonb map sang record có kiểu":
```java
/** Người thân chờ OTP, lưu ở verification_codes.payload (jsonb). Không ghi vào audit hay log. */
public record DependentAddPayload(String fullName, LocalDate dateOfBirth, String gender, String nationalId,
        String identityDocumentType, String relationship) { }
```
`relationship` là `String` vì DB lưu `Relationship.name()`.

## Related Code Files
**Create**
- `patient/model/DependentAddPayload.java` (mục 4f).
- `backend/src/main/resources/db/migration/V111__final_db_patients_caregivers.sql`: `git mv` từ `migration-staged/`, sửa theo mục 2 và 3.

**Modify (main)**
- `auth/model/entity/PatientProfileJpaEntity.java`: thêm 8 field tiền sử (4a). Package `auth` cũng do phase 02 sửa: rebase và giữ dòng của mình.
- `patient/repository/PatientMedicalHistoryRepository.java`: đổi generic và SQL sang `patient_profiles` (4b).
- `patient/service/impl/PatientMedicalHistoryServiceImpl.java`: `read` lọc chưa có tiền sử, `response` đọc getter mới (4c).
- `patient/repository/CareLinkOtpChallengeRepository.java`: entity, `purpose`, đổi tên 2 method đếm (4d).
- `patient/repository/DependentAddRequestRepository.java`: entity, `purpose`, đổi tên method đếm, thêm `clearExpiredPayloads` (4d, 4e).
- `patient/service/impl/DependentProfileServiceImpl.java`: `prepareOtp` và `verify` (4d).
- `patient/service/impl/DependentAddOtpServiceImpl.java`: `issue`, `verify`, `resend`, `toRequest` (4e).
- `auth/model/entity/VerificationCodeJpaEntity.java` (file của phase 02): chỉ thêm field `payload` nếu thiếu.

**Delete**
- `patient/model/entity/PatientMedicalHistoryJpaEntity.java`
- `patient/model/entity/CareLinkOtpChallengeJpaEntity.java`
- `patient/model/entity/DependentAddRequestJpaEntity.java`

**Test (Modify)**
- `patient/MedicalHistoryR3PostgresIntegrationTest.java`: SQL trên `patient_medical_histories` sang `patient_profiles` (cột `medical_history_*`). Setup `insert` thành `update patient_profiles`. Đếm `9` cột (dòng ~371) thành đếm 8 cột mới trên `patient_profiles`. Test `migrationAppliesAfterV072OutOfOrderAndPreservesV049` (cuối test đọc bảng đã bỏ) thêm `@Disabled("final-db: patient_medical_histories đã gộp vào patient_profiles")` theo quy tắc 6.5. Không sửa nội dung để giả xanh.
- `patient/DependentsR3PostgresIntegrationTest.java`: `care_link_otp_challenges` sang `verification_codes where purpose='CARE_LINK'`, đổi `otp_hash → code_hash`, `recipient_account_id → account_id`. Dòng `doctor_shifts` (~362) thuộc phase 07.
- `patient/DependentAddOtpServiceTest.java`: type entity và repo; assert payload NULL sau khi dùng.
- `patient/DependentProfileServiceTest.java`: `CareLinkOtpChallengeJpaEntity` sang `VerificationCodeJpaEntity`, tên mock đổi.
- `patient/MedicalHistoryServiceTest.java`: mock `PatientMedicalHistoryJpaEntity` (dòng ~82) sang `PatientProfileJpaEntity`, stub getter mới.
- `patient/DoctorCurrentCareAuthorizationTest.java`: tên repo giữ nguyên, chỉ kiểm biên dịch.
- Không đổi: `patient/MedicalHistoryErrorWebTest.java` (chỉ mock service).
- 43 file test tham chiếu `patient_profiles` (35 file có `INSERT`). Các cột mới đều có DEFAULT nên các câu INSERT hiện tại vẫn chạy, không cần sửa.

## Implementation Steps
1. Kiểm mục 1: phase 02 đã merge, `\d verification_codes` đủ ràng buộc. Thiếu thì DỪNG và báo NghiaDT.
2. Chờ NghiaDT trả lời Câu hỏi 1 và 2 (chặn). Chưa có trả lời thì không làm mục 4e.
3. Tạo nhánh `final-db-p06-patients-caregivers` từ `final-db` mới nhất.
4. `git mv` V111 từ `migration-staged/` sang `db/migration/`. Sửa SQL theo mục 2 và 3.
5. Reset DB Docker (agent-rules mục 4), chạy app profile `final-db` đến V111 không lỗi. `\dt` không còn 3 bảng `patient_medical_histories`, `care_link_otp_challenges`, `dependent_add_requests`.
6. Làm tiền sử: 4a, 4b, 4c. Xoá `PatientMedicalHistoryJpaEntity`.
7. Làm người thân: 4d, 4e, 4f. Xoá 2 entity cũ.
8. Sửa 6 file test (mục Test). Thêm 6 test mới vào file test có sẵn, không tạo file mới.
9. Chạy các grep trong Success Criteria, `./mvnw test` (env theo `otcms/AGENTS.md`), so OpenAPI.
10. Mở PR theo mẫu agent-rules mục 8: "Đổi migration: không", "Ranh giới module: ghi chéo `verification_codes` (phase 02)".

## Success Criteria
- [ ] `grep -rln "patient_medical_histories\|PatientMedicalHistoryJpaEntity" backend/src/main/java backend/src/test` chỉ còn file test đã `@Disabled`.
- [ ] `grep -rln "care_link_otp_challenges\|dependent_add_requests\|CareLinkOtpChallengeJpaEntity\|DependentAddRequestJpaEntity" backend/src/main/java backend/src/test` rỗng.
- [ ] `SELECT proname FROM pg_proc WHERE prosrc ILIKE '%patient_medical_histories%' OR prosrc ILIKE '%care_link_otp%' OR prosrc ILIKE '%dependent_add_requests%';` rỗng.
- [ ] `\d patient_profiles` có 8 cột mới, đúng DEFAULT và CHECK. `\dt` không còn 3 bảng trên.
- [ ] Test mới đạt:
  - [ ] hai link khác nhau cùng một người nhận đều có mã hiện hành (regression của UNIQUE);
  - [ ] payload NULL sau khi dùng mã;
  - [ ] payload NULL khi hết hạn;
  - [ ] lưu tiền sử sau khi sửa hồ sơ cơ bản không báo `STALE_VERSION`;
  - [ ] chép dữ liệu demo giữ đủ số dòng tiền sử và người thân;
  - [ ] rate-limit `CARE_LINK` không đếm `EMAIL_VERIFY`.
- [ ] `./mvnw test`: chỉ còn lỗi có sẵn (agent-rules mục 7) và các test `@Disabled` đã ghi.
- [ ] So OpenAPI: no differences cho endpoint tiền sử và người thân.
- [ ] Đi tay: lưu tiền sử hai lần (version 1 rồi 2); thêm người thân có OTP, nhập sai đến 5 lần thì khoá.

## Risk Assessment
- **Dùng chung `version` đổi hành vi API.** Đã chọn cột riêng (Câu hỏi 1).
- **UNIQUE sai cho `CARE_LINK`** (proposal mục 2.1): INSERT thứ hai của cùng người nhận bị từ chối. Test "hai link cùng người nhận" bắt lỗi này.
- **Payload hết hạn làm hỏng "Gửi lại mã".** FE `CreateProfileForm` gọi `POST /api/v1/me/dependents/add-requests/{id}/resend`. Xem Câu hỏi 2.
- **Ghi chéo module.** `verification_codes` thuộc phase 02 (auth), còn patient ghi các purpose người thân. Nêu trong PR.
- **Rate-limit đếm thiếu `purpose`** sẽ đếm nhầm mã xác thực tài khoản, làm sai giới hạn.
- **Xung đột khi rebase.** `PatientProfileJpaEntity` (02 cũng sửa auth), `DependentsR3PostgresIntegrationTest` (07 cũng sửa). Giữ dòng của mình.
- **Record `DependentAddPayload` ở package `patient`** còn entity ở `auth`. Phụ thuộc vòng giữa package, chấp nhận vì cùng một module.
- **Chép dữ liệu demo** lỗi nếu một requester có hai mã hiện hành. Pre-check ở mục 3.

## Câu hỏi còn mở

> **ĐÃ CHỐT 13/11 (tiêu chí: giữ API + tính năng đang chạy):** Q1 dùng cột riêng `medical_history_version`. Q2 `payload` chỉ set NULL khi mã được dùng hoặc bị thay bằng mã mới, KHÔNG xoá lúc hết hạn, để "Gửi lại mã" của FE vẫn chạy; dọn payload hết hạn khi có yêu cầu mới như đề xuất, không thêm job. Q3 GIỮ luồng `DEPENDENT_ADD` vì đang chạy; lệch R3 UC-13 đã báo NghiaDT để xem lại R3, không sửa trong đợt này. Q4 entity `VerificationCodeJpaEntity` nằm ở package `auth` (phase 02); phase 06 có repository riêng trong `patient` trên cùng entity, được phép. Q5 không thêm job.

Các câu bên dưới giữ lại để tra lý do.
1. **(Chặn)** Version tiền sử: dùng cột riêng `medical_history_version` (đề xuất, giữ đúng `STALE_VERSION`) hay dùng chung `patient_profiles.version` như schema-33 mục 2.2? Hệ quả của phương án dùng chung đã ghi ở Risk.
2. **(Chặn)** Gửi lại sau khi hết hạn. Theo yêu cầu R3, payload NULL khi hết hạn, nên `resend` sau hết hạn trả `CARE_LINK_NOT_AVAILABLE`, và FE phải cho nhập lại form, trái với ràng buộc "FE không phải sửa". Phương án khác: giữ payload đến khi dùng hoặc đến khi mã bị thay thế, rồi xoá khi quá hạn cộng cửa sổ gửi lại (`auth.resend().windowSeconds()`). Chọn phương án nào?
3. Luồng `DEPENDENT_ADD` (OTP gửi về email của người thêm, trước mọi lần thêm) không có trong R3 UC-13. Bước 5 của UC-13 nói hồ sơ mới không cần OTP, còn AF1 gửi OTP về email trên hồ sơ có sẵn. Luồng này có thể là quy tắc của spec D03 đã bỏ (memory 01/11). Phase này giữ nguyên hành vi. NghiaDT quyết giữ hay bỏ.
4. `verification_codes` thuộc phase 02 (auth) hay patient? Phase 06 đọc và ghi các dòng người thân từ package `patient`.
5. Dọn payload hết hạn: đề xuất dọn khi có yêu cầu thêm người thân mới, không thêm job theo lịch. Có cần job theo lịch không?
