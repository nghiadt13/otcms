---
phase: 2
title: "Tài khoản, xác thực, nhân sự: accounts, verification_codes, staff_profiles; bỏ Google và nghỉ phép"
status: pending
priority: P1
effort: "6h"
owner: NghiaDT
dependencies: [1]
migration: V110__final_db_accounts_auth_staff.sql
---

# Phase 02: Tài khoản, xác thực, nhân sự (V110)

## Overview
Đưa các bảng tài khoản và xác thực về đích:
- `accounts` nhận `account_avatars` và `account_consents`.
- `verification_codes` (mới) thay `email_verifications`, `email_change_challenges`, `password_resets`.
- `audit_logs` nhận `auth_audits` và `account_management_audits`.
- `staff_profiles` nhận `doctor_profiles`.

Đồng thời bỏ: đăng nhập Google, bảng `account_identities`, nghỉ phép bác sĩ (`doctor_time_off` và 5 endpoint).

Phase này sửa mọi chỗ gọi `DoctorProfileJpaEntity` và `DoctorProfileRepository` (package `staff`, `auth`, `appointment`, `visit`, `clinical`, `billing`, `patient`, `notification`, `persistence`, và test). Tên class `DoctorProfile*Service` giữ nguyên.

**5 quyết định D-a đến D-e ĐÃ CHỐT 13/11 theo cột đề xuất (giữ nguyên API, tiêu chí "tính năng vẫn chạy")** (mục Requirements). Phase này đề xuất lệch schema-33 ở các điểm đó vì API và hành vi hiện tại; bằng chứng nằm trong code.

## Requirements
- Chỉ bắt đầu sau khi phase 01 đã merge vào `final-db` (V109 có `audit_logs` và `AuditLogWriter`).
- API giữ nguyên, trừ các endpoint đã bỏ:
  - `GET /api/v1/admin/doctor-profiles/{doctorId}/time-offs` (lưu ý base path là `doctor-profiles`, không phải `doctors`).
  - `POST /api/v1/admin/doctors/{doctorProfileId}/time-offs`.
  - `PUT` và `DELETE /api/v1/admin/doctors/{doctorProfileId}/time-offs/{timeOffId}`.
  - `GET /api/v1/admin/doctors/{doctorProfileId}/time-offs/{timeOffId}/mutation-impact`.
  - `POST /api/auth/patient/google/exchange`.
- `POST /api/v1/admin/doctors/{doctorProfileId}/mutation-impact` giữ nguyên, nhưng `resourceType = TIME_OFF` bị từ chối (400, `DOCTOR_RESOURCE_REQUEST_INVALID`). `SHIFT` vẫn chạy.
- `SecurityConfig`: không đổi. Đã kiểm: không có dòng riêng cho Google; `/api/auth/**` đã `permitAll`.
- `lib/api/adminApiMessage.ts`: không sửa (AGENTS: chỉ thêm dòng). Các khoá `DOCTOR_TIME_OFF_*` còn lại vô hại.
- Dữ liệu demo: không cần giữ lịch sử Google hay nghỉ phép. Dữ liệu còn hiệu lực (OTP, token, ảnh, đồng ý, audit) phải được chép trước khi drop.

### Quyết định D-a…D-e (đã chốt 13/11: làm theo cột "Phase này làm")
| # | Schema-33 nói | Bằng chứng trong code (origin/product) | Phase này làm (đã chốt) |
|---|---|---|---|
| D-a | `status` ACTIVE/REMOVED thay `in_active_directory` | `PATCH /api/v1/admin/doctors/{id}/directory-state` đặt `inActiveDirectory` độc lập với trạng thái nhân sự. V036 ghi: "Directory visibility is separate from whether the employee profile is active." | Giữ cả `status` và `in_active_directory` |
| D-b | `consultation_information` gộp vào `biography` | `DoctorAdminResponse`, `DoctorPublicResponse` trả trường này; FE đọc ở 6 file (`features/staff-profiles/*`, `lib/domain/adminDoctor.ts`) | Giữ cột, không gộp |
| D-c | Tên và ảnh lấy từ `accounts`; bỏ `photo_content`, `photo_url` | Admin nhập tên, ảnh, URL cho hồ sơ qua API. Ảnh nhân viên đang ở `staff_profiles.photo_content`. Chuyển sang `accounts` làm nhân viên tự đổi ảnh hồ sơ qua `/api/v1/me/avatar`. Hiện `staff.display_name` và `accounts.full_name` có thể khác nhau | Giữ `display_name`, `photo_content`, `photo_content_type`, `photo_url` trong `staff_profiles`. Bytea không map vào entity (đọc qua native) |
| D-d | "Là bác sĩ" kiểm bằng `accounts.account_type` trong service | `StaffProfileAdminService.create` tạo hồ sơ cho mọi vai trò nội bộ (DOCTOR, RECEPTIONIST, LAB_TECHNICIAN, IMAGING_TECHNICIAN). Nếu gộp không có cờ, `DoctorProfileIdentityQueryServiceImpl` sẽ trả hồ sơ tiếp tân như bác sĩ | Thêm cột `is_doctor boolean`. Mọi truy vấn "bác sĩ" lọc `is_doctor = true` |
| D-e | `work_contact` 100 NOT NULL; có cột `version` | Hồ sơ bác sĩ có `work_contact` 254 và có thể NULL (`AdminDoctorProfileServiceImpl.update`). Code không dùng `@Version` | `work_contact varchar(254) NULL`; không thêm `version` |

Lý do chốt: giữ nguyên API và hành vi; schema-33 mục 2.3 đã ghi chú theo.

## Architecture

### A. `accounts` (sau V110)
Thêm cột, mặc định NULL cho mọi tài khoản cũ:
- `avatar_content bytea`, `avatar_content_type varchar(32)`, `avatar_updated_at timestamptz`. Nguồn: `account_avatars` (1-1).
- `terms_version varchar(64)`, `privacy_version varchar(64)`, `consent_actor_type varchar(16)`, `consent_accepted_at timestamptz`. Nguồn: dòng mới nhất của `account_consents` theo `accepted_at` (`DISTINCT ON (account_id)`).
- CHECK:
  - Ba cột avatar cùng NULL hoặc cùng có.
  - `avatar_content_type IN ('image/jpeg','image/png','image/webp')`.
  - `octet_length(avatar_content) BETWEEN 1 AND 2097152`.
  - `consent_actor_type IN ('PATIENT','GUARDIAN')`.
  - Bốn cột consent cùng NULL hoặc cùng có.
