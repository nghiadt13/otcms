# UC08 / UC09 — Clinic Owner quản lý Administrator accounts

## Phạm vi và nguồn yêu cầu

Nguồn: `docs/requirements/UC_description.html`, requirement UC08/UC09 người dùng cung cấp
và clarification UC07: initial status ACTIVE. Người dùng đã yêu cầu tự triển khai, review,
sửa lỗi kỹ thuật và chạy regression liên tục. Chỉ thay đổi backend; không commit/push/deploy.
Giữ convention controller → service interface → service.impl → repository → JPA entity hiện có.
UC07 cùng các thay đổi chưa commit có từ đầu task được giữ nguyên ngoài phần reuse/security cần thiết.

## Cấu trúc hiện tại sau minimal refactor

```text
auth/controller/OwnerAdministratorController
    → auth/service/OwnerAdministratorService
        → auth/repository/AccountRepository
            → auth/model/entity/AccountJpaEntity
```

Controller chứa sáu endpoint hiện có, nhận/trả DTO và gọi service; không chứa business logic.
Service có sáu method `create`, `activate`, `deactivate`, `reissuePassword`, `list`, `detail`.
Query ở repository; mapper chỉ expose các field an toàn. Request/response, URL, status code,
CSRF/OWNER/ADMIN policy và database schema giữ nguyên.

- Ba controller create/management/query cũ gộp thành OwnerAdministratorController.
- Ba service interface + ba implementation create/management/query cũ gộp thành một concrete
  OwnerAdministratorService có @Service, không thêm facade hoặc interface trung gian.
- AdministratorAccountAuthorization chuyển thành private methods trong service; giữ cả OWNER
  guard và target ADMIN guard. AdministratorInitialStatusPolicy bỏ, create gán trực tiếp ACTIVE.
- Giữ AdministratorPasswordIssuer/TemporaryPasswordGenerator/PasswordEncoder vì create và reissue
  dùng chung secure issuance. Giữ mapper, repositories, DTO/entity/enum, exception handler, JWT filter,
  AccountAccessService, StaffAuthenticationService, session revoke, transaction/locking và migrations.
- Các danh sách class ở phần implementation lịch sử bên dưới mô tả trước refactor; cấu trúc tại mục này
  là hiện tại. Không còn các class controller/service/policy/authorization cũ trong source.

## Shadow Plan

1. Đọc lại Account, repository, password generator/encoder/policy, login, session, security, enum,
   pagination và SRS. Không thêm role hay status mới.
2. UC08: chia sẻ issuance/authorization với UC07; activate/deactivate/reissue chỉ target ADMIN;
   transaction, revoke session, supersede reset link và first-login restriction.
3. Review UC08, sửa lỗi kỹ thuật HIGH, chạy UC08 + UC07/auth; chỉ sang UC09 khi non-DB suite pass.
4. UC09: query cố định ADMIN tại database, search/filter/page và DTO allowlist cho list/detail.
5. Review UC09, compile, chạy toàn bộ non-DB regression và PostgreSQL integration có thể chạy.
6. Ghi kết quả và kiểm tra diff, không thay artifact approval thay Human.

## Contract

Base URL: `/api/v1/owner/administrator-accounts`.

| Method | Path | Request | Success |
| --- | --- | --- | --- |
| POST | /{accountId}/activate | Không có body | 200 AdministratorAccountResponse |
| POST | /{accountId}/deactivate | Không có body | 200 AdministratorAccountResponse |
| POST | /{accountId}/password-reissues | Không có body | 200 ReissueAdministratorPasswordResponse, no-store |
| GET | / | query?, status?, page=0, size=20 | 200 Page<AdministratorAccountResponse>, no-store |
| GET | /{accountId} | Không có body | 200 AdministratorAccountResponse, no-store |

UC07 `POST /` giữ nguyên contract, username và initial ACTIVE.
Các thao tác ghi yêu cầu CSRF theo SecurityConfig hiện có.

Query `query` tìm substring username/email không phân biệt hoa thường; ký tự %, _ và !
được escape để giữ nghĩa literal. SQL parameter binding, không nối input thành SQL.
`status` dùng enum hiện tại, ACTIVE hoặc DISABLED tương ứng thao tác activate/deactivate.
Không thêm trạng thái Inactive mới. Page zero-based, size 1..50 reuse PatientProfileServiceImpl;
sort createdAt DESC, id DESC để ổn định thứ tự khi phân trang. Query rỗng trả danh sách.
Tham số role/accountType không thể làm thay đổi scope ADMIN trong repository.

Ví dụ activate/deactivate/detail response (timestamp và UUID chỉ minh họa):

```json
{
  "accountId": "11111111-1111-4111-8111-111111111111",
  "username": "clinic.admin",
  "email": null,
  "accountType": "ADMIN",
  "status": "ACTIVE",
  "createdAt": "2026-10-23T00:00:00Z",
  "updatedAt": "2026-10-23T01:00:00Z"
}
```

Deactivate trả cùng DTO với `status=DISABLED`.
Ví dụ reissue response (placeholder, không phải credential thật):

```json
{
  "accountId": "11111111-1111-4111-8111-111111111111",
  "temporaryPassword": "<one-time-issued-password>",
  "mustChangePassword": true
}
```

Ví dụ list/search request:
`GET /api/v1/owner/administrator-accounts?query=clinic&status=ACTIVE&page=0&size=20`.
Response là Page theo convention hiện có: content chứa DTO an toàn ở trên, cùng number, size,
totalElements, totalPages và metadata Page khác. Không nhận role để mở rộng scope.

## Authorization matrix

| Caller | Target | UC08 actions | UC09 list/detail |
| --- | --- | --- | --- |
| Authenticated OWNER | ADMIN | Cho phép | Cho phép |
| Authenticated OWNER | Account role khác hoặc không tồn tại | 404, không ghi dữ liệu | 404 detail; loại khỏi list |
| Authenticated non-OWNER | Bất kỳ | 403 | 403 |
| Anonymous | Bất kỳ | 401 | 401 |

Controller dùng @PreAuthorize OWNER. Service kiểm tra OWNER độc lập.
OwnerAdministratorService kiểm tra OWNER và target ADMIN bằng private methods.
Repository list cố định ADMIN; detail dùng id AND accountType ADMIN.
Không có API đổi role/delete. Không tạo/xóa/sửa staff profile hay linked records.
Các role chưa có trong enum source cũng không thể đi qua predicate ADMIN.

## Credential và trạng thái

- Activate chỉ set ACTIVE và updatedAt; deactivate set DISABLED và updatedAt, revoke session.
- Reissue giữ nguyên status/role/profile. Không tự activate hay đặt thêm rule account.
- AdministratorPasswordIssuer được cả UC07/UC08 reuse, gọi TemporaryPasswordGenerator và
  PasswordEncoder hiện có, chỉ ghi passwordHash và mustChangePassword=true lên entity.
- Raw chỉ có trong biến cục bộ và create/reissue result; không có cột raw password,
  không có endpoint đọc lại, không log credential. Credential DTO toString redact.
- Safe response mapper chỉ trả accountId, username, email, accountType, status, createdAt, updatedAt.
  Không trả hash, temporary password, token, lockout/counter, reset challenge hay credential state.
- Login response chỉ giữ mustChangePassword; raw password không trả lại trong login/list/detail/status.
- Reissue supersede reset challenge cũ và revoke mọi session qua mechanism hiện có trong cùng transaction.
- AccountAccessService kiểm tra tất cả ADMIN, kể cả email-based legacy ADMIN không có username.
  JWT của session đã revoke hoặc account không ACTIVE trả 401.
- Issued-password session chỉ được change-password/logout/csrf theo UC07; chức năng khác trả
  403 PASSWORD_CHANGE_REQUIRED. Refresh cũng bị chặn khi mustChangePassword=true.
- Credential verification và session create của staff nằm trong cùng transaction;
  account row lock được giữ đến lúc session persist.
- Refresh/reset lấy account ID bằng scalar, khóa account trước khi hydrate session/challenge;
  JWT admission/refresh network lookup dùng projection/scalar, tránh entity cache trước khóa.
  Lock order account → session/challenge nhất quán với management.
- Login thất bại không đổi DISABLED thành TEMPORARILY_LOCKED; reset link cũ không kích hoạt
  account đã disable. Đây là sửa bypass kỹ thuật của UC08, không thêm nghiệp vụ.

## Migration

Migration mới duy nhất: `V021__allow_legacy_administrator_password_reissue.sql`.

```sql
ALTER TABLE accounts DROP CONSTRAINT ck_accounts_issued_admin_username;
```

V020 ràng buộc ADMIN mustChangePassword=true phải có username, nhưng legacy ADMIN có thể chỉ login email.
UC08 không yêu cầu tạo username cho account cũ. V021 chỉ bỏ CHECK đó để reissue dùng được cho legacy ADMIN.
Không sửa V020 đã có; không thêm/xóa cột, không sửa data/role/status/profile; username UNIQUE,
nonblank khi có giá trị, email-required logic và default must_change_password của V020 vẫn giữ.
UC07 tiếp tục bắt buộc username tại request/service.
Migration chưa được thực thi thành công vì PostgreSQL local từ chối kết nối.

## Self-review và fix

| Mức | Finding kỹ thuật | Xử lý |
| --- | --- | --- |
| HIGH | Constraint issued-admin username chặn reissue legacy ADMIN | V021 |
| HIGH | JWT guard chỉ kiểm tra username ADMIN khiến legacy ADMIN bỏ qua revoke/status | Kiểm tra mọi ADMIN |
| HIGH | Failed login/reset link có thể bỏ qua deactivate | Giữ DISABLED và reject reset cho status ineligible |
| HIGH | Login/session, refresh, reset có thể race với reissue | Transaction chung, account row lock, lock order và projection/scalar trước khóa |
| CRITICAL/HIGH | UC09 role/data exposure, input, DTO và pagination | Không còn finding sau review; predicate ADMIN + safe DTO + backend guards |

Không tự approve thay Human. Self-review kỹ thuật hoàn tất; không còn NEEDS_CLARIFICATION nghiệp vụ.
Giới hạn evidence: chưa xác minh runtime JPQL, row locking và migration trên PostgreSQL thật.

## Verification / Action Record