- `AccountJpaEntity` map: `avatarContentType`, `avatarUpdatedAt`, `termsVersion`, `privacyVersion`, `consentActorType` (`ConsentActorType`, `@Enumerated(STRING)`), `consentAcceptedAt`. **Không map `avatar_content`** (agent-rules 6.1).

### B. `verification_codes` (mới)
```
id uuid PK DEFAULT gen_random_uuid()
account_id uuid NOT NULL FK accounts(id) ON DELETE CASCADE
purpose varchar(24) NOT NULL CHECK IN ('EMAIL_VERIFY','EMAIL_CHANGE','PASSWORD_RESET','CARE_LINK','DEPENDENT_ADD')
code_hash varchar(255) NOT NULL                       -- OTP hash hoặc token hash, không lưu bản gốc
target_email varchar(254) NULL                        -- EMAIL_CHANGE: CHECK = lower(btrim(x)) AND length > 0; bắt buộc khi purpose = EMAIL_CHANGE
link_id uuid NULL FK patient_caregivers(id)           -- CARE_LINK (phase 06); bắt buộc khi purpose = CARE_LINK
payload jsonb NULL                                    -- DEPENDENT_ADD (phase 06). Phase 02 không ghi và không map
failed_attempts integer NOT NULL DEFAULT 0 CHECK (failed_attempts BETWEEN 0 AND 5)
expires_at timestamptz NOT NULL
consumed_at timestamptz NULL
superseded_at timestamptz NULL
created_at timestamptz NOT NULL DEFAULT now()
CHECK (expires_at > created_at)
UNIQUE partial (account_id, purpose) WHERE consumed_at IS NULL AND superseded_at IS NULL AND purpose IN ('EMAIL_VERIFY','EMAIL_CHANGE','PASSWORD_RESET')
UNIQUE partial (link_id, purpose) WHERE purpose = 'CARE_LINK' AND consumed_at IS NULL AND superseded_at IS NULL
UNIQUE partial (code_hash) WHERE purpose = 'PASSWORD_RESET'
INDEX (account_id, purpose, created_at DESC)          -- đếm giới hạn gửi lại; đồng thời là index cho FK account_id
INDEX (link_id) WHERE link_id IS NOT NULL             -- index cho FK
```
- Chép dữ liệu còn hiệu lực, giữ `id` và hash:
  - `email_verifications` sang EMAIL_VERIFY (`otp_hash` thành `code_hash`).
  - `email_change_challenges` sang EMAIL_CHANGE (`normalized_target_email` thành `target_email`).
  - `password_resets` sang PASSWORD_RESET (`token_hash` thành `code_hash`).
  - Giữ `failed_attempts`, `expires_at`, `consumed_at`, `superseded_at`, `created_at`.
- Giá trị `purpose` theo schema-33 (không dùng `EMAIL_VERIFICATION` trong tài liệu scan).
- Java: `VerificationCodeJpaEntity` (field `accountId` UUID, `purpose` là enum `VerificationPurpose`), `VerificationCodeRepository` gồm `findCurrentForUpdate(accountId, purpose)` (PESSIMISTIC_WRITE), `supersedeCurrent(accountId, purpose, now)` (@Modifying), `countByAccountIdAndPurposeAndCreatedAtAfter(...)`, `findAccountIdByCodeHash(hash, PASSWORD_RESET)`, `findByCodeHashForUpdate(hash, PASSWORD_RESET)`.

### C. `staff_profiles` (sau V110), gộp `doctor_profiles`
- Giữ nguyên: `id`, `account_id` (UNIQUE, FK `accounts` ON DELETE RESTRICT), `status` (ACTIVE | REMOVED), `created_at`, `updated_at`, `photo_content`, `photo_content_type`.
- Đổi: `display_name varchar(160)` (nới từ 100 để nhận `doctor_profiles.full_name`), `work_contact varchar(254) NULL` (bỏ NOT NULL).
- Thêm:
```
is_doctor boolean NOT NULL DEFAULT false                 -- D-d
in_active_directory boolean NOT NULL DEFAULT true        -- D-a: hiện trong danh bạ
publicly_visible boolean NOT NULL DEFAULT false
accepting_new_bookings boolean NOT NULL DEFAULT false
specialties text NOT NULL DEFAULT ''
qualifications text NOT NULL DEFAULT ''
biography text NOT NULL DEFAULT ''
consultation_information text NOT NULL DEFAULT ''        -- D-b
photo_url varchar(2048) NULL                             -- D-c: URL ảnh bác sĩ do admin nhập
CHECK (is_doctor OR (NOT publicly_visible AND NOT accepting_new_bookings))
INDEX ix_staff_profiles_public_directory (display_name, id) WHERE is_doctor AND publicly_visible AND in_active_directory
```
- Bỏ `doctor_profiles` sau khi chép. Không thêm `version` (D-e).
- Java: `StaffProfileJpaEntity` nhận các field trên. Copy annotation từ `DoctorProfileJpaEntity` (`columnDefinition = "text"`, `length = 2048`) để `ddl-auto: validate` qua.
  - Thêm method: `becomeDoctor(specialties, qualifications, biography, consultationInfo, publiclyVisible)` (đặt `doctor = true`, `inActiveDirectory = true`, `acceptingNewBookings = false`), `updateDoctor(...)`, `updateDirectoryState(publiclyVisible, acceptingNewBookings, inActiveDirectory)`, `updatePhotoUrl(String)`, `isDoctor()`.
  - Giữ: `update(name, contact)`, `remove()`, `photo(...)`, và constructor 4 tham số hiện có.
- **Quy tắc:** mọi chỗ trước đây dùng `DoctorProfileRepository` (chỉ có bác sĩ) chuyển sang `StaffProfileRepository` với điều kiện bác sĩ: `findByIdAndDoctorTrue`, `findByAccountIdAndDoctorTrue`, `existsByAccountIdAndDoctorTrue`, `existsByIdAndDoctorTrue`, `findAllDoctors()`, `findPublicDoctors()` (ORDER BY `display_name`, `id`), `findAcceptingDoctors()`. Không dùng `findById`/`existsById` trần cho bác sĩ.