Scope/file boundary là danh sách bên dưới, không frontend hoặc skill/governance artifact.
Checkpoint là instruction mới nhất cho phép tự tiếp tục các bước kỹ thuật.

Compile command đã chạy:

```powershell
.\mvnw.cmd -DskipTests test-compile
```

Kết quả PASS; các lệnh test cuối cũng chạy compile/testCompile thành công.

Exact non-DB suite (redirect output vào target/uc08-uc09-unit-regression.log):

```powershell
.\mvnw.cmd '-Dtest=UC07CreateAdministratorAccountTest,UC07AdministratorAccountControllerTest,UC07AdministratorLoginTest,UC07FirstLoginSecurityTest,UC08ManageAdministratorAccountTest,UC08AdministratorAccountControllerTest,UC08StaffAuthenticationTest,UC09AdministratorAccountQueryTest,UC09AdministratorAccountControllerTest,PatientRegistrationT004Test,T005ContractTest,T005CredentialLoginServiceTest,T006SecurityContractTest,T006HttpSecurityEvidenceTest,T006A12EvidenceTest,T007GoogleAuthenticationServiceTest,T008PasswordRecoveryServiceTest,T009SmtpEmailDeliveryTest,T010PasswordRecoveryControllerTest,T010CommonSecurityContractTest,SessionCsrfWebMvcTest' test
```

| Suite | Tests | Result |
| --- | ---: | --- |
| UC07 regression | 17 | PASS |
| UC08 | 17 | PASS |
| UC09 | 14 | PASS |
| Auth/security regression không cần DB | 45 | PASS |
| Tổng | 93 | 0 failure, 0 error, 0 skipped |

UC08 service tests: status/role/account preservation, caller/target restriction, hash/no log/response redaction,
old password rejected/new password accepted, first-login restriction, revoke access/refresh, legacy account,
disabled bypass và status preservation. MVC tests: OWNER, all non-OWNER, CSRF, bad UUID, non-ADMIN404,
response/no-store, không có delete. Staff authentication tests: orchestration, credential denial,
cookie/login response và staff network admission.

UC09 service tests: list DTO allowlist, search/filter/pagination, literal wildcard escaping, role-scoped detail,
missing/non-ADMIN targets, all non-OWNER, invalid paging. MVC tests: OWNER/list/detail, query params,
ignored role selection, permission errors, non-ADMIN404, invalid types, credential absent after create/reissue.
PostgreSQL tests bổ sung kiểm tra migration legacy ADMIN, actual session revoke, role-filtered query,
pagination và escaped LIKE.

Exact final PostgreSQL selection:

```powershell
.\mvnw.cmd '-Dtest=UC07AdministratorAccountPostgresIntegrationTest,UC08AdministratorAccountPostgresIntegrationTest,UC09AdministratorAccountPostgresIntegrationTest,T006PostgresIntegrationTest,T008PasswordRecoveryPostgresIntegrationTest,T006A8EvidenceTest' test
```

Result được ghi trong target/uc08-uc09-postgres-final.log và Surefire reports.
Lượt cuối: 15 tests, 0 assertion failures, 15 context errors. UC07/UC08/UC09 có 2 tests mỗi UC;
T006PostgresIntegrationTest có 4, T008PasswordRecoveryPostgresIntegrationTest có 1,
T006A8EvidenceTest có 4. Các context errors đều bắt nguồn từ lỗi kết nối dưới đây.
PostgreSQL `127.0.0.1:5432 connection refused`: ENVIRONMENT FAILURE; assertions chưa chạy.
Không coi đây là lỗi assertion UC07/UC08/UC09 và không sửa production để né môi trường.

`git diff --check`: PASS sau code changes và sau tài liệu. Đã chạy git diff --stat và git status --short -uall.
Không có test code failure còn tồn tại. Failure ban đầu của UC08 là mock thiếu stub token-family query,
đã sửa fixture và chạy lại. T006A8EvidenceTest cần PostgreSQL dù tên không có Postgres:
4 context errors trong suite mở rộng được phân loại ENVIRONMENT FAILURE và tách khỏi non-DB selection.

Sync-back: tài liệu này cập nhật implementation/evidence; không đổi architecture, enum, API cũ hoặc approval
của artifact trước. Residual blocker duy nhất: PostgreSQL runtime verification.
Human Final Review: PENDING HUMAN REVIEW; agent không ghi quyết định APPROVED thay người dùng.

## File tạo trong task UC08/UC09 (23)

- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/controller/AdministratorAccountManagementController.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/controller/AdministratorAccountQueryController.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/mapper/AdministratorAccountMapper.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/model/dto/AccountAccessSnapshot.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/model/dto/response/AdministratorAccountResponse.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/model/dto/response/ReissueAdministratorPasswordResponse.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/ManageAdministratorAccountService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/AdministratorAccountQueryService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/StaffAuthenticationService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/AdministratorAccountAuthorization.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/AdministratorPasswordIssuer.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/ManageAdministratorAccountServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/AdministratorAccountQueryServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/StaffAuthenticationServiceImpl.java`
- `src/main/resources/db/migration/V021__allow_legacy_administrator_password_reissue.sql`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/UC08ManageAdministratorAccountTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/UC08AdministratorAccountControllerTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/UC08StaffAuthenticationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/UC08AdministratorAccountPostgresIntegrationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/UC09AdministratorAccountQueryTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/UC09AdministratorAccountControllerTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/UC09AdministratorAccountPostgresIntegrationTest.java`
- `docs/uc08-uc09-administrator-accounts.md`

## File sửa so với trạng thái đầu task (15)

- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/controller/StaffLoginController.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/exception/AuthExceptionHandler.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/repository/AccountRepository.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/repository/AuthSessionRepository.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/repository/PasswordResetChallengeRepository.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/AuthSessionService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/AuthSessionServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/CredentialLoginServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/PasswordRecoveryServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/AccountAccessServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/CreateAdministratorAccountServiceImpl.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/UC07CreateAdministratorAccountTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/UC07AdministratorLoginTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/UC07FirstLoginSecurityTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/T008PasswordRecoveryServiceTest.java`

Các file UC07 có sẵn nhưng chưa được git track vẫn được phân loại là sửa nếu đã tồn tại trước task này.
Git diff --stat chỉ thể hiện file tracked; dùng git status --short -uall để thấy cả file mới.

## Request flow

HTTP Request → Security (JWT, trạng thái/session/first-login, OWNER, CSRF với POST)
→ Controller → Validation (UUID/enum/int binding, page/size service; UC08 không nhận body/role)
→ Service → Business Rules (OWNER, target ADMIN; không đổi role/delete)
→ Repository → Database (UC08 transaction/row lock/hash/status/revoke; UC09 scoped query)
→ Mapper/Response (safe DTO; raw password chỉ trong response của lần issue mới).

Database không lưu raw password. Access sau issue mới vẫn bị backend first-login guard chặn đến khi đổi password.

## Resume verification — 2026-10-23

### Shadow Plan / Action Record của phiên Codex mới

- Authorization/checkpoint: yêu cầu trực tiếp mới nhất của người dùng cho phép kiểm tra local changes,
  tiếp tục UC08 rồi UC09, tự review/fix lỗi kỹ thuật và chạy regression; không commit/push/deploy.
- Branch quan sát được: `feat/long/appointment-booking`.
- Đã đọc root/backend AGENTS.md, CLAUDE.md, CONSTITUTION.md, architecture profile,
  EditorConfig, diff tracked và các file UC07/UC08/UC09 untracked liên quan.
- Trạng thái đầu phiên thực tế đã có implementation/test/migration/tài liệu của cả UC07, UC08 và UC09.
  Danh sách file ở các mục trên mô tả implementation từ phiên trước, không phải file vừa tạo trong phiên resume.
- File boundary phiên resume: chỉ bổ sung evidence vào tài liệu này và sinh build/test output dưới `target/`.
  Không sửa source, test, migration, tài liệu UC07 hoặc frontend; không reset/revert/xóa local changes.
- UC07 xác nhận đủ OWNER tạo ADMIN, username unique (service + UNIQUE constraint), initial ACTIVE,
  temporary password qua SecureRandom/PasswordEncoder, chỉ lưu hash, mustChangePassword=true,
  first-login guard và chặn refresh trước khi đổi password. Regression: 17 PASS.
- UC08 self-review: OWNER/ADMIN guards, giữ role/account/linked records, dùng chung password issuance,
  revoke session/reset link và guard cho legacy ADMIN. Không phát hiện CRITICAL/HIGH cần sửa thêm.
- UC09 self-review: predicate ADMIN tại repository và service, DTO allowlist, role-scoped detail,
  parameter binding/LIKE escaping, pagination 0-based và size 1..50. Không phát hiện CRITICAL/HIGH cần sửa thêm.
- Source requirement HTML ở `../docs/requirements/UC_description.html` dùng số UC06/UC07/UC08 cho
  create/status/view; prompt mới nhất dùng UC07/UC08/UC09. Phiên này theo tên/phạm vi explicit của prompt,
  không sửa HTML hoặc mở rộng sang Create Walk-in Patient Profile (UC09 trong HTML).

### Exact execution và kết quả mới

Wrapper `.\mvnw.cmd -DskipTests test-compile` lỗi trước Maven:
`Cannot index into a null array` tại logic đọc `.m2` Target của wrapper PowerShell.
Không sửa wrapper. Dùng đúng Maven 3.9.16 đã cache theo distributionUrl và JDK 21 theo pom/profile.
Lượt gọi Maven trực tiếp đầu tiên cũng chưa compile do default repository `C:\.m2\repository`
không truy cập được; lần chạy thành công chỉ định local repository hiện có bên dưới.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
$resumeMaven = 'C:/Users/ad/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0/bin/mvn.cmd'
& $resumeMaven '-Dmaven.repo.local=C:/Users/ad/.m2/repository' -DskipTests test-compile
& $resumeMaven '-Dmaven.repo.local=C:/Users/ad/.m2/repository' '-Dtest=UC07CreateAdministratorAccountTest,UC07AdministratorAccountControllerTest,UC07AdministratorLoginTest,UC07FirstLoginSecurityTest,UC08ManageAdministratorAccountTest,UC08AdministratorAccountControllerTest,UC08StaffAuthenticationTest,PatientRegistrationT004Test,T005ContractTest,T005CredentialLoginServiceTest,T006SecurityContractTest,T006HttpSecurityEvidenceTest,T006A12EvidenceTest,T007GoogleAuthenticationServiceTest,T008PasswordRecoveryServiceTest,T009SmtpEmailDeliveryTest,T010PasswordRecoveryControllerTest,T010CommonSecurityContractTest,SessionCsrfWebMvcTest' test
& $resumeMaven '-Dmaven.repo.local=C:/Users/ad/.m2/repository' '-Dtest=UC07CreateAdministratorAccountTest,UC07AdministratorAccountControllerTest,UC07AdministratorLoginTest,UC07FirstLoginSecurityTest,UC08ManageAdministratorAccountTest,UC08AdministratorAccountControllerTest,UC08StaffAuthenticationTest,UC09AdministratorAccountQueryTest,UC09AdministratorAccountControllerTest,PatientRegistrationT004Test,T005ContractTest,T005CredentialLoginServiceTest,T006SecurityContractTest,T006HttpSecurityEvidenceTest,T006A12EvidenceTest,T007GoogleAuthenticationServiceTest,T008PasswordRecoveryServiceTest,T009SmtpEmailDeliveryTest,T010PasswordRecoveryControllerTest,T010CommonSecurityContractTest,SessionCsrfWebMvcTest' test
& $resumeMaven '-Dmaven.repo.local=C:/Users/ad/.m2/repository' '-Dtest=UC07AdministratorAccountPostgresIntegrationTest,UC08AdministratorAccountPostgresIntegrationTest,UC09AdministratorAccountPostgresIntegrationTest,T006PostgresIntegrationTest,T008PasswordRecoveryPostgresIntegrationTest,T006A8EvidenceTest' test
git diff --check
```