### D. Dòng audit do V110 chép vào `audit_logs` (theo bảng ánh xạ phase 01)
| Nguồn (bảng cũ) | category | action | record_type | entity_id | visible | ghi chú |
|---|---|---|---|---|---|---|
| `staff_resource_audits`, `resource_type = DOCTOR_PROFILE` | STAFF_PROFILE | CREATED→`CREATE_STAFF_PROFILE`, khác→`UPDATE_STAFF_PROFILE` | DOCTOR_PROFILE | `resource_id` | true | `record_label` = `doctor_profiles.full_name` (chép trước khi drop) |
| `staff_resource_audits`, `resource_type = TIME_OFF` | STAFF_PROFILE | như trên | TIME_OFF | `resource_id` | false | nghỉ phép đã bỏ, giữ để tra |
| `account_management_audits` | ACCOUNT | `action` cũ | ACCOUNT | `account_id` | true | `record_label` = `account_id::text` |
| `auth_audits` | AUTH | `method` (bảng không có cột action) | AUTH | `account_id` | false | giữ `identifier_hmac`, `client_ip`, `user_agent`, `outcome`, `correlation_id`; `duration_ms` vào `metadata` `{"durationMs": n}` |
| `account_consents` (lịch sử) | CONSENT | `ACCEPT_CONSENT` | ACCOUNT | `account_id` | false | `metadata` `{termsVersion, privacyVersion, actorType}`; `occurred_at` = `accepted_at` |

- `staff_resource_audits`: **không xoá dòng** ở V110 (trigger `reject_staff_resource_audit_mutation` chặn DELETE). Bảng này do phase 10 drop ở V120 (plan.md mục 2), sau khi cả 02 và 07 đã chuyển sang `AuditLogWriter`. Các dòng `DOCTOR_PROFILE` còn lại chỉ là bản sao cũ; view không đọc nữa (phase 01).
- Không chép: `account_identities` (đã bỏ Google), `doctor_time_off` (đã bỏ nghỉ phép).
- Không ghi audit mới trong V110.

### E. Thứ tự V110 (một transaction; không dùng `IF EXISTS` để che lỗi thứ tự)
0. Chốt an toàn: `RAISE EXCEPTION` nếu có account có hai hồ sơ nhân sự khác `id` (`doctor_profiles.account_id` trùng `staff_profiles.account_id` nhưng `id` khác). Theo V035/V036 và code (`create` dùng chung một `id`), trường hợp này không xảy ra; check để chắc.
1. `ALTER accounts` (thêm cột và CHECK); `UPDATE` chép `account_avatars` và bản `account_consents` mới nhất.
2. `CREATE verification_codes` và index; `INSERT … SELECT` từ `email_verifications`, `email_change_challenges`, `password_resets`.
3. `ALTER staff_profiles` (thêm cột, đổi `display_name` và `work_contact`).
   - `UPDATE staff_profiles s … FROM doctor_profiles d WHERE d.id = s.id`: đặt `is_doctor = true`, `display_name = d.full_name`, copy các cột bác sĩ.
   - `INSERT` hồ sơ cho bác sĩ chưa có: `id = d.id`, `account_id = d.account_id`, `display_name = d.full_name`, `work_contact = d.work_contact`, `status = 'ACTIVE'`, `is_doctor = true`.
   - Tạo partial index.
4. Repoint 10 FK từ `doctor_profiles(id)` sang `staff_profiles(id)`, giữ tên constraint, `ON DELETE RESTRICT`:
   - `appointment_audits_new_doctor_profile_id_fkey`, `appointment_audits_old_doctor_profile_id_fkey` (phase 04).
   - `appointments_doctor_profile_id_fkey`, `appointments_ordering_doctor_profile_id_fkey` (phase 04).
   - `doctor_work_shifts_doctor_profile_id_fkey` (bảng `doctor_shifts`, phase 07).
   - `fk_doctor_service_assignments_doctor_profile` (bảng `doctor_services`, phase 07).
   - `staff_resource_management_audits_doctor_profile_id_fkey` (bảng `staff_resource_audits`).
   - `visit_care_assignments_doctor_profile_id_fkey` (bảng `visit_assignments`, phase 05).
   - `visit_clinical_examinations_doctor_profile_id_fkey` (bảng `visit_examinations`, phase 05 bỏ bảng này).
   - `visit_exam_queue_doctor_profile_id_fkey` (phase 05).
   - Chỉ đổi FK, không đổi cột. FK thứ 11 `doctor_operational_time_off_doctor_profile_id_fkey` đi cùng `doctor_time_off` ở bước 6.
5. `INSERT INTO audit_logs … SELECT` theo mục D. **Phải chạy trước bước 6** vì cần `doctor_profiles.full_name` và dữ liệu cũ.
6. `DROP TABLE`: `account_avatars`, `account_consents`, `account_identities`, `doctor_time_off`, `email_verifications`, `email_change_challenges`, `password_resets`, `auth_audits`, `account_management_audits`, `doctor_profiles`. Không drop `staff_resource_audits`.
7. Trigger và hàm: trigger gắn trên bảng bị drop mất theo bảng. Đã grep schema V108: không hàm plpgsql nào đọc các bảng này, nên không cần sửa hàm ở V110. Không đổi trigger.
- V110 phải chạy sau V109. View `activity_log_entries` do V109 viết lại không còn phụ thuộc `doctor_profiles` hay bảng audit cũ; nếu chưa viết lại, `DROP` sẽ báo lỗi phụ thuộc view.