| Verification | Kết quả mới | Evidence |
| --- | --- | --- |
| Compile/test-compile | PASS | `target/uc-resume-compile.log` |
| UC08 + UC07/auth regression | 79 PASS, 0 failure/error/skipped | `target/uc-resume-uc08-regression.log` |
| UC09 + UC07/UC08/auth regression | 93 PASS, 0 failure/error/skipped | `target/uc-resume-final-regression.log` |
| PostgreSQL integration | ENVIRONMENT FAILURE: 15 tests, 0 assertion failures, 15 context errors | `target/uc-resume-postgres.log` |
| git diff --check | PASS, exit 0 | Chạy lại sau khi bổ sung resume evidence |

PostgreSQL `127.0.0.1:5432` connection refused (SQL state 08001) chặn Flyway/context initialization.
Chưa xác minh V020/V021, JPQL và row locking trên DB thật; không sửa code để xử lý failure môi trường.
Non-DB suite vẫn PASS. Không phát sinh migration mới trong phiên resume.
Sync-back: giữ nguyên implementation và approval của artifact trước; bổ sung evidence mới tại đây.
Residual blocker: PostgreSQL runtime verification. NEEDS_CLARIFICATION nghiệp vụ: không có.
Human Final Review vẫn PENDING HUMAN REVIEW; self-review kỹ thuật không thay Human approval.

## PostgreSQL DB integration verification — 2026-10-23

### Shadow Plan / Action Record

- Authorization/checkpoint: người dùng yêu cầu kiểm tra môi trường, Flyway, UC07–UC09, concurrency,
  regression và cập nhật mô tả constraint UC07; không commit/push/deploy/reset/xóa database.
- Plan: xác định datasource/startup có sẵn → chạy compile/regression → thử Spring PostgreSQL contexts
  để Flyway chạy → phân loại failure → ghi thiếu coverage và sửa tài liệu constraint.
- File boundary: chỉ cập nhật hai tài liệu UC07 và UC08/UC09, sinh log dưới `target/`.
  Không sửa source, test, migration, frontend hoặc cấu hình credential; không tạo/xóa database/data.

### Môi trường quan sát được

| Thuộc tính | Evidence |
| --- | --- |
| Test profile | `test`, qua `@LocalPostgresTest` |
| Host / port | `127.0.0.1:5432` |
| Database | `otcms_test_db` |
| Username | `postgres`, từ `src/test/resources/application-test.yml` |
| Password source | Giá trị test literal trong application-test.yml; không in/log/copy giá trị |
| Test datasource guard | Chỉ chấp nhận loopback + otcms_test_db; reject supabase profile |
| Flyway / JPA | enabled=true, baseline-on-migrate=false, ddl-auto=validate |
| Testcontainers | Không có dependency hoặc test binding đang dùng |
| Compose | `../compose.yaml` chỉ có orthanc/ohif/imaging-gateway, không có PostgreSQL |
| TCP probe | 127.0.0.1:5432 NOT AVAILABLE |
| Windows local PostgreSQL | Không tìm thấy service qua Get-Service/Get-CimInstance; không thấy installation tại Program Files/PostgreSQL |
| Docker | Có CLI, nhưng daemon pipe dockerDesktopLinuxEngine không tồn tại, kể cả kiểm tra ngoài sandbox |