### F. Xoá Google và nghỉ phép
**Xoá file (30 file, đã kiểm trên origin/product):**
- Google (9): `auth/controller/GoogleExchangeController.java`, `auth/exception/GoogleAuthenticationException.java`, `auth/integration/GoogleOidcClient.java`, `auth/integration/UnavailableGoogleOidcClient.java`, `auth/model/dto/request/GoogleExchangeRequest.java`, `auth/service/GoogleAuthenticationService.java`, `auth/service/GoogleOidcService.java`, `auth/service/impl/GoogleAuthenticationServiceImpl.java`, `config/GoogleOidcProperties.java`.
- Identity Google (3): `auth/model/entity/ExternalIdentityJpaEntity.java`, `auth/repository/ExternalIdentityRepository.java`, `auth/model/entity/enums/IdentityProvider.java`.
- Consent (2): `auth/model/entity/ConsentEvidenceJpaEntity.java`, `auth/repository/ConsentEvidenceRepository.java`.
- Avatar (2): `auth/model/entity/AccountAvatarJpaEntity.java`, `auth/repository/AccountAvatarRepository.java`.
- Audit auth và admin (4): `auth/model/entity/AuthenticationAuditJpaEntity.java`, `auth/repository/AuthenticationAuditRepository.java`, `auth/model/entity/AccountManagementAuditJpaEntity.java`, `auth/repository/AccountManagementAuditRepository.java`.
- OTP cũ (6): `auth/model/entity/EmailChangeChallengeJpaEntity.java`, `auth/repository/EmailChangeChallengeRepository.java`, `auth/model/entity/EmailVerificationChallengeJpaEntity.java`, `auth/repository/EmailVerificationChallengeRepository.java`, `auth/model/entity/PasswordResetChallengeJpaEntity.java`, `auth/repository/PasswordResetChallengeRepository.java`.
- Nghỉ phép và bác sĩ (4): `staff/model/entity/DoctorOperationalTimeOffJpaEntity.java`, `staff/repository/DoctorOperationalTimeOffRepository.java`, `staff/model/entity/DoctorProfileJpaEntity.java`, `staff/repository/DoctorProfileRepository.java`.
- `AuthExceptionHandler`: bỏ import (dòng 5) và handler `GoogleAuthenticationException` (dòng 40–47).
- `AuthSessionService`: đổi Javadoc dòng 12 (bỏ "ví dụ Google"). **Giữ** default method `create(accountId, ip, ua)`: 4 file test còn gọi (`AdministratorAccountDatabaseVerificationTest`, `Kan29AvatarHttpPostgresIntegrationTest`, `Kan29EmailChangeCollisionPostgresIntegrationTest`, `Kan29MyAccountPostgresIntegrationTest`).
- `AuthConfiguration` dòng 12: bỏ `GoogleOidcProperties.class`.
- `AuthProperties`: bỏ `googleExchange` (field, tham số constructor, getter, dòng 14, 20, 25, 35, 49–50, 61–63). Constructor 9 tham số và constructor tương thích 8 tham số đều bớt một `RateLimit`. 10 file test gọi constructor phải bớt một `null`.
- `application.yml`: bỏ block `app.auth.google` (dòng 104–111).

**Nghỉ phép (xoá/sửa):**
- Xoá `staff/model/entity/DoctorOperationalTimeOffJpaEntity.java`, `staff/repository/DoctorOperationalTimeOffRepository.java`.
- `DoctorProfileController`: bỏ 4 endpoint dòng 112–145 và import liên quan.
- `AdminDoctorProfileController`: bỏ endpoint `timeOffs` dòng 40–43.
- `AdminDoctorProfileService` dòng 14 và `AdminDoctorProfileServiceImpl` dòng 23, 28–30 (ctor), 45–51.
- `ManageDoctorResourcesService` dòng 14–16, 21; `ManageDoctorResourcesServiceImpl`: bỏ `createTimeOff`, `deleteTimeOff`, `updateTimeOff` (dòng 118–158), `assessTimeOffRemovalImpact` (187–196), `timeOffImpact` (198–206), `requireNoTimeOffOverlap` (269–271), nhánh `TIME_OFF` trong `assessImpact` (dòng 179 chỉ nhận `SHIFT`; dòng 181 bỏ). Bỏ tham số `timeOffs` ở cả hai constructor (dòng 45–58).
- `DoctorProfileExceptionHandler` dòng 16 và 20: bỏ `DOCTOR_TIME_OFF_NOT_FOUND` và `DOCTOR_TIME_OFF_OVERLAP`.

**Slot đặt lịch (không còn trừ giờ nghỉ):**
- `appointment/model/DoctorSchedule.java`: bỏ field, tham số, getter `timeOff` (dòng 10, 11, 14, 17, 23, 24). Constructor còn 2 tham số.
- `GenerateAvailableSlotsServiceImpl` dòng 63–67: `new DoctorSchedule(id, shifts…)`, bỏ tham số thứ ba.
- `SlotAvailabilityEngine`: xoá dòng 67 `|| intersects(occupiedInterval, schedule.timeOff())` và hàm `intersects` (dòng 96–…, không còn caller).
- `staff/service/StaffScheduleAvailabilityService.java`: bỏ `timeOff` (dòng 16–19, 22, 28–29); sửa Javadoc dòng 8.
- `StaffAvailabilityQueryServiceImpl.findSchedules` (dòng 80–93): bỏ `timeOffByDoctor` và `timeOff.findOverlapping`.
- `staff/service/StaffDirectoryQueryService.DoctorBookingProfile`: bỏ `coveredByOperationalTimeOff` (dòng 16, 17, 23, 39–41) → 5 tham số. `findDoctorByAccount` bỏ `timeOffOverlap` (dòng 65–68).
- `AppointmentOccupancyRepository.countBookingsOverlappingTimeOff` (dòng 32–40); `AppointmentMutationImpactService` dòng 10; `AppointmentMutationImpactServiceImpl` dòng 39–43 và comment dòng 25–29 (bỏ chữ "time off").
- `common/web/ProductionReadinessGate.java` dòng 81: bỏ điều kiện `time-offs/[^/]+/mutation-impact`.

**Frontend:** `frontend/src/features/staff-shifts/api/doctorScheduleApi.ts` (bỏ `"time-offs"` khỏi `ScheduleKind`, nhánh impact, `resourceType` TIME_OFF); `components/AdminDoctorScheduleLive.tsx` (option "Khoảng nghỉ" dòng 150, copy dòng 282–283, biến `noun`); hai file test tương ứng. Phase 07 cũng chạm thư mục này: merge 02 trước, 07 rebase.

### G. Java: thay đổi trọng yếu (before → after)
1. **Avatar** (`MyAccountQueryServiceImpl.toView`):
```java
// before
boolean hasAvatar = avatars.existsById(account.getId());
// after
boolean hasAvatar = account.getAvatarContentType() != null;
```
- `MyAccountServiceImpl.updateAvatar` và `removeAvatar`: một câu native `UPDATE accounts SET avatar_content=:content, avatar_content_type=:type, avatar_updated_at=:at WHERE id=:id` (`@Modifying(clearAutomatically = true, flushAutomatically = true)`). Xoá: `removeAvatar` ghi NULL cả ba cột.
- `avatar(actor, id)`: `accounts.findById` (kiểm `getAvatarContentType() != null`) và `accounts.findAvatarContent(id)` (native `select avatar_content`, trả `byte[]`).
- `AccountRepository` thêm ba native method tương ứng. Xoá `AccountAvatarRepository`.

2. **Giới hạn đăng nhập theo IP** (`CredentialLoginServiceImpl.enforceIpRate`; `RegistrationServiceImpl.enforceRate` làm tương tự):
```java
// before
audits.countByMethodAndOutcomeAndIdentifierHmacAndOccurredAtAfter("LOGIN_IP", "ATTEMPT", identifierHash, since)
// after
auditLogs.countBySourceAndActionAndOutcomeAndIdentifierHmacAndOccurredAtAfter(
        "auth_audits", "LOGIN_IP", "ATTEMPT", identifierHash, since)
```
- Phải có `outcome` trong hàm đếm. Hàm phase 01 `countBySourceAndActionAndIdentifierHmacAndOccurredAtAfter` không có outcome: nếu dùng, các outcome khác (ví dụ `RATE_LIMIT_ATTEMPT`) bị đếm chung. Phase 02 thay hàm đó trong `common/audit/repository/AuditLogRepository.java` và xoá hàm cũ (chưa có caller nào).
- Ghi log: `auditWriter.record(AuditEntry.builder("auth_audits", "AUTH", method, "AUTH")…)` với `visibleInActivityLog(false)`, `identifierHmac`, `outcome`. Tên setter theo `AuditEntry.Builder` của phase 01. `login`, `register`, `resendOtp` đã `@Transactional`, nên `MANDATORY` không lỗi.

3. **Đồng ý điều khoản** (`RegistrationServiceImpl`):
```java
// before (verifyEmail)
boolean consentExists = consents.existsByAccountIdAndActorTypeAndTermsVersionAndPrivacyVersion(
        account.getId(), ConsentActorType.PATIENT, properties.termsVersion(), properties.privacyVersion());
if (!consentExists) throw new RegistrationException("CONSENT_REQUIRED");
// after
boolean consentOk = account.getConsentActorType() == ConsentActorType.PATIENT
        && properties.termsVersion().equals(account.getTermsVersion())
        && properties.privacyVersion().equals(account.getPrivacyVersion());
if (!consentOk) throw new RegistrationException("CONSENT_REQUIRED");
```
- `register`: thay `consents.save(...)` bằng gán bốn trường trên `account` và `AuditLogWriter` (category CONSENT, `visibleInActivityLog(false)`).

4. **Hồ sơ nhân sự** (`StaffProfileAdminService`): bỏ `DoctorProfileRepository` khỏi constructor. `response(profile)` dùng `boolean doctor = profile.isDoctor()` thay cho `doctors.findById(...)`. Giữ đúng giá trị cũ khi không phải bác sĩ: `isPublic`, `info`, `acceptingNewBookings`, `inActiveDirectory`, link `/doctors/{id}` đều `null`; `actions` không có `togglePublic`.
```java
String photoUrl = profile.photoContent() == null ? (doctor ? profile.photoUrl() : null) : photoPath(profile.id());
```
- `create`: nếu role DOCTOR thì gọi `profile.becomeDoctor(...)` trước `saveAndFlush`.
- `update`: nếu DOCTOR thì `profile.updateDoctor(...)`; nếu có ảnh thì `updatePhotoUrl(photoPath(id))`.
- `remove`: `profile.remove()` và `profile.updateDirectoryState(false, false, false)`, cả hai như cũ.
- `photo(id, admin)`: người không phải admin cần `isDoctor() && publiclyVisible() && inActiveDirectory()`.

5. **Mapper:** `DoctorProfileMapper.toModel(StaffProfileJpaEntity)` và `toEntity` tạo `StaffProfileJpaEntity`. Giữ chữ ký của `DoctorProfile` (domain model không đổi).

6. **Ghi audit thay đổi hồ sơ bác sĩ** (`ManageDoctorResourcesServiceImpl.updateDirectoryState`): `AuditLogWriter` với category STAFF_PROFILE, action `UPDATE_STAFF_PROFILE`, record_type DOCTOR_PROFILE. `before` và `after` truyền `Map` (không truyền chuỗi JSON) để cột `jsonb` là object, đúng với dòng cũ.

## Related Code Files
Đường dẫn rút gọn: `B/` = `backend/src/main/java/fu/fa26/sep490/g58/otcms_be/`; `T/` = `backend/src/test/java/fu/fa26/sep490/g58/otcms_be/`. Đã kiểm trên `origin/product`.

- **Create (main):**
  - `B/auth/model/entity/VerificationCodeJpaEntity.java`, `B/auth/model/entity/enums/VerificationPurpose.java`, `B/auth/repository/VerificationCodeRepository.java`.
  - `backend/src/main/resources/db/migration/V110__final_db_accounts_auth_staff.sql`: `git mv` từ `db/migration-staged/`, sửa theo mục E trong PR này (ghi "Đổi migration").
- **Create (test):**
  - `T/auth/VerificationCodeRepositoryPostgresIntegrationTest.java`: unique partial theo purpose; supersede chỉ ảnh hưởng cùng purpose; password reset tra theo hash.
  - `T/staff/DoctorOnlyLookupTest.java`: hồ sơ nhân viên không phải bác sĩ (RECEPTIONIST) không được trả bởi `DoctorProfileIdentityQueryService`, danh bạ công khai, `GET /api/v1/doctors/me/shifts`.