`docs/local-testing.md` yêu cầu local PostgreSQL service và database riêng. Helper
`scripts/test-local.ps1` được tài liệu nhắc đến không tồn tại trong checkout; cũng không có Compose
PostgreSQL/start command tương ứng. Không tự tạo cấu hình hoặc đoán credential để chạy container mới.
Các biến OTCMS_TEST_DB_USERNAME/OTCMS_TEST_DB_PASSWORD chưa có trong process; cấu hình test hiện tại
không tham chiếu chúng, trái với hướng dẫn trong local-testing.md. Không đọc `.env` hoặc secret files.
Thiếu môi trường PostgreSQL test đang chạy và cách provision/start được repository cấu hình;
không thể xác nhận database/account/password thực tế có tồn tại khi server chưa reachable.

### Verification mới

Dùng cùng Maven 3.9.16 cache/JDK 21/local repository và exact test selections đã ghi ở phần
Resume verification phía trên; không sửa wrapper hoặc dependency.
Compile: `-DskipTests test-compile`; non-DB: toàn bộ 21 test classes trong final regression selection;
DB: UC07AdministratorAccountPostgresIntegrationTest, UC08AdministratorAccountPostgresIntegrationTest,
UC09AdministratorAccountPostgresIntegrationTest, T006PostgresIntegrationTest,
T008PasswordRecoveryPostgresIntegrationTest, T006A8EvidenceTest.

| Verification | Result | Evidence |
| --- | --- | --- |
| Compile / test-compile | PASS | `target/uc-db-verification-compile.log` |
| UC07 non-DB | 17 PASS | `target/uc-db-verification-regression.log` |
| UC08 non-DB | 17 PASS | Cùng log regression |
| UC09 non-DB | 14 PASS | Cùng log regression |
| Auth/security non-DB | 45 PASS | Cùng log regression |
| Tổng non-DB | 93 PASS, 0 failure/error/skipped | Cùng log regression |
| PostgreSQL integration | ENVIRONMENT FAILURE: 15 tests, 0 assertion failures, 15 context errors | `target/uc-db-verification-postgres.log` |
| git diff --check | PASS, exit 0 | Chạy sau khi cập nhật tài liệu |

Root cause: PostgreSQL 127.0.0.1:5432 connection refused. Flyway initializer không lấy được connection.
Chưa kiểm tra được schema history, V019 → V020 → V021, checksum/validation, dữ liệu cũ hoặc assertion
UC07/UC08/UC09 trên DB thật. Không báo migration/query/locking PASS dựa trên unit tests.
Phân loại: ENVIRONMENT FAILURE; chưa xác nhận APPLICATION DEFECT hoặc TEST DEFECT mới.

### Concurrency và findings còn lại

- MEDIUM: không có concurrent UC07–UC09 test coverage hiện tại. Strategy đã có transaction,
  PESSIMISTIC_WRITE account row lock và advisory locks; không thêm/sửa locking.
  Đề xuất test bằng các transaction/connection độc lập cho duplicate username; login/refresh đồng thời
  reissue/deactivate; hai reissue hoặc activate/deactivate cùng target. Kiểm tra kết quả commit,
  session revoke và password cuối cùng, dùng synchronization barrier thay vì timing assumption.
- MEDIUM: test migration legacy hiện tại insert record sau khi migrate. Cần test chuẩn bị dữ liệu
  Patient/staff/OWNER/ADMIN tại V019, chạy V020/V021 rồi đối chiếu ID, role, status, hash,
  linked records và must_change_password=false; không reset database dùng chung.
- LOW: local-testing.md tham chiếu helper không tồn tại và env binding không khớp application-test.yml.
  Ghi finding; không mở rộng task sang sửa hạ tầng hoặc credential configuration.
- LOW tài liệu constraint UC07 đã xử lý: mô tả V020 ban đầu và việc V021 bỏ CHECK,
  giữ UNIQUE/nonblank/email constraints/default FALSE; session guard áp dụng mọi ADMIN.

Sync-back: cập nhật evidence và tài liệu, không thay requirement hoặc Human approval.
Residual blocker: PostgreSQL runtime verification và các coverage gap trên.
VERDICT: NOT READY FOR COMMIT. Human Final Review vẫn PENDING HUMAN REVIEW.

## PostgreSQL Docker verification — kết quả mới nhất 2026-10-23

Phần này thay thế verdict ENVIRONMENT FAILURE/NOT READY ở các lượt trước bằng evidence mới;
không sửa hoặc suy Human approval.

### Authorization, Shadow Plan và Action Record

- Checkpoint: người dùng xác nhận Docker Desktop đã chạy, cho phép tạo container test riêng
  `otcms-postgres-test`, dùng đúng test credential và bổ sung legacy/concurrency tests nếu không đổi rule.
- Plan: Docker info → đọc test binding → tạo/health-check container riêng → Flyway + UC DB suites →
  migration/concurrency evidence → sửa technical test defects nếu có → regression tổng hợp → diff check/docs.
- File boundary: thêm AdministratorAccountDatabaseVerificationTest; sửa UC08AdministratorAccountPostgresIntegrationTest,
  T006A8EvidenceTest và docs/local-testing.md, docs/uc07-administrator-account.md, tài liệu này.
- Không sửa production Java, migration, frontend, credential configuration hoặc requirement.
  Không reset/revert, commit, push, deploy, xóa database/volume hoặc dữ liệu cũ.