- **Delete (main):** 30 file liệt kê ở mục F.
- **Modify (main):**
  - `B/auth/model/entity/AccountJpaEntity.java`, `B/auth/repository/AccountRepository.java` (mục A và G.1).
  - `B/auth/service/impl/MyAccountServiceImpl.java`, `MyAccountQueryServiceImpl.java` (G.1).
  - `B/auth/service/impl/RegistrationServiceImpl.java`: ctor bỏ `ConsentEvidenceRepository`, `EmailVerificationChallengeRepository`, `AuthenticationAuditRepository`; thêm `VerificationCodeRepository`, `AuditLogWriter`, `AuditLogRepository`. `enforceRate`, `recordOutcome`, `recordDeliveryOutcome` ghi qua `AuditLogWriter`. `sendNewOtp`, `canResend`, `issueReplacementOtp`, `verifyEmail` dùng purpose EMAIL_VERIFY. Consent theo G.3.
  - `B/auth/service/impl/CredentialLoginServiceImpl.java`: `audit()` và `enforceIpRate` (G.2).
  - `B/auth/service/impl/PasswordRecoveryServiceImpl.java`: purpose PASSWORD_RESET; `findAccountIdByTokenHash` → `findAccountIdByCodeHash`; `findByTokenHashForUpdate` → `findByCodeHashForUpdate`; `setTokenHash` → `setCodeHash`.
  - `B/auth/service/impl/EmailChangeServiceImpl.java`: purpose EMAIL_CHANGE; `setNormalizedTargetEmail` → `setTargetEmail`.
  - `B/auth/service/OwnerAdministratorService.java` (dòng 13, 35, 40, 97): `supersedeCurrent(accountId, PASSWORD_RESET, now)`.
  - `B/auth/service/impl/StaffAccountAdminServiceImpl.java` (dòng 40, 46, 160; `audit()` dòng 205–206 ghi qua `AuditLogWriter`).
  - `B/auth/service/impl/PatientAccountAdminServiceImpl.java` (dòng 86, 102 ghi qua `AuditLogWriter`; ctor bỏ `AccountManagementAuditRepository`).
  - `B/auth/service/AuthSessionService.java` (Javadoc dòng 12). `B/auth/exception/AuthExceptionHandler.java` (F).
  - `B/auth/config/DemoDoctorSeeder.java` (dòng 10–12, 108, 123–127): tạo `StaffProfileJpaEntity` + `becomeDoctor(...)`, tra bằng `findByAccountIdAndDoctorTrue`.
  - `B/config/AuthProperties.java`, `B/config/AuthConfiguration.java`, `backend/src/main/resources/application.yml` (F).
  - `B/common/audit/repository/AuditLogRepository.java` (do phase 01 tạo): thay hàm đếm, thêm `outcome`.
  - `B/staff/model/entity/StaffProfileJpaEntity.java` (mục C). `B/staff/repository/StaffProfileRepository.java` (thêm hàm bác sĩ; `findAccountSummaries` giữ nguyên).
  - `B/staff/mapper/DoctorProfileMapper.java` (G.5).
  - `B/staff/service/StaffProfileAdminService.java` (G.4).
  - `B/staff/service/impl/DoctorDirectoryServiceImpl.java`: `findPublicDoctors()`; `findByIdAndDoctorTrue` kèm filter.
  - `B/staff/service/impl/DoctorProfileIdentityQueryServiceImpl.java`, `DoctorProfileQueryServiceImpl.java`, `DoctorScheduleServiceImpl.java`: repo bác sĩ mới.
  - `B/staff/service/impl/StaffAvailabilityQueryServiceImpl.java`: bỏ `timeOff`; dùng repo bác sĩ; `displayName()` thay `fullName()`.
  - `B/staff/service/impl/AdminDoctorProfileServiceImpl.java`: `list()` dùng `findAllDoctors()` sắp `displayName, id`; `eligibleAccounts` dùng `findAllDoctors()`; `update()` dùng `updateDoctor` và đặt `displayName = fullName`.
  - `B/staff/service/impl/ManageDoctorResourcesServiceImpl.java`: E và G.6; `requireDoctor` dùng `existsByIdAndDoctorTrue`.
  - `B/staff/service/impl/ProvisionDoctorProfileServiceImpl.java`: nếu đã có hồ sơ nhân sự của account thì bật cờ bác sĩ, không insert; kiểm `DOCTOR_ACCOUNT_ALREADY_PROFILED` bằng `existsByAccountIdAndDoctorTrue`.
  - `B/staff/controller/AdminDoctorProfileController.java`, `B/staff/controller/DoctorProfileController.java`, `B/staff/service/AdminDoctorProfileService.java`, `B/staff/service/ManageDoctorResourcesService.java`, `B/staff/service/StaffScheduleAvailabilityService.java`, `B/staff/service/StaffDirectoryQueryService.java`, `B/staff/exception/DoctorProfileExceptionHandler.java` (E).
  - `B/appointment/model/DoctorSchedule.java`, `B/appointment/service/impl/GenerateAvailableSlotsServiceImpl.java`, `B/appointment/service/impl/SlotAvailabilityEngine.java`, `B/appointment/repository/AppointmentOccupancyRepository.java`, `B/appointment/service/AppointmentMutationImpactService.java`, `B/appointment/service/impl/AppointmentMutationImpactServiceImpl.java` (E).
  - `B/common/web/ProductionReadinessGate.java` (E).
  - `B/staff/service/StaffProfileAccountQueryService` và `StaffShiftAdminService` không đổi (vẫn đọc `display_name`).
- **Modify (FE):** 4 file trong `frontend/src/features/staff-shifts/` (F).
- **Modify (test):**
  - Xoá: `T/auth/T007GoogleAuthenticationServiceTest.java`, `T/auth/T007GoogleOnboardingPostgresIntegrationTest.java`.
  - `@Disabled("final-db: bảng … đã gộp")` (đọc bảng đã bỏ, copy cả V110): `T/auth/Kan27StaffAccountMigrationPostgresTest.java` (đã lỗi sẵn), `T/auth/Kan29MyAccountMigrationPostgresTest.java`.
  - Xoá test nghỉ phép hoặc impact: `T/staff/ManageDoctorResourcesT032Test.java`, `T/staff/AdminDoctorProfileServiceTest.java`, `T/staff/AdminDoctorProfileControllerTest.java`, `T/appointment/SlotAvailabilityT018PostgresIntegrationTest.java` (case `doctor_time_off`), `T/appointment/AppointmentPaymentHoldLifecyclePostgresIntegrationTest.java` (dòng 249, 254), `T/persistence/T032AdminResourceMutationPostgresIntegrationTest.java` (case nghỉ phép).
  - Sửa Google và OTP sang bảng mới: `T/auth/Kan29MyAccountPostgresIntegrationTest.java` (bỏ test Google dòng 192–236; `email_change_challenges` sang `verification_codes`), `T/auth/Kan29EmailChangeCollisionPostgresIntegrationTest.java`, `T/auth/T004PostgresIntegrationTest.java`, `T/auth/T005RealLoginIntegrationTest.java` (`auth_audits` sang `audit_logs`), `T/auth/T008PasswordRecoveryPostgresIntegrationTest.java`, `T/auth/Kan27StaffAccountPostgresIntegrationTest.java`, `T/auth/Kan27PatientManagementAuditTest.java`, `T/auth/MyAccountQueryServiceImplTest.java`, `T/auth/PasswordRecoveryFlowTest.java`, `T/auth/T010PasswordRecoveryControllerTest.java`, `T/auth/UC07CreateAdministratorAccountTest.java`, `T/auth/UC07FirstLoginSecurityTest.java`, `T/auth/UC08ManageAdministratorAccountTest.java`, `T/auth/UC09AdministratorAccountQueryTest.java`, `T/auth/service/impl/Kan27StaffAccountServiceTest.java`, `T/auth/EmailOtpReuseTest.java`, `T/auth/PatientRegistrationT004Test.java`, `T/auth/T005CredentialLoginServiceTest.java`, `T/auth/T008PasswordRecoveryServiceTest.java` (giữ case `googleOnly` dòng 84–87: đây là tài khoản không có mật khẩu, không phải code Google).
  - Ctor `AuthProperties` bớt một `null`: `T/auth/Kan27StaffLoginAndAccessTest.java`, `T/auth/UC07AdministratorLoginTest.java`, `T/auth/UC08ManageAdministratorAccountTest.java`, `T/patient/DependentAddOtpServiceTest.java`, `T/patient/DependentProfileServiceTest.java`. `T/auth/Kan27TestPropertiesTest.java` (bỏ `googleExchange()`).
  - Fixture SQL `INSERT INTO doctor_profiles` sang `staff_profiles` (`is_doctor = true`, đủ cột NOT NULL): khoảng 37 file, lấy danh sách bằng lệnh grep ở mục Success. Gồm `T/appointment/*` (~18, trừ `CheckInAuditT046…`), `T/billing/*` (3), `T/clinical/PastMedicalRecordsPostgresIntegrationTest`, `T/notification/NotificationPostgresIntegrationTest`, `T/patient/DependentsR3…`, `T/patient/MedicalHistoryR3…`, `T/persistence/T032…`, `T/staff/DoctorProfileT011PostgresIntegrationTest`, `T/visit/*` (8), và `backend/src/test/resources/reporting-fixtures.sql`.
  - Đổi mock và ctor sang repo staff: `T/clinical/DoctorCoreProductionWiringTest.java`, `T/clinical/DoctorImagingReviewIntegrationTest.java`, `T/clinical/PatientPrescriptionArchitectureTest.java`, `T/staff/DoctorDirectoryServiceImplTest.java`, `T/staff/ProvisionDoctorProfileServiceImplTest.java`, `T/staff/ManageDoctorResourcesT032Test.java`, `T/staff/DoctorPublicProfileControllerTest.java`.
  - Đổi chữ ký: `T/appointment/SlotAvailabilityEngineTest.java` (`DoctorSchedule` 3 → 2 tham số), `T/appointment/GetAppointmentsServiceImplT023Test.java` (`DoctorBookingProfile` 6 → 5 tham số), `T/staff/DoctorProfileT011PostgresIntegrationTest.java` (bỏ assert dòng 85).
  - **Không đổi:** `T/persistence/T017PersistenceCheckpointPostgresIntegrationTest.java` (chạy trên schema riêng tới V014), `T/appointment/CheckInAuditT046MigrationPostgresIntegrationTest.java` (target tới V026), `T/auth/Kan29AvatarHttpPostgresIntegrationTest.java` và `T/auth/Kan29MyAccountControllerTest.java` (chỉ gọi HTTP).

## Implementation Steps
1. Tạo nhánh `final-db-p02-accounts-auth-staff` từ `final-db` (đã có V109). Chốt D-a đến D-e với NghiaDT. Chưa chốt thì không làm bước 4 trở đi.
2. Viết V110 theo mục E trong `db/migration-staged/` (phase 00 đã có file thô; phase này là nguồn chuẩn). `git mv` sang `db/migration/`. Reset Docker DB rồi chạy Flyway: V001–V110 không lỗi. Kiểm `\dt`.
3. Kiểm trên DB sau V110 (xem Success): bảng cũ đã mất; không hàm nào tham chiếu bảng đã bỏ.
4. Entity và repository: thêm trường `AccountJpaEntity`, `StaffProfileJpaEntity`; tạo `VerificationCode*`; sửa `AuditLogRepository`.
5. Auth: avatar (G.1), consent và OTP (`RegistrationServiceImpl`, `EmailChangeServiceImpl`, `PasswordRecoveryServiceImpl`, `OwnerAdministratorService`, `StaffAccountAdminServiceImpl`), audit (`CredentialLoginServiceImpl`, `PatientAccountAdminServiceImpl`, `StaffAccountAdminServiceImpl`). Xoá Google (F).
6. Staff: chuyển mọi `DoctorProfile*` sang `StaffProfile*` theo quy tắc mục C; xoá nghỉ phép (F); `StaffProfileAdminService` (G.4).
7. Appointment slot và `ProductionReadinessGate` (F).
8. Config: `AuthProperties`, `AuthConfiguration`, `application.yml`. FE `staff-shifts`.
9. Chạy `./mvnw -q -DskipTests test-compile` cho đến khi hết lỗi. Lỗi biên dịch là danh sách chỗ còn gọi class đã xoá; sửa đúng các file trong danh sách Related Code Files.
10. Sửa test: fixture SQL, `@Disabled`, xoá test nghỉ phép và Google, đổi chữ ký. Viết 2 test mới.
11. Reset DB, `./mvnw test` (env theo AGENTS.md). Đối chiếu với lỗi có sẵn.
12. Chạy app profile `final-db`, `curl -s http://localhost:8080/v3/api-docs > /tmp/api-after.json`, rồi so OpenAPI (Success).
13. Chạy các lệnh grep "phải rỗng". Điền PR theo mẫu agent-rules mục 8 (ghi "Đổi migration", "File ngoài danh sách", "Ghi chú cho NghiaDT").