### Container và Flyway

- `docker info`: Docker Engine 29.6.1, Linux. Không có PostgreSQL container cũ; container MySQL khác giữ nguyên.
- Test config chọn `127.0.0.1:5432/otcms_test_db`, user `postgres`; password được đọc từ
  application-test.yml trong process, truyền qua Docker env và xóa khỏi process sau khi tạo, không in/log.
- application-test.yml không pin server version. Dùng PostgreSQL major 18 theo evidence repository;
  runtime xác nhận PostgreSQL 18.6 (Debian 18.6-1.pgdg13+2).
- Image đã kiểm chứng: `postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6`.
- Container `otcms-postgres-test`, health=healthy, bind chỉ `127.0.0.1:5432`, volume riêng
  `otcms-postgres-test-data:/var/lib/postgresql`. Giữ container/volume để chạy lại.
- Reproducible setup command đã ghi tại docs/local-testing.md; không dùng Supabase/production.
- Spring context đã apply 23 migrations vào public schema, đến V021; JPA validate thành công.
- Đọc trực tiếp public.flyway_schema_history:

| installed_rank | version | checksum | success |
| ---: | --- | ---: | --- |
| 21 | 019 | -1306517819 | true |
| 22 | 020 | 1595489762 | true |
| 23 | 021 | -1130579379 | true |

Không có failed migration; Flyway validation không có checksum conflict. Constraints thực tế giữ
uk_accounts_username, ck_accounts_username_not_blank, ck_accounts_email_required;
ck_accounts_issued_admin_username không còn sau V021.

### Tests và technical fixes

AdministratorAccountDatabaseVerificationTest bổ sung 4 real-DB cases:

1. Flyway migrate schema riêng tới V019, tạo accounts cho mọi AccountType và auth_session FK links,
   snapshot dữ liệu cũ, chạy V020/V021 rồi validate. So sánh tất cả field cũ và session records;
   kiểm tra username NULL/default flag FALSE và history theo thứ tự; legacy ADMIN có thể nhận flag TRUE.
2. Hai transaction tạo cùng username đồng thời qua service thật: chỉ một CREATED, một
   USERNAME_ALREADY_EXISTS, đúng một account persist.
3. Hai reissue transaction cùng ADMIN: transaction đầu giữ row lock, quan sát
   pg_blocking_pids của connection thứ hai trước khi thả khóa. Sau commit, chỉ password lần hai hợp lệ,
   password ban đầu/lần đầu không còn hợp lệ; status/role/flag đúng và old sessions revoked.
4. Non-ADMIN target không bị update và không expose detail; DTO list/detail từ real query không chứa
   hash, temporaryPassword, mustChangePassword hoặc giá trị issued password.

Test dùng UUID riêng, giữ schema upgrade riêng để kiểm tra; không clean shared schema/database.
Không thêm hoặc sửa locking production để test pass.

- TEST DEFECT đã sửa: UC08 PostgreSQL session case và test concurrency mới thiếu JWT test binding.
  Thêm DynamicPropertySource với signing key ngẫu nhiên, chỉ trong test memory; không đọc secret production.
- TEST DEFECT đã sửa: T006A8EvidenceTest dùng forwarded header để giả lập client ngoài mạng dù không có
  trusted proxy; fixture mới đặt remote address thực và assert STAFF_NETWORK_ACCESS_DENIED.
  Test CSRF lấy token qua /api/auth/csrf, không trộn SecurityMockMvc csrf repository với cookie flow;
  negative case dùng cookie/header mismatch rõ ràng. Không nới policy production.
- APPLICATION DEFECT mới: không phát hiện qua các tests đã chạy.
- Hai MEDIUM gaps trước (upgrade/concurrency evidence) đã có coverage và PASS.
  LOW local-testing helper/env binding drift đã sửa bằng hướng dẫn Docker thực tế và nguồn credential đúng.

### Exact verification và kết quả cuối