## Success Criteria
- [ ] Flyway V001–V110 trên DB Docker rỗng không lỗi. `\dt public.*` không còn: `account_avatars`, `account_consents`, `account_identities`, `doctor_time_off`, `email_verifications`, `email_change_challenges`, `password_resets`, `auth_audits`, `account_management_audits`, `doctor_profiles`. `staff_resource_audits` còn tới V112.
- [ ] `grep -rn -E "account_avatars|account_consents|account_identities|email_verifications|email_change_challenges|password_resets|auth_audits|account_management_audits|doctor_time_off|doctor_profiles" backend/src/main` → rỗng, trừ một dòng chú thích ở `DiagnosticRequestJpaEntity.java:26` (phase 03 sửa).
- [ ] `grep -rn -E "AccountAvatar|ConsentEvidence|ExternalIdentity|IdentityProvider|EmailVerificationChallenge|EmailChangeChallenge|PasswordResetChallenge|AuthenticationAudit|AccountManagementAudit|DoctorOperationalTimeOff|DoctorProfileJpaEntity|DoctorProfileRepository|Google|googleExchange" backend/src/main` → rỗng.
- [ ] `grep -rln -E "time-offs|TimeOff|time_off|coveredByOperationalTimeOff" backend/src/main frontend/src` → rỗng, trừ `lib/api/adminApiMessage.ts`.
- [ ] Không còn hàm plpgsql tham chiếu bảng đã bỏ: `SELECT proname FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND (prosrc ILIKE '%doctor_profiles%' OR prosrc ILIKE '%doctor_time_off%' OR prosrc ILIKE '%account_avatars%' OR prosrc ILIKE '%account_consents%' OR prosrc ILIKE '%account_identities%' OR prosrc ILIKE '%email_verifications%' OR prosrc ILIKE '%email_change_challenges%' OR prosrc ILIKE '%password_resets%' OR prosrc ILIKE '%auth_audits%' OR prosrc ILIKE '%account_management_audits%')` → rỗng.
- [ ] Test fixture: `grep -rln "INSERT INTO staff_profiles" backend/src/test` có mặt `is_doctor` ở mọi fixture bác sĩ.
- [ ] `./mvnw test`: không có lỗi mới. Lỗi có sẵn được phép: `AdministratorAccountDatabaseVerificationTest.upgradeFromV019…`, `T005RealLoginIntegrationTest.wrongPasswords…`, `ContentAuditMigrationOrderTest`. `Kan27StaffAccountMigrationPostgresTest` và `Kan29MyAccountMigrationPostgresTest` đã `@Disabled` có lý do. Hai test mới đạt.
- [ ] Giới hạn đăng nhập theo IP: 10 lần sai từ cùng IP rồi lần 11 trả `RATE_LIMIT_EXCEEDED`; các outcome khác không làm tăng bộ đếm (đã có `T005RealLoginIntegrationTest.loginIpLimit…`; chạy lại).
- [ ] OpenAPI: `node plans/261113-final-db/tools/compare-openapi.mjs plans/261113-final-db/api-baseline.json /tmp/api-after.json --allow "time-offs|/api/auth/patient/google/exchange|GoogleExchangeRequest"` → "no differences".
- [ ] Chạy tay trên profile `final-db`: `GET /api/v1/me` (avatarUrl đúng), `PUT`/`DELETE /api/v1/me/avatar`, `GET /api/v1/accounts/{id}/avatar`; `GET /api/v1/admin/staff-profiles` hiện đủ bác sĩ và nhân viên (đúng `isPublic`, `inActiveDirectory`); `GET /api/v1/doctors` chỉ hiện bác sĩ công khai; `GET /api/v1/admin/activity-logs` trả 200.

## Risk Assessment
- **Lệch schema-33 (D-a đến D-e):** nếu làm đúng schema-33 mà không thêm việc cross-module, `in_active_directory` và cờ danh bạ sẽ bị gộp sai nghĩa (bật/tắt danh bạ sẽ đặt nhân sự thành REMOVED), `consultation_information` trả rỗng, và ảnh nhân viên đổi theo ảnh tài khoản. Vì vậy phải chốt trước.
- **Tiếp tân bị coi là bác sĩ:** nếu quên `is_doctor`, mọi `findByAccountId` của `DoctorProfileIdentityQueryService` sẽ khớp hồ sơ nhân viên không phải bác sĩ. Test `DoctorOnlyLookupTest` chặn lỗi này.
- **Khối lượng test:** khoảng 40 file fixture đổi SQL. Dễ sót cột NOT NULL hoặc `is_doctor`. Nên làm theo lệnh grep và chạy `test-compile` sau mỗi nhóm. Thời lượng 6h có thể không đủ; báo sớm nếu vượt.
- **FK repoint 10 bảng của phase 04, 05, 07:** rebase sau sẽ xung đột ở migration. Người merge sau chạy lại V110 bằng cách reset DB, không sửa V110 đã merge.
- **Dữ liệu demo:** V110 và `DemoDoctorSeeder` tạo hồ sơ nhân sự cho bác sĩ demo, nên họ xuất hiện trong danh sách nhân sự và không còn ở `eligible-accounts`. Đây là đúng mô hình, ghi vào PR.
- **Tên hiển thị:** `doctor_profiles.full_name` ghi đè `staff_profiles.display_name` khi chép. Nếu hai tên đã khác nhau trong dữ liệu cũ, tên trong danh sách nhân sự sẽ đổi.
- **`ddl-auto: validate`:** cột `text` và `varchar` mới phải khớp annotation. Copy đúng từ `DoctorProfileJpaEntity`.
- **Mất dữ liệu có chủ đích:** lịch sử liên kết Google (`account_identities`) và lịch sử nghỉ phép (`doctor_time_off`). Ghi vào PR.
- **Hàm đếm IP cũ của phase 01:** nếu bỏ bước thay hàm đếm, đăng nhập bị giới hạn sai. Đã nêu ở G.2.

## Câu hỏi còn mở
> **Bổ sung 13/11 (từ phase 06/07):** giữ nguyên `DoctorProfileIdentityQueryService.findDoctorNames` (phase 07 dùng để lấy nhãn nhật ký ca bác sĩ). `verification_codes` phải có UNIQUE partial theo `link_id` cho `CARE_LINK` (đã ghi ở mục Architecture) và chừa purpose `DEPENDENT_ADD` cho phase 06.
1. **(Chặn)** Chốt D-a đến D-e. Mặc định là cột giữ trong bảng Architecture. Nếu NghiaDT chọn schema-33 cho D-c, cần thêm: hàm mới trong auth public API (`StaffAccountDirectory`) để đặt `fullName` và ghi/đọc avatar; staff đọc tên qua auth; và chấp nhận ảnh nhân viên trùng ảnh tài khoản (nhân viên tự đổi được).
2. Nếu chọn schema-33 cho tên: `accounts.full_name` đang `varchar(100)`, tên bác sĩ tới 160 ký tự. Cần nới cột này lên 160.
3. `frontend/src/features/admin-shell/adminNavigation.ts` còn mục "Ca trực và ngày nghỉ" và mô tả có chữ "ngày nghỉ". Giữ hay đổi? Ngoài phạm vi phase này.
4. `visit_examinations` (phase 05 bỏ bảng) vẫn có FK tới `doctor_profiles` ở V108. Phase 02 repoint FK này để V110 chạy. Phase 05 xác nhận bảng này bị bỏ hẳn.
5. (Đã chốt) `staff_resource_audits` do phase 10 drop ở V120; phase 02 không drop.