JDK 21/Maven 3.9.16 cache như các lệnh phía trên. Targeted DB suite sau sửa fixture:
10 tests PASS tại `target/uc-postgres-running-verification-fixed.log`.
Auth fixture verification: 4 PASS tại `target/uc-postgres-security-fixture-fixed.log`.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
$verifiedMaven = 'C:/Users/ad/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0/bin/mvn.cmd'
& $verifiedMaven '-Dmaven.repo.local=C:/Users/ad/.m2/repository' '-Dtest=UC07CreateAdministratorAccountTest,UC07AdministratorAccountControllerTest,UC07AdministratorLoginTest,UC07FirstLoginSecurityTest,UC08ManageAdministratorAccountTest,UC08AdministratorAccountControllerTest,UC08StaffAuthenticationTest,UC09AdministratorAccountQueryTest,UC09AdministratorAccountControllerTest,PatientRegistrationT004Test,T005ContractTest,T005CredentialLoginServiceTest,T006SecurityContractTest,T006HttpSecurityEvidenceTest,T006A12EvidenceTest,T007GoogleAuthenticationServiceTest,T008PasswordRecoveryServiceTest,T009SmtpEmailDeliveryTest,T010PasswordRecoveryControllerTest,T010CommonSecurityContractTest,SessionCsrfWebMvcTest,UC07AdministratorAccountPostgresIntegrationTest,UC08AdministratorAccountPostgresIntegrationTest,UC09AdministratorAccountPostgresIntegrationTest,AdministratorAccountDatabaseVerificationTest,T006PostgresIntegrationTest,T008PasswordRecoveryPostgresIntegrationTest,T006A8EvidenceTest' test
git diff --check
```

| Final selection | Tests | Result |
| --- | ---: | --- |
| UC07 non-DB / DB | 17 + 2 | PASS |
| UC08 non-DB / DB | 17 + 2 | PASS |
| UC09 non-DB / DB | 14 + 2 | PASS |
| Upgrade/concurrency/real DTO bổ sung | 4 | PASS |
| Auth/security non-DB / DB-dependent regression | 45 + 9 | PASS |
| Tổng | 112 | 0 failure, 0 error, 0 skipped |

Final log: `target/uc-postgres-final-regression-fixed.log`. Compile/testCompile trong final command PASS.
`git diff --check`: PASS sau cập nhật test và tài liệu.
Sync-back: ghi evidence mới, giữ production implementation và migrations; không thay Human review decision.
Residual blocker kỹ thuật trong scope UC07–UC09: không còn finding đã xác nhận.
VERDICT kỹ thuật: READY FOR COMMIT. Không thực hiện commit/push; Human Final Review vẫn do Human quyết định.

## Minimal refactor — 2026-10-23

### Shadow Plan / Action Record

- Checkpoint/authorization: yêu cầu mới nhất cho phép minimal refactor về
  OwnerAdministratorController → OwnerAdministratorService → AccountRepository → AccountJpaEntity/DTO,
  giữ nguyên endpoint, behavior, schema, security; không frontend/commit/push.
- Plan: đọc references → chuyển ba controller thành một → chuyển ba service pairs thành một
  concrete service → inline constant policy/authorization helper → cập nhật wiring test → clean compile
  → full UC/auth/DB regression → docs/diff review.
- Approved file boundary: hai OwnerAdministrator classes mới; bỏ 11 class cũ được gộp/thay thế;
  cập nhật wiring trong 10 test files UC07–UC09/DatabaseVerification và hai tài liệu UC07, UC08/UC09.
- Không sửa business logic ở authentication/session/password helpers, repository, DTO, mapper,
  entity, SecurityConfig hoặc migrations trong đợt refactor này.
- Giữ AdministratorPasswordIssuer và TemporaryPasswordGenerator: secure issuance dùng cho cả create/reissue;
  giữ mapper để allowlist dữ liệu trả ra. StaffAuthenticationService không bị gộp vì transaction bao trùm
  credential verification và session creation là security boundary đã được kiểm tra.
- requireOwner/requireAdministrator chuyển từ AdministratorAccountAuthorization vào private methods
  của OwnerAdministratorService. Initial ACTIVE gán trực tiếp trong create thay policy một hằng số.
- Giữ @Transactional cho bốn thao tác ghi; @Transactional(readOnly=true) cho list/detail.
  Advisory lock username, target row lock, hash/flag, reset supersession và revoke giữ nguyên thứ tự.

### Test adaptation và verification

- Các test vẫn theo tên UC để truy vết requirement; đổi imports/constructor/mock/autowire sang service mới.
  Không bỏ test case hoặc assertion về role/hash/flag/session/data exposure/locking.
- Hai MVC assertions từng phụ thuộc controller slice được điều chỉnh theo routing đầy đủ đã tồn tại:
  UC07 GET list trả 200 và không có issued password; UC08 DELETE detail trả 405 vì đã có GET mapping.
  Đây không phải thay đổi URL hay behavior của application khi chạy đủ ba controller cũ.
- Compile exact command, với JDK 21/Maven 3.9.16/local repository như trên:
  `-DskipTests clean test-compile` → PASS. Windows không cho clean log đang mở trong target ở lượt đầu;
  chạy lại với log ngoài target rồi copy vào sau compile → PASS, không có code compilation failure.
  Maven clean cũng dọn build/log artifacts cũ dưới target; source và database không bị reset.
- Regression exact command dùng cùng 28 test classes trong PostgreSQL Docker verification phía trên,
  sau refactor: 112 tests PASS, 0 failures, 0 errors, 0 skipped.
- Evidence hiện tại: `target/owner-administrator-refactor-compile.log`,
  `target/owner-administrator-refactor-regression.log`. Logs dưới target của các lượt trước là artifacts
  lịch sử có thể đã được Maven clean dọn; kết quả lịch sử vẫn được ghi trong tài liệu này.
- Docker test container hiện có vẫn running/healthy; suite migration upgrade/concurrency tiếp tục PASS.
- `git diff --check`: PASS sau code/test và tài liệu; không còn references đến class cũ trong Java source.
- Sync-back: mục Cấu trúc hiện tại ở đầu tài liệu và phần initial status UC07 đã cập nhật theo refactor;
  các class/file inventories lịch sử giữ để truy vết. Không thay convention cho feature khác hoặc Human approval.
- Residual blocker trong phạm vi refactor: không có finding kỹ thuật đã xác nhận.
- VERDICT: SIMPLE ENOUGH FOR DEFENSE. Human Final Review không được agent tự approve.

