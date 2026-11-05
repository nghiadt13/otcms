# KAN-27 — Quản lý tài khoản nhân viên theo R3

KAN-27 PostgreSQL integration đã PASS 20/20, migration PASS 2/2; combined selector 22 tests, 0 failures, 0 errors, BUILD SUCCESS. Full-suite evidence mới nhất sau khi thêm hai controller regression tests là 807 tests, 7 failures, 1 error, 0 skipped; không gọi full build green. Clean verify chưa được rerun thành công sau khi môi trường DB được sửa.

## Nguồn yêu cầu và quyết định Human

Human đã xác nhận trong yêu cầu CONTINUE KAN-27 ngày 2026-11-02:

1. KAN-27 ticket do Human cung cấp là nguồn ưu tiên.
2. Current Report 3 / R3 SRS UC07, UC08, UC09 và BR/MSG tương ứng là nguồn nghiệp vụ.
3. Implementation conventions chỉ áp dụng khi không trái R3.

`../docs/srs4-uc07-09-extract.md` trích UC07–UC09 từ Report3. Appendix cũ hiện còn MSG-11 theo email/invitation, MSG-13 theo email và BR-08 cấm trả readable password; những nội dung mâu thuẫn contract này được Human xác định là STALE cho KAN-27. Không sửa appendix, không dùng FEAT-002 D03, không dùng KAN-26 làm dependency kỹ thuật.

Shared fixes trong `AccountAccessServiceImpl` và `CredentialLoginServiceImpl.loginStaff()` đã được NghiaDT CONFIRM theo yêu cầu Human. Không đổi Administrator login hoặc Owner business contract.

## Branch / base và migration allocation

- Branch: `feat/hung/KAN-27-staff-accounts`.
- Original HEAD: `db64ca8beb93807b7f2ed6e953cd2c4645d99cdc`.
- `origin/product` sau fetch và recheck: cùng SHA; ahead/behind `0/0`.
- Pre-flight đầu: working tree clean. Recheck trước code: chỉ có evidence document này untracked; không tracked/staged changes.
- Exact checks: `git status`, `git branch --show-current`, `git fetch origin`, `git log -1 --oneline HEAD`, `git log -1 --oneline origin/product`, `git rev-list --left-right --count HEAD...origin/product`.
- `git ls-tree -r HEAD -- src/main/resources/db/migration | Select-String "V049|V050|V051|V052"`: không có collision. Highest base migration V072.
- Chỉ thêm `V050__staff_account_management_r3.sql`; không sửa migration lịch sử, không sử dụng V049/V051/V052.

## Kiến trúc và file boundary

- `StaffAccountAdminController → StaffAccountAdminService → StaffAccountAdminServiceImpl → Auth repositories/entities`.
- Refactor nhỏ service concrete cũ thành interface và implementation; không refactor service khác.
- Auth chỉ gọi public `StaffProfileAccountQueryService`; không import Staff repository/entity.
- Staff service trả map accountId → `{id, displayName}` bằng một batch projection query cho mỗi page; không load photo/contact/clinical data, không N+1.
- Mọi mutation và audit nằm cùng service transaction; read operations read-only.
- Không sửa frontend, SecurityConfig, dependencies, Owner service hoặc các feature ngoài scope.

## API contracts

Prefix: `/api/v1/admin/staff-accounts`. Cả sáu operations yêu cầu `ROLE_ADMIN`; service kiểm tra principal Admin và persisted account thực sự là ADMIN ACTIVE. Owner, principal giả, actor thiếu/không tồn tại/bị vô hiệu hóa bị từ chối.

| Method / suffix | Input / kết quả |
| --- | --- |
| POST / | `{role, username, initialPassword}` → 201 `{accountId, username, role, status, mustChangePassword}` |
| GET / | `q`, `role`, `status`, `page=0`, `size=20` → `{items, page, size, totalItems, totalPages}` |
| GET /{id} | Item detail trong scope Staff |
| POST /{id}/activation | DISABLED → ACTIVE; trả item |
| POST /{id}/deactivation | ACTIVE → DISABLED; trả item |
| POST /{id}/password-reissues | 200 `{accountId, temporaryPassword, mustChangePassword:true}` |

Mọi Staff response dùng `Cache-Control: no-store`. OpenAPI operations/statuses được khai báo theo SpringDoc annotations hiện có, không thêm library.

Item list/detail: `{accountId, username, role, status, mustChangePassword, createdAt, staffProfile}`; `staffProfile` là `{id, displayName}` hoặc null. Legacy email staff có thể có username null. Không expose email, password/hash, session, failed count, lock fields, workContact, photo hoặc clinical profile fields.

### Role mapping

| External API | Internal AccountType |
| --- | --- |
| RECEPTIONIST | RECEPTIONIST |
| ORTHOPEDIC_DOCTOR | DOCTOR |
| IMAGING_TECHNICIAN | IMAGING_TECHNICIAN |
| LAB_TECHNICIAN | LAB_TECHNICIAN |

Không nhận/expose DOCTOR trong Staff management HTTP contract. Reject PATIENT/ADMIN/OWNER/custom role. Detail/mutations ẩn cả category và existence của target ngoài scope bằng cùng 404 ACCESS_DENIED.

### Username và password

- Username required/nonblank, tối đa 64 theo existing `AdministratorUsernamePolicy`; reject ISO controls và invalid surrogate. Không trim/lowercase/normalize; giữ exact case-sensitive uniqueness. Cho phép `@`.
- CREATE dùng password Admin nhập; reuse KAN-25 `PasswordPolicy`: 8–64 Unicode code points, tối đa 72 UTF-8 bytes, có Unicode letter và digit. Không trim/normalize.
- PasswordEncoder chỉ lưu hash; email/name/displayName null; ACTIVE, mustChangePassword true. Không tạo StaffProfile, không gọi password issuer để generate CREATE credential, không echo initialPassword.
- Coordination namespace `administrator-username:<exact username>` dùng chung Owner create; check tồn tại sau lock, unique constraint DB làm bảo vệ cuối. Chỉ `uk_accounts_username` chuyển thành USERNAME_TAKEN.

### Search / status

- q tối đa 100 ký tự, trim để search literal substring trên username; escape `!`, `%`, `_`; không đổi exact-case username semantics. Search hiện case-sensitive.
- page >= 0, size 1..100; sort createdAt DESC, id DESC; empty results 200.
- External ACTIVE tương ứng DB ACTIVE; mọi DB status khác hiển thị INACTIVE. Mutation dùng trạng thái DB thực.
- Activation chỉ DISABLED → ACTIVE, xóa failedCredentialCount/lockedUntil; TEMPORARILY_LOCKED không thể admin-activate.
- Deactivation chỉ ACTIVE → DISABLED và revokeAll sessions. Không xóa account/profile/history hoặc đổi role.

## Login, first login và JWT invalidation

- `loginStaff()` thực hiện một authenticate attempt: khóa/rate-limit IP, exact username lookup trước; nếu miss và identifier có @ thì fallback legacy normalized email. Không fallback khi username tồn tại nhưng password sai; không double audit/counter.
- Giữ Administrator login nguyên trạng. Existing StaffAuthenticationService transaction giữ account row lock từ password verification tới session persistence.
- Full account/session eligibility áp dụng cho ADMIN + bốn Clinic Staff types: account ACTIVE; session đúng account, chưa revoked, chưa expired. PATIENT/OWNER không được mở rộng trong shared fix.
- Eligibility được kiểm tra trước forced-change flag; invalid session → existing JWT filter trả 401 AUTHENTICATION_FAILED. Reactivation không revive revoked JWT/session.
- Existing first-login flow: login username/initialPassword → authenticated session có mustChangePassword → business routes 403 PASSWORD_CHANGE_REQUIRED → password-change route được phép → valid change xóa flag và revoke old sessions → credential cũ thất bại, credential mới login được.

## Password reissue

Khóa target account trước; chỉ bốn Staff roles. Reuse AdministratorPasswordIssuer/TemporaryPasswordGenerator, PasswordResetChallengeRepository và AuthSessionService: issue/hash mới → mustChangePassword → timestamp → supersede reset challenge → revokeAll → REISSUE_PASSWORD audit → commit.

Không tự activate/unlock, không xóa failed count/lockedUntil. Response trả plaintext mới một lần, no-store; không persist/log/audit plaintext. DTO request/reissue redacts toString. Reissue lại tạo credential mới, không có endpoint đọc lại plaintext đã issue.

## V050 và account_management_audits

Constraint mới:

```sql
CHECK (normalized_email IS NOT NULL
       OR (account_type <> 'PATIENT' AND username IS NOT NULL))
```

Patient vẫn cần email. Bốn Clinic Staff username-only và ADMIN username-only hợp lệ; legacy email rows không bị ép có username. Giữ uk_accounts_username.

Audit columns duy nhất: id UUID PK; account_id và actor_account_id FK accounts ON DELETE RESTRICT; action VARCHAR(24) CHECK CREATE/ACTIVATE/DEACTIVATE/REISSUE_PASSWORD; occurred_at TIMESTAMPTZ; correlation_id VARCHAR(64). Tất cả required. Index theo account/actor + occurred_at DESC + id DESC.

Audit Staff: CREATE, ACTIVATE, DEACTIVATE, REISSUE_PASSWORD. Audit Patient: ACTIVATE, DEACTIVATE. Không thêm Owner audit. Không chứa email/password/hash/token.

Controller lấy `CorrelationIdFilter.current(request)` rồi truyền String vào service; không truyền HttpServletRequest vào service, không tạo UUID thay correlation của management request. Existing filter chỉ chấp nhận header UUID hợp lệ; nếu header không hợp lệ/thiếu, filter tạo correlation theo policy hiện có. Test dùng valid UUID và so exact request header với audit/error.

Audit cùng transaction với mutation; validation/denied/duplicate/no-op không audit success. Audit failure rollback mutation; transaction rollback không để lại account/audit.

## Patient integration

Giữ `/api/v1/admin/accounts` list/detail/search/DTO/status behavior. Chỉ thêm correlation argument từ controller và audit save trong activate/deactivate transaction. Xóa obsolete Staff exception mapping khỏi Patient handler; Patient mapping còn nguyên.

## Error contract

Shared ErrorResponse giữ nguyên envelope `error: {code, message, timestamp, correlationId, ...}`; không có messageCode.

| Tình huống | HTTP | Code | Message semantics |
| --- | --- | --- | --- |
| Create invalid | 400 | VALIDATION_FAILED | MSG-12 |
| Search/filter/page invalid | 400 | VALIDATION_FAILED | MSG-54 |
| Username duplicate | 409 | USERNAME_TAKEN | MSG-13 theo username R3 |
| Actor denied | 403 | ACCESS_DENIED | MSG-14 |
| Target missing/outside scope | 404 | ACCESS_DENIED | MSG-14 |
| Invalid/no-op transition | 409 | ACCOUNT_STATUS_UNCHANGED | MSG-42 |
| Explicit operation/persistence failure | 500 | ACCOUNT_OPERATION_FAILED | MSG-16 |

Scoped Staff advice giữ các mappings cụ thể cho business/validation/access-denied/persistence exceptions, rồi fallback RuntimeException còn lại thành HTTP 500 ACCOUNT_OPERATION_FAILED với MSG-16 an toàn, current request correlation và no-store. Không attach exception text, SQL, hash hoặc stack trace vào response. Transport MSG-54 chỉ áp dụng GET /api/v1/admin/staff-accounts; malformed UUID ở GET detail dùng MSG-12/VALIDATION_FAILED, HTTP 400.

## Test source và concurrency

- `Kan27StaffAccountServiceTest`: bốn roles, forbidden roles, actual Admin check, username controls/surrogate/max/case/@, Unicode/password byte/codepoint boundaries, untrimmed password, duplicate exact constraint, list filters/sort/profile batch, target hiding, transitions/no-op/temporary lock, reissue state preservation, audit evidence.
- `Kan27StaffAccountControllerTest`: request/response/validation/error envelope, no-store, sensitive-field exclusion, exact request correlation cho Staff và Patient.
- `Kan27StaffLoginAndAccessTest`: exact @ username precedence, legacy email fallback, one attempt, all five full-eligibility roles, forced-change precedence, revoked JWT/reactivation behavior, không broaden PATIENT/OWNER.
- `Kan27PatientManagementAuditTest`: hai successful actions và no success audit khi no-op/denied/failure.
- `Kan27StaffProfileAccountQueryTest`: một batch projection, skip empty query, public summary chỉ id/displayName.
- `Kan27StaffAccountPostgresIntegrationTest`: full context/security/HTTP/real DB, first-login/change flow, create persistence/no profile, directory/profile/paging/literal LIKE, scope/status/JWT, reissue/reset, Patient audit, rollback. MockMvc credential request printing bị tắt.
- `Kan27StaffAccountMigrationPostgresTest`: isolated fresh schema và out-of-order V050 sau V072, không phụ thuộc V049/V051/V052; legacy rows, email invariant, case uniqueness, audit CHECK/FK/index/columns.

Ba concurrency cases dùng real independent worker threads/HTTP requests, start latch và bounded future timeout, không mock persistence và không dùng sequential test thay concurrency. Mỗi case lặp ba lần:

1. Duplicate CREATE: expect exactly one 201, one 409 USERNAME_TAKEN, one account, one CREATE audit.
2. Login vs reissue: sau cả hai commit, không có live old-credential session/token; login lại bằng credential cũ thất bại.
3. Login vs deactivate: sau commit account DISABLED; không live session/token; login lại thất bại.

Runtime evidence hiện có: Kan27StaffAccountPostgresIntegrationTest PASS 20/20 và Kan27StaffAccountMigrationPostgresTest PASS 2/2. Bao gồm cả ba concurrency groups lặp ba lần, first-login HTTP flow, username @/legacy email, JWT lifecycle, management audit và transaction rollback. Không thay đổi V050 hoặc business rules trong lượt review fixes.

## Current verification evidence — review fixes 2026-11-03

Human cung cấp kết quả sau khi DB environment đã được sửa. Trước khi sửa handler/tests trong lượt review này, Agent kiểm tra các Surefire XML hiện có và xác nhận KAN-27 integration 20/20, migration 2/2 và full-report total 805 tests / 7 failures / 1 error. Combined-selector BUILD SUCCESS là evidence Human cung cấp; class-level XML hiện có khớp 22/0/0. Không phải lệnh PostgreSQL/full suite mới được chạy trong lượt review fixes.

| Verification | Tests | Failures | Errors | Result / thời điểm |
| --- | ---: | ---: | ---: | --- |
| KAN-27 PostgreSQL integration | 20 | 0 | 0 | PASS, sau DB recovery, trước review fixes |
| KAN-27 migration | 2 | 0 | 0 | PASS, sau DB recovery, trước review fixes |
| Combined KAN-27 PostgreSQL selector | 22 | 0 | 0 | BUILD SUCCESS, sau DB recovery, trước review fixes |
| Full `.\mvnw.cmd test` | 805 | 7 | 1 | BUILD FAILURE; latest full-run evidence trước review fixes |
| Full suite mới nhất sau khi thêm hai controller regression tests | 807 | 7 | 1 | Evidence Human cung cấp; 0 skipped; không gọi full build green |
| Directly affected unit/controller tests | 64 | 0 | 0 | BUILD SUCCESS, JDK 21, 2026-11-03T02:49:37+07:00 |
| `.\mvnw.cmd clean verify` sau DB recovery | — | — | — | Chưa được rerun thành công; không claim PASS |

PostgreSQL selector đã PASS theo evidence hiện có:

```powershell
.\mvnw.cmd '-Dtest=Kan27StaffAccountPostgresIntegrationTest,Kan27StaffAccountMigrationPostgresTest' test
```

Directly affected selector được chạy trong lượt này trên JDK 21:

```powershell
.\mvnw.cmd '-Dtest=Kan27StaffAccountControllerTest,Kan27StaffAccountServiceTest,Kan27PatientManagementAuditTest' test
```

Không rerun full suite hoặc clean verify trong scope review fixes này. Vì full suite còn failures/error và clean verify chưa có successful rerun sau DB recovery, không đánh dấu full build green hoặc tự approve final review.

### Current full-suite failures/error

Full-suite evidence hiện tại được Human cung cấp là 807 tests / 7 failures / 1 error / 0 skipped, sau khi thêm hai controller regression tests. Các entry dưới đây được ghi nhận từ full run lịch sử 805 tests trước review fixes và khớp XML đã kiểm tra ở lượt đó. Chưa có untouched-base reproduction trong evidence này; không tự phân loại chúng là pre-existing base failures hoặc KAN-27 regressions, và không sửa unrelated failures.

| Result | Exact suite / method |
| --- | --- |
| FAIL | `appointment.AppointmentArrivalT053PostgresIntegrationTest#timelyArrivalClaimCommittedBeforeNoShowBlocksNoShowAndLateClaimCannotReopenNoShow` |
| FAIL | `appointment.AppointmentArrivalT053PostgresIntegrationTest#timelyQrArrivalHoldingSharedGuardSerializesAheadOfNoShowAfterGrace` |
| ERROR | `auth.AdministratorAccountDatabaseVerificationTest#upgradeFromV019PreservesExistingAccountsAndSessionLinks` |
| FAIL | `auth.T005RealLoginIntegrationTest#wrongPasswordsPersistLockoutAndSuccessResetsCounter` |
| FAIL | `auth.T008PasswordRecoveryPostgresIntegrationTest#changingPasswordInvalidatesPreviouslyIssuedResetLink` |
| FAIL | `persistence.T032AdminResourceMutationPostgresIntegrationTest#mutationWaitsForGuardThenRechecksNewBookingAndRejectsWithoutWrites` |
| FAIL | `persistence.T032AdminResourceMutationPostgresIntegrationTest#adminMutationsReturnImpactConflictsAndWriteOnlySuccessfulOwnerAudits` |
| FAIL | `persistence.T032AdminResourceMutationPostgresIntegrationTest#checkedInAppointmentAlsoBlocksTimeOffCreation` |

Package prefix cho bảng trên: `fu.fa26.sep490.g58.otcms_be`.

### Historical baseline notes

- Pre-implementation `.\mvnw.cmd test` trên JDK 21.0.9: 712 tests / 0 failures / 255 errors / 0 skipped, BUILD FAILURE, 2026-11-02T21:19:52+07:00. Thiếu OTCMS_TEST_DB_PASSWORD, PostgreSQL connection refused và cascading context errors; không phải accepted application-code baseline. Exact historical inventory được giữ cuối tài liệu.
- Các lượt trước DB recovery: full test từng có 800/0/276 rồi 806/0/277; clean verify ngày 2026-11-02T22:20:55+07:00 có 806/0/277, dừng ở test phase vì environment/authentication failures. Những số này chỉ là lịch sử, không phải current PostgreSQL result và không thay thế full run 805/7/1 sau recovery.
- Không coi 255/276/277 infrastructure errors là historical product failures được chấp nhận. Môi trường hiện đã đủ để có KAN-27 PostgreSQL PASS 22/22 theo evidence mới; các cảnh báo yêu cầu secret/container và trạng thái chưa xác minh trước đó đã được thay thế.

## Review-fix self-review

| Check | Current evidence |
| --- | --- |
| Specific business/validation/access/persistence mappings | Giữ nguyên; controller regression suite bao phủ existing error contract |
| Unexpected RuntimeException fallback | Scoped Staff advice, 500 ACCOUNT_OPERATION_FAILED, MSG-16, exact correlation, no-store; không expose detail |
| Malformed detail UUID | 400 VALIDATION_FAILED, MSG-12; không dùng search-criteria message |
| GET list search/filter/page validation | MSG-54; existing create/search tests giữ nguyên |
| Supplied CREATE password, no echo, email NULL, no auto-profile | Unit/web và PostgreSQL integration PASS theo evidence trước review fixes; business code không đổi |
| Generated reissue, reset supersession, revoked sessions, preserved status | PostgreSQL integration PASS |
| Username @ / legacy email / first-login forced change | PostgreSQL integration PASS |
| Deactivate invalidates JWT; reactivate does not revive it | PostgreSQL integration PASS |
| Duplicate CREATE / login vs reissue / login vs deactivate races | PostgreSQL integration PASS, mỗi race ba repetitions |
| Staff + Patient management audit / correlation / no-op / rollback | PostgreSQL integration PASS |
| V050 fresh/out-of-order/constraints | Migration PASS 2/2; V050 không sửa trong review fixes |
| Auth/Staff boundary / batch profile query | Không thay đổi |
| Full suite / clean verify | Full 807/7/1, 0 skipped, còn cần triage; clean verify chưa rerun thành công sau DB recovery |
## Manual testing và FE KAN-35 follow-ups

1. Chuẩn bị dedicated loopback otcms_test_db, Docker/service và secret environment theo docs/local-testing.md; không dùng Supabase/app DB. Dùng JDK 21.
2. Chạy focused KAN-27 non-DB, PostgreSQL classes, full Maven test và clean verify (commands exact được ghi trong Action Record).
3. Login Admin thực, lấy CSRF theo flow hiện có, tạo từng role bằng username và Admin-supplied password; kiểm tra response không có password và first-login flag.
4. Login Staff/đổi password; kiểm tra business route trước/sau change, credential cũ thất bại. Thử username có @ và legacy email.
5. List/detail/filter/page; kiểm tra role external, profile summary/null, empty/error/no-store.
6. Deactivate/reactivate và reissue; kiểm tra old JWT/refresh không revive; kiểm tra TEMPORARILY_LOCKED không activate được; linked profile/history còn nguyên.
7. So audit actor/target/action/time/exact correlation với request; duplicate/no-op/failure không ghi success. Không đưa credential/PII vào screenshot/log.

FE KAN-35: thay email/fullName creation bằng role/username/initialPassword; external ORTHOPEDIC_DOCTOR; không tự normalize username; password input theo policy KAN-25; create không render plaintext response; reissue hiển thị một lần rồi xóa state, không cache/log/store; list profile nullable/username legacy null; status projection chỉ để hiển thị; gửi CSRF và valid correlation UUID; phân biệt MSG-12/MSG-54; handle 401 revoked session và forced-change flow. Không sửa frontend trong KAN-27.

## Shadow Plan / Action Record — review fixes

- Human-authorized scope ngày 2026-11-03: chỉ StaffAccountAdminApiExceptionHandler, Kan27StaffAccountControllerTest và tài liệu này. Không sửa V050, business rules, config/dependencies hoặc unrelated files.
- Shadow Plan: thêm fallback/route-specific validation; thêm hai controller regressions và giữ existing cases; chạy directly affected selector; đối chiếu file hashes và review diff.
- Checkpoint: explicit review-fix instruction trong phiên hiện tại; các quyết định R3/shared changes đã được Human xác nhận trước đó, không yêu cầu duyệt lại.
- Execution: direct; không spawn worker. Exact command nằm trong Current verification evidence; JAVA_HOME command-scoped dùng JDK 21.
- Actions: giữ specific exception handlers; thêm scoped RuntimeException handler an toàn; transport MSG-54 chỉ cho GET list; malformed detail UUID dùng MSG-12; ghi evidence PostgreSQL 22/22 và full 805/7/1 trước review fixes.
- Result: directly affected tests PASS 64/64 (controller 14, Staff service 48, Patient audit 2), 0 failures, 0 errors, 0 skipped, BUILD SUCCESS. Full-suite failures/error không được sửa hoặc tự coi là accepted base failures. Clean verify không được rerun trong lượt này.
- Sync-back: chỉ tài liệu KAN-27 này; không sửa stale appendix, Constitution, Architecture Profile, V050 hoặc skill files. Không stage/commit/push/rebase.

## AI Agent Recommendation

Status: PENDING HUMAN REVIEW. Review hai fixes về error boundary/transport message và evidence mới. KAN-27 PostgreSQL đã PASS 22/22; full 807/7/1, 0 skipped, cần triage riêng và clean verify cần successful rerun sau DB recovery trước khi gọi toàn build green.

## Human Final Review

Final code review: PENDING. Agent không ghi APPROVED thay Human. Nguồn R3 và hai shared changes đã được Human quyết định; đây không phải yêu cầu duyệt lại chúng.
## Files changed

Modified (11):

- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/controller/PatientAccountAdminController.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/controller/StaffAccountAdminController.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/exception/PatientAccountAdminExceptionHandler.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/exception/StaffAccountAdminException.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/repository/AccountRepository.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/PatientAccountAdminService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/StaffAccountAdminService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/AccountAccessServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/CredentialLoginServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/PatientAccountAdminServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/staff/repository/StaffProfileRepository.java`

New (16):

- `docs/kan27-staff-accounts-r3.md`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/exception/StaffAccountAdminApiExceptionHandler.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/model/dto/StaffAccountDtos.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/model/entity/AccountManagementAuditJpaEntity.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/repository/AccountManagementAuditRepository.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/StaffAccountAdminServiceImpl.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/staff/service/StaffProfileAccountQueryService.java`
- `src/main/java/fu/fa26/sep490/g58/otcms_be/staff/service/impl/StaffProfileAccountQueryServiceImpl.java`
- `src/main/resources/db/migration/V050__staff_account_management_r3.sql`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/Kan27PatientManagementAuditTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/Kan27StaffAccountControllerTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/Kan27StaffAccountMigrationPostgresTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/Kan27StaffAccountPostgresIntegrationTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/Kan27StaffLoginAndAccessTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/auth/service/impl/Kan27StaffAccountServiceTest.java`
- `src/test/java/fu/fa26/sep490/g58/otcms_be/staff/Kan27StaffProfileAccountQueryTest.java`

## Historical pre-implementation environment-error inventory — 2026-11-02

Inventory lịch sử của lần baseline 712 tests / 0 failures / 255 errors trước implementation, khi test DB/configuration chưa sẵn sàng; không phải kết quả hiện tại hoặc accepted product failures. Các entry giữ nguyên suite/method từ Surefire XML. Entry có method rỗng (`ReviewPostgresIntegrationTest#`) là suite lifecycle error được Surefire ghi với name rỗng, không phải method nghiệp vụ:

- `fu.fa26.sep490.g58.otcms_be.activitylog.ActivityLogPostgresIntegrationTest#creatingRoomProducesVisibleRoomEventAndSearchIsLiteral`
- `fu.fa26.sep490.g58.otcms_be.activitylog.ActivityLogPostgresIntegrationTest#catalogCreateAndUpdateAreCommittedWithSafeSearchableAuditRows`
- `fu.fa26.sep490.g58.otcms_be.activitylog.ActivityLogPostgresIntegrationTest#invalidDateRangeUsesMessage54AndMissingCompositeIdUsesMessage50`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentArrivalT053PostgresIntegrationTest#patientQrCheckInIsIdempotentAndStartsTheVisit`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentArrivalT053PostgresIntegrationTest#timelyArrivalClaimCommittedBeforeNoShowBlocksNoShowAndLateClaimCannotReopenNoShow`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentArrivalT053PostgresIntegrationTest#worklistIsReceptionistOnlyDateStateFilteredAndPaginated`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentArrivalT053PostgresIntegrationTest#manualReceptionistFallbackIsAuditedIdempotentAndRejectsPatient`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentArrivalT053PostgresIntegrationTest#issuesReceptionistOnlyNoStoreProofAndRejectsTamperWrongClinicFutureAndExpiry`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentArrivalT053PostgresIntegrationTest#receptionistCanRecordLateArrivalButItDoesNotBlockNoShow`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentArrivalT053PostgresIntegrationTest#timelyQrArrivalHoldingSharedGuardSerializesAheadOfNoShowAfterGrace`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentAvailabilityT019PostgresIntegrationTest#publicAvailabilityFiltersProviderVisibilityAcceptanceAssignmentAndNoticeWithoutLeakingPrivateData`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentAvailabilityT019PostgresIntegrationTest#patientCannotUseStaffAvailabilityAndAdminHasReadOnlyEndpoint`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentAvailabilityT019PostgresIntegrationTest#ineligibleServiceDoctorAndInvalidWindowsUseStableErrorEnvelope`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentAvailabilityT019PostgresIntegrationTest#staffAndAdminCanInspectHiddenAcceptingProvidersButNotNonAcceptingOrUnassignedOnes`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentAvailabilityT019PostgresIntegrationTest#emptyAvailabilityIsSuccessfulAndOutsideHorizonIsRejected`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentAvailabilityT019PostgresIntegrationTest#doctorMayInspectAnotherEligibleProviderForFollowUp`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentBookingT022PostgresIntegrationTest#patientCannotBookAnotherProfileAndOtherAuthenticatedRolesAreForbidden`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentBookingT022PostgresIntegrationTest#auditInsertFailureRollsBackAppointmentInsert`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentBookingT022PostgresIntegrationTest#patientCreateDerivesSelfAndWritesAtomicCreatedAuditAndResponse`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentBookingT022PostgresIntegrationTest#receptionistCanBookAccountlessProfileAndClientCannotForgeSourceOrCreator`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentBookingT022PostgresIntegrationTest#concurrentSameSlotCreatesAllowExactlyOneAppointment`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentChangeAvailabilityT020PostgresIntegrationTest#reassignmentAvailabilityRejectsPatientsAndPastOrUnchangeableBookings`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentChangeAvailabilityT020PostgresIntegrationTest#rescheduleAvailabilityReturnsOnlyCurrentDoctorsSlotsAndAllowsExistingInactiveService`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentChangeAvailabilityT020PostgresIntegrationTest#rescheduleAvailabilityEnforcesProfileScopeRoleAndPatientChangeDeadline`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentChangeAvailabilityT020PostgresIntegrationTest#adminCanReadReassignmentAvailabilityButOtherRolesAndAnonymousCannot`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentChangeAvailabilityT020PostgresIntegrationTest#reassignmentAvailabilityReturnsOnlyDifferentEligibleDoctorAtSameSlot`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentLifecycleT029PostgresIntegrationTest#doctorStartAndReceptionCancellationRaceResolvesAccordingToFirstCommittedState`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentLifecycleT029PostgresIntegrationTest#concurrentCareStartAndCancellationCommitOnlyOneConsistentOutcome`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentLifecycleT029PostgresIntegrationTest#receptionistCannotUseDoctorLifecycleTargetAndIllegalCompletionIsRejected`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentLifecycleT029PostgresIntegrationTest#existingClinicalActivityRejectsCancellationWithoutChangingEitherOwner`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentLifecycleT029PostgresIntegrationTest#appointmentAuditFailureRollsBackVisitCloseAndCancellation`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentLifecycleT029PostgresIntegrationTest#receptionistCanCancelCheckedInVisitOnlyBeforeCareStarts`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentLifecycleT029PostgresIntegrationTest#anotherActiveCareAssignmentRejectsStartWithoutChangingAppointment`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentLifecycleT029PostgresIntegrationTest#assignedDoctorStartsAndCompletesWhileVisitStaysOpenAndAppointmentIntervalIsRetained`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentQueryT023PostgresIntegrationTest#patientListIsScopedAndInclusiveDateFiltersAndPaginationAreStable`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentQueryT023PostgresIntegrationTest#detailReturnsApprovedDtoAndToDateIsInclusiveInClinicZone`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentQueryT023PostgresIntegrationTest#detailHidesOutOfScopeRecordsAndAdminIsForbidden`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentQueryT023PostgresIntegrationTest#receptionistAndDoctorListsUseClinicAndCurrentDoctorScopes`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentQueryT023PostgresIntegrationTest#lockedDoctorGetsAccessDeniedWithoutChangingAppointments`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentQueryT023PostgresIntegrationTest#badDatesAndReversedRangeAreValidationErrors`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentT013PostgresIntegrationTest#checkedInStateRequiresLinkedVisitAndInitialReception`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentT013PostgresIntegrationTest#cleanAndUpgradeMigrationPreservesVisitsAndEnforcesAppointmentHistoryLinks`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentT015PostgresIntegrationTest#completedAppointmentContinuesToReserveDoctorAndExclusionFailureRollsBack`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentT015PostgresIntegrationTest#doctorOccupancyUsesHalfOpenRangeAndIncludesBuffer`
- `fu.fa26.sep490.g58.otcms_be.appointment.AppointmentT015PostgresIntegrationTest#checkedInAndInProgressAppointmentsAlsoRemainExcluded`
- `fu.fa26.sep490.g58.otcms_be.appointment.CancelAppointmentT025PostgresIntegrationTest#receptionistRequiresReasonAndMayOverridePatientDeadline`
- `fu.fa26.sep490.g58.otcms_be.appointment.CancelAppointmentT025PostgresIntegrationTest#patientInsideDeadlineCannotCancel`
- `fu.fa26.sep490.g58.otcms_be.appointment.CancelAppointmentT025PostgresIntegrationTest#patientCancellationUpdatesStatusWritesAuditAndReleasesOccupiedSlot`
- `fu.fa26.sep490.g58.otcms_be.appointment.CheckInAppointmentT027PostgresIntegrationTest#mandatoryAuditFailureRollsBackVisitReceptionAppointmentAndReplay`
- `fu.fa26.sep490.g58.otcms_be.appointment.CheckInAppointmentT027PostgresIntegrationTest#acceptsInclusiveLateBoundaryAndRejectsOutsideWindowWithoutWrites`
- `fu.fa26.sep490.g58.otcms_be.appointment.CheckInAppointmentT027PostgresIntegrationTest#checksInAtInclusiveEarlyBoundaryAndReplayAfterProgressionDoesNotDuplicateEvidence`
- `fu.fa26.sep490.g58.otcms_be.appointment.CheckInAppointmentT027PostgresIntegrationTest#mismatchedPatientAttestationRejectsWithoutAnyCheckInWrites`
- `fu.fa26.sep490.g58.otcms_be.appointment.CheckInAppointmentT027PostgresIntegrationTest#patientIdentityProjectionIsPurposeLimitedAndReceptionistOnly`
- `fu.fa26.sep490.g58.otcms_be.appointment.CheckInAppointmentT027PostgresIntegrationTest#differentKeysRacingCreateOnlyOneVisitReceptionAuditAndReplay`
- `fu.fa26.sep490.g58.otcms_be.appointment.CheckInAppointmentT027PostgresIntegrationTest#sameIdempotencyKeyWithChangedAttestedPatientConflicts`
- `fu.fa26.sep490.g58.otcms_be.appointment.CheckInAppointmentT027PostgresIntegrationTest#rejectsNonReceptionistAndDifferentKeyAfterCheckInWithoutCreatingDuplicate`
- `fu.fa26.sep490.g58.otcms_be.appointment.CheckInAppointmentT027PostgresIntegrationTest#timelyArrivalAtInclusiveGraceBoundaryAllowsDelayedStaffProcessingWithoutQueue`
- `fu.fa26.sep490.g58.otcms_be.appointment.CheckInAuditT046MigrationPostgresIntegrationTest#cleanAndUpgradeMigrationRetainsUnknownHistoryAndGuardsNewAttestation`
- `fu.fa26.sep490.g58.otcms_be.appointment.controller.PublicRequestRateLimitT021PostgresIntegrationTest#permitsAnonymousHeadRequestsAndAppliesTheSamePublicLimit`
- `fu.fa26.sep490.g58.otcms_be.appointment.controller.PublicRequestRateLimitT021PostgresIntegrationTest#limitsCatalogAndAvailabilityIndependentlyAndReturnsSafeCorrelatedRetryResponse`
- `fu.fa26.sep490.g58.otcms_be.appointment.CreateFollowUpAppointmentT030PostgresIntegrationTest#rejectsMissingReasonAndNonDoctorWithoutWrites`
- `fu.fa26.sep490.g58.otcms_be.appointment.CreateFollowUpAppointmentT030PostgresIntegrationTest#createsFollowUpWithVisitDerivedPatientAndImmutableAudit`
- `fu.fa26.sep490.g58.otcms_be.appointment.MarkAppointmentNoShowT028PostgresIntegrationTest#rejectsGraceBoundaryAndTimelyArrivalWithoutMutation`
- `fu.fa26.sep490.g58.otcms_be.appointment.MarkAppointmentNoShowT028PostgresIntegrationTest#serializesLateCheckInAndNoShowAndAllowsOnlyNoShowTransition`
- `fu.fa26.sep490.g58.otcms_be.appointment.MarkAppointmentNoShowT028PostgresIntegrationTest#marksNoShowOnlyAfterGraceAndWritesActorReasonAndCorrelationAudit`
- `fu.fa26.sep490.g58.otcms_be.appointment.MarkAppointmentNoShowT028PostgresIntegrationTest#rejectsNonReceptionist`
- `fu.fa26.sep490.g58.otcms_be.appointment.ReassignAppointmentT026PostgresIntegrationTest#allOtherRolesAndAnonymousRemainDeniedForReassignment`
- `fu.fa26.sep490.g58.otcms_be.appointment.ReassignAppointmentT026PostgresIntegrationTest#adminReassignsAndAuditUsesActualAccountRole`
- `fu.fa26.sep490.g58.otcms_be.appointment.ReassignAppointmentT026PostgresIntegrationTest#adminDoesNotGainOtherAppointmentMutationPermissions`
- `fu.fa26.sep490.g58.otcms_be.appointment.ReassignAppointmentT026PostgresIntegrationTest#receptionistReassignsToDifferentEligibleDoctorAtSameSlotWithAudit`
- `fu.fa26.sep490.g58.otcms_be.appointment.ReassignAppointmentT026PostgresIntegrationTest#patientCannotReassignAndInvalidReplacementDoesNotChangeBooking`
- `fu.fa26.sep490.g58.otcms_be.appointment.ReassignAppointmentT026PostgresIntegrationTest#occupiedReplacementSlotLeavesOriginalDoctorAndAuditUnchanged`
- `fu.fa26.sep490.g58.otcms_be.appointment.RescheduleAppointmentT024PostgresIntegrationTest#patientCannotRescheduleAnotherProfileAndReceptionistNeedsReason`
- `fu.fa26.sep490.g58.otcms_be.appointment.RescheduleAppointmentT024PostgresIntegrationTest#occupiedTargetLeavesOldSlotAndAuditUnchanged`
- `fu.fa26.sep490.g58.otcms_be.appointment.RescheduleAppointmentT024PostgresIntegrationTest#patientRescheduleUpdatesSameBookingAndWritesCorrelatedAudit`
- `fu.fa26.sep490.g58.otcms_be.appointment.SlotAvailabilityT018PostgresIntegrationTest#combinesBoundedStaffAppointmentAndVisitIntervalsForCurrentAndSnapshotContexts`
- `fu.fa26.sep490.g58.otcms_be.auth.AdministratorAccountDatabaseVerificationTest#concurrentReissuesWaitForAccountLockAndCommitOnlyLatestCredential`
- `fu.fa26.sep490.g58.otcms_be.auth.AdministratorAccountDatabaseVerificationTest#upgradeFromV019PreservesExistingAccountsAndSessionLinks`
- `fu.fa26.sep490.g58.otcms_be.auth.AdministratorAccountDatabaseVerificationTest#simultaneousDuplicateUsernameCreatesExactlyOneAccount`
- `fu.fa26.sep490.g58.otcms_be.auth.AdministratorAccountDatabaseVerificationTest#nonAdminMutationsAreRejectedAndRealQueryDtosNeverExposeCredentials`
- `fu.fa26.sep490.g58.otcms_be.auth.T004PostgresIntegrationTest#registrationPersistsPendingAccountOtpAndPatientConsentWithoutCreatingProfile`
- `fu.fa26.sep490.g58.otcms_be.auth.T004PostgresIntegrationTest#databaseEnforcesOneCurrentOtpChallengeAndPersistsRateAuditWithoutRawIp`
- `fu.fa26.sep490.g58.otcms_be.auth.T004PostgresIntegrationTest#missingPatientConsentIsRejectedBeforeAccountCreation`
- `fu.fa26.sep490.g58.otcms_be.auth.T004PostgresIntegrationTest#duplicatePendingRegistrationReturnsSameGenericResponseAndDoesNotCreateAccountAgain`
- `fu.fa26.sep490.g58.otcms_be.auth.T004PostgresIntegrationTest#duplicateActiveRegistrationReturnsSameGenericResponseWithoutChangingPassword`
- `fu.fa26.sep490.g58.otcms_be.auth.T004PostgresIntegrationTest#emailFailureLeavesPendingAccountAndPersistsDeliveryAudit`
- `fu.fa26.sep490.g58.otcms_be.auth.T005RealLoginIntegrationTest#configuredTypedStaffCidrsAreFailClosedAndForwardingRequiresTrustedProxy`
- `fu.fa26.sep490.g58.otcms_be.auth.T005RealLoginIntegrationTest#loginIpLimitAndAuditArePersistedWithHmacOnlyIdentifier`
- `fu.fa26.sep490.g58.otcms_be.auth.T005RealLoginIntegrationTest#patientAndStaffAccountBoundariesAreEnforcedWithGenericFailure`
- `fu.fa26.sep490.g58.otcms_be.auth.T005RealLoginIntegrationTest#wrongPasswordsPersistLockoutAndSuccessResetsCounter`
- `fu.fa26.sep490.g58.otcms_be.auth.T006A8EvidenceTest#csrfAndCorsAndStaffNetworkAreEnforcedThroughMvc`
- `fu.fa26.sep490.g58.otcms_be.auth.T006A8EvidenceTest#refreshFamilyIsolationUsesIndependentTokens`
- `fu.fa26.sep490.g58.otcms_be.auth.T006A8EvidenceTest#refreshWithCsrfCookieAndFrontendHeaderReachesSessionService`
- `fu.fa26.sep490.g58.otcms_be.auth.T006A8EvidenceTest#realSecurityFilterChainPermitsAuthAndRejectsProtectedRequest`
- `fu.fa26.sep490.g58.otcms_be.auth.T006PostgresIntegrationTest#concurrentSixthLoginsNeverLeaveMoreThanFiveActiveSessions`
- `fu.fa26.sep490.g58.otcms_be.auth.T006PostgresIntegrationTest#sixthLoginEvictsOldestAndLogoutPersists`
- `fu.fa26.sep490.g58.otcms_be.auth.T006PostgresIntegrationTest#concurrentRefreshAllowsAtMostOneRotationAndReplayRevokesFamily`
- `fu.fa26.sep490.g58.otcms_be.auth.T006PostgresIntegrationTest#migratedAccountConstraintAcceptsEachExplicitRole`
- `fu.fa26.sep490.g58.otcms_be.auth.T007GoogleOnboardingPostgresIntegrationTest#consentPersistenceFailureRollsBackAccountAndLinkage`
- `fu.fa26.sep490.g58.otcms_be.auth.T007GoogleOnboardingPostgresIntegrationTest#providerSubjectLinkedToDifferentEmailRollsBackWithoutNewLinkage`
- `fu.fa26.sep490.g58.otcms_be.auth.T007GoogleOnboardingPostgresIntegrationTest#missingOnboardingDataDoesNotCreateAccountOrIdentity`
- `fu.fa26.sep490.g58.otcms_be.auth.T007GoogleOnboardingPostgresIntegrationTest#profilePersistenceFailureRollsBackAccountAndLinkage`
- `fu.fa26.sep490.g58.otcms_be.auth.T008PasswordRecoveryPostgresIntegrationTest#changingPasswordInvalidatesPreviouslyIssuedResetLink`
- `fu.fa26.sep490.g58.otcms_be.auth.T008PasswordRecoveryPostgresIntegrationTest#resetPersistsChallengeConsumesItAndRevokesAllSessions`
- `fu.fa26.sep490.g58.otcms_be.auth.UC07AdministratorAccountPostgresIntegrationTest#persistsHashedAdministratorAndRejectsDuplicateWithoutReturningPasswordAgain`
- `fu.fa26.sep490.g58.otcms_be.auth.UC07AdministratorAccountPostgresIntegrationTest#migrationPreservesPatientEmailAndLegacyStaffAccountsWithoutUsernames`
- `fu.fa26.sep490.g58.otcms_be.auth.UC08AdministratorAccountPostgresIntegrationTest#administratorRefreshInvalidatesOldAccessSessionAndAllowsNewAccessSession`
- `fu.fa26.sep490.g58.otcms_be.auth.UC08AdministratorAccountPostgresIntegrationTest#legacyAdministratorCanReceiveIssuedPasswordWithoutUsernameAndOldSessionIsRevoked`
- `fu.fa26.sep490.g58.otcms_be.auth.UC08AdministratorAccountPostgresIntegrationTest#statusChangesPreserveAccountIdentityAndRole`
- `fu.fa26.sep490.g58.otcms_be.auth.UC09AdministratorAccountPostgresIntegrationTest#searchEscapesLiteralPercentAndUnderscore`
- `fu.fa26.sep490.g58.otcms_be.auth.UC09AdministratorAccountPostgresIntegrationTest#databaseSearchFiltersRoleStatusAndPagesAndHidesEveryOtherRole`
- `fu.fa26.sep490.g58.otcms_be.billing.BillingInvoicePostgresIntegrationTest#migratesBillingSchemaAndCreatesOneCorrectInvoicePerSourceWithPartialCounterPayment`
- `fu.fa26.sep490.g58.otcms_be.content.ClinicContentPostgresIntegrationTest#publicNoticePersistsButStaffNoticeCannotBeReadPubliclyAndUnavailableDeliveryDoesNotWrite`
- `fu.fa26.sep490.g58.otcms_be.content.ClinicContentPostgresIntegrationTest#uploadStoresImageAndServesTheExactBytesWithSafeMimeType`
- `fu.fa26.sep490.g58.otcms_be.content.ClinicContentPostgresIntegrationTest#structuredClinicContactIsStoredAndReturnedToPublic`
- `fu.fa26.sep490.g58.otcms_be.content.ClinicContentPostgresIntegrationTest#articleSurvivesReloadAndPublicHomepageReadsPublishedDataOnly`
- `fu.fa26.sep490.g58.otcms_be.content.ContentAuditMigrationOrderTest#contentFirstThenActivityLogIncludesEveryContentEventExactlyOnce`
- `fu.fa26.sep490.g58.otcms_be.content.ContentAuditMigrationOrderTest#normalOrderIncludesEveryContentEventExactlyOnce`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#readAllOnlyUpdatesCurrentRecipientAndReturnsExactCount`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#listIsScopedNewestFirstAndFiltersUnread`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#receptionistCancellationPublishesOnceAndPatientSelfCancellationDoesNot`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#cancelDoesNotPublishReviewInvitation`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#readAllMixedDatasetPreservesPreReadRowsAndOtherRecipient`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#publisherRequiresAnExistingBusinessTransaction`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#bookingAndReassignmentPublishAppointmentNotifications`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#notificationInsertFailureRollsBackReviewAndHistory`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#swaggerDocumentsAll14OperationsAndPrivatePublicSchemas`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#forgedQueryCannotListAnotherAccountsNotifications`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#unavailableRelatedRecordDoesNotMarkReadTransactionRollbackOnly`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#markReadPreservesTimestampAndDoesNotUpdateRowTwice`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#newProducerNotificationFailureRollsBackSourceAndAudit(String)[1]`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#newProducerNotificationFailureRollsBackSourceAndAudit(String)[2]`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#businessRollbackAlsoRollsBackNotifications`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#reviewPublishesOnlyToActiveAdminsAndResolverChecksRoleAndExistence`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#absentForeignAndAdminCrossAccountReadsAre404`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#bookingForWalkInProfileDoesNotInventRecipient`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#notificationInsertFailureRollsBackAppointmentAndAudit`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#newProducerSourceRollbackAlsoRollsBackNotifications(String)[1]`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#newProducerSourceRollbackAlsoRollsBackNotifications(String)[2]`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#relatedVisitAvailabilityIsDynamicAndDoesNotHideNotification`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#reschedulingPublishesNewTimeAndCurrentDoctorOnceAndSkipsUnlinkedProfiles`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#twoHttpPagesAreScopedCompleteAndUseDeterministicNewestOrder`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#unreadFalseAndOmittedReturnSameMixedDataset`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#httpProjectionHasExactFieldsAndSerializesUnavailableReviewWithoutRecordData`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#closeVisitPublishesInvitationOnlyForLinkedPatient`
- `fu.fa26.sep490.g58.otcms_be.notification.NotificationPostgresIntegrationTest#migrationSupportsKindsAndRecipientIndexes`
- `fu.fa26.sep490.g58.otcms_be.OtcmsBeApplicationTests#contextLoads`
- `fu.fa26.sep490.g58.otcms_be.patient.CareRelationshipT008PostgresIntegrationTest#cleanAndV011UpgradeCreateRelationshipSchemaAndPreserveIdentity`
- `fu.fa26.sep490.g58.otcms_be.patient.CareRelationshipT008PostgresIntegrationTest#noAccountProfileRemainsReceptionistManagedAndDependentRoutesAreAbsent`
- `fu.fa26.sep490.g58.otcms_be.patient.CareRelationshipT008PostgresIntegrationTest#cleanMigrationRunsAllTwelveVersions`
- `fu.fa26.sep490.g58.otcms_be.patient.CareRelationshipT008PostgresIntegrationTest#linkedProfileIsAuthorizedButOtherPatientProfilesAndDependentRoutesAreDenied`
- `fu.fa26.sep490.g58.otcms_be.patient.PatientProfileT007PostgresIntegrationTest#openApiDocumentsNewAndExistingPatientRoutesWithoutRemovingOtherFeatures`
- `fu.fa26.sep490.g58.otcms_be.patient.PatientProfileT007PostgresIntegrationTest#migrationFromV047PreservesLegacyDataAndCreatesExpectedSchema`
- `fu.fa26.sep490.g58.otcms_be.patient.PatientProfileT007PostgresIntegrationTest#exactMatchingAndDuplicateUseDatabaseNormalizationWithoutRemovingAccents`
- `fu.fa26.sep490.g58.otcms_be.patient.PatientProfileT007PostgresIntegrationTest#concurrentManagedTransactionsAllowOnlyOneWinner`
- `fu.fa26.sep490.g58.otcms_be.patient.PatientProfileT007PostgresIntegrationTest#managedUpdateIncrementsVersionKeepsAccountAndAllowsNullAddress`
- `fu.fa26.sep490.g58.otcms_be.patient.PatientProfileT007PostgresIntegrationTest#createPersistsAdministrativeFieldsWithoutCreatingAnAccount`
- `fu.fa26.sep490.g58.otcms_be.patient.PatientProfileT007PostgresIntegrationTest#oversizedLegacyIdentifierFailsSafelyWithoutTruncationOrPartialDDL`
- `fu.fa26.sep490.g58.otcms_be.patient.PatientProfileT007PostgresIntegrationTest#competingCreatesUseTheExistingCoordinationGuardToPreventDuplicates`
- `fu.fa26.sep490.g58.otcms_be.patient.PatientProfileT007PostgresIntegrationTest#nullNationalIdAndSameNationalIdOnDifferentProfilesAreAllowed`
- `fu.fa26.sep490.g58.otcms_be.persistence.T017PersistenceCheckpointPostgresIntegrationTest#cleanSchemaMigratesV001ThroughV014WithFinalOwnerConstraints`
- `fu.fa26.sep490.g58.otcms_be.persistence.T017PersistenceCheckpointPostgresIntegrationTest#upgradeFromV001PreservesAuthAndPatientDataAndTheOriginalChecksum`
- `fu.fa26.sep490.g58.otcms_be.persistence.T032AdminResourceMutationPostgresIntegrationTest#assignmentRemovalIsBlockedByActiveCareAcrossAllServices`
- `fu.fa26.sep490.g58.otcms_be.persistence.T032AdminResourceMutationPostgresIntegrationTest#assignmentRemovalWaitsForGuardThenRechecksNewActiveCare`
- `fu.fa26.sep490.g58.otcms_be.persistence.T032AdminResourceMutationPostgresIntegrationTest#migratesV019FromCleanAndV018WhilePreservingExistingRows`
- `fu.fa26.sep490.g58.otcms_be.persistence.T032AdminResourceMutationPostgresIntegrationTest#mutationWaitsForGuardThenRechecksNewBookingAndRejectsWithoutWrites`
- `fu.fa26.sep490.g58.otcms_be.persistence.T032AdminResourceMutationPostgresIntegrationTest#adminMutationsReturnImpactConflictsAndWriteOnlySuccessfulOwnerAudits`
- `fu.fa26.sep490.g58.otcms_be.persistence.T032AdminResourceMutationPostgresIntegrationTest#checkedInAppointmentAlsoBlocksTimeOffCreation`
- `fu.fa26.sep490.g58.otcms_be.reporting.ReportingPostgresIntegrationTest#ownerCanReadButOtherRolesCannotAndInvalidRangesUseMessage72`
- `fu.fa26.sep490.g58.otcms_be.reporting.ReportingPostgresIntegrationTest#aggregatesMatchTheMigratedDatabaseAndMissingSourcesAreExplicit`
- `fu.fa26.sep490.g58.otcms_be.reporting.ReportingPostgresIntegrationTest#nonemptyDailyReportUsesVietnamDatesAndExclusiveNextMidnight`
- `fu.fa26.sep490.g58.otcms_be.reporting.ReportingPostgresIntegrationTest#emptyReportIsReadyWithZeroTotalAndNoUnapprovedExports`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#historyCannotBeUpdated`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#reviewCodePointBoundariesMatchPostgres(int, boolean)[1]`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#reviewCodePointBoundariesMatchPostgres(int, boolean)[2]`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#reviewCodePointBoundariesMatchPostgres(int, boolean)[3]`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#reviewCodePointBoundariesMatchPostgres(int, boolean)[4]`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#hiddenPublishedReviewDisappearsFromPublicHttpList`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#submittingSameVisitTwiceLeavesOneReviewAndOneSubmitHistory`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#migrationTablesForeignKeysAndIndexesExist`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#historyForeignKeyFailureRollsBackReviewMutation`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#replyAndEditPreserveStatusAndAppendImmutableSnapshots(String)[1]`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#replyAndEditPreserveStatusAndAppendImmutableSnapshots(String)[2]`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#replyAndEditPreserveStatusAndAppendImmutableSnapshots(String)[3]`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#staleEntityVersionRejectedByDatabaseUpdate`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#foreignVisitReferenceIsRejected`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#hiddenReplyDisappearsEntirelyFromPublicHttp`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#versionIncreasesForReplyAndModeration`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#ownEligibilitySubmissionModerationReplyPublicAndFilters`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#lifecycleAndGlobalCountsWithDatabaseFiltering`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#historyCannotBeDeleted`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#replyCodePointBoundariesMatchPostgres(int, boolean)[1]`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#replyCodePointBoundariesMatchPostgres(int, boolean)[2]`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#replyCodePointBoundariesMatchPostgres(int, boolean)[3]`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#replyCodePointBoundariesMatchPostgres(int, boolean)[4]`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#uniqueVisitConstraintRejectsCompetingInsert`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#concurrentAdminsCommitOnlyWinningMutationAndHistory`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#publicHttpSummaryUsesOnlyPublishedRatings(int, double)[1]`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#publicHttpSummaryUsesOnlyPublishedRatings(int, double)[2]`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#emptyPublicHttpSummaryPreservesMinimumOnePageConvention`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#rollbackReviewMutationAlsoRollsBackHistory`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#publicHomepageSizeThreeKeepsGlobalSummaryAndNewestOrderAcrossPages`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#publicHttpReturnsOnlyPublishedReviewIds`
- `fu.fa26.sep490.g58.otcms_be.review.ReviewPostgresIntegrationTest#`
- `fu.fa26.sep490.g58.otcms_be.servicecatalog.ClinicServicePostgresIntegrationTest#adminDeactivationPreservesServiceRowAndWritesAudit`
- `fu.fa26.sep490.g58.otcms_be.servicecatalog.ClinicServicePostgresIntegrationTest#publicCatalogOmitsInactiveAndNonOnlineServices`
- `fu.fa26.sep490.g58.otcms_be.servicecatalog.ClinicServicePostgresIntegrationTest#staffCatalogReturnsEveryActiveServiceAndOmitsInactiveServices`
- `fu.fa26.sep490.g58.otcms_be.servicecatalog.ClinicServicePostgresIntegrationTest#adminCannotSetNegativeServicePrice`
- `fu.fa26.sep490.g58.otcms_be.servicecatalog.ClinicServicePostgresIntegrationTest#receptionistCannotManageServices`
- `fu.fa26.sep490.g58.otcms_be.servicecatalog.ClinicServicePostgresIntegrationTest#labIndicatorsPersistThroughAdminReloadButAreAbsentFromPublicJson`
- `fu.fa26.sep490.g58.otcms_be.servicecatalog.ClinicServicePostgresIntegrationTest#cleanSchemaMigratesThroughV004`
- `fu.fa26.sep490.g58.otcms_be.staff.DoctorProfileT011PostgresIntegrationTest#cleanSchemaMigratesDoctorAndScheduleOwnershipTables`
- `fu.fa26.sep490.g58.otcms_be.staff.DoctorProfileT011PostgresIntegrationTest#adminCannotAttachNonDoctorAccount`
- `fu.fa26.sep490.g58.otcms_be.staff.DoctorProfileT011PostgresIntegrationTest#openApiKeepsDoctorListSchemaAndAddsDetailAvailabilityAndStaffRooms`
- `fu.fa26.sep490.g58.otcms_be.staff.DoctorProfileT011PostgresIntegrationTest#publicDetailUsesRealPublicAvailabilityAndListRemainsUnenriched`
- `fu.fa26.sep490.g58.otcms_be.staff.DoctorProfileT011PostgresIntegrationTest#adminLinksExistingDoctorAccountAndUnpublishedProfileStaysPrivate`
- `fu.fa26.sep490.g58.otcms_be.visit.CreateWalkInVisitT043PostgresIntegrationTest#auditFailureRollsBackVisitReceptionAndReplay`
- `fu.fa26.sep490.g58.otcms_be.visit.CreateWalkInVisitT043PostgresIntegrationTest#queueInsertFailureRollsBackWalkInReceptionAuditAndReplay`
- `fu.fa26.sep490.g58.otcms_be.visit.CreateWalkInVisitT043PostgresIntegrationTest#createsWalkInAndFourEvidenceRowsAtomicallyWithServerArrivalTime`
- `fu.fa26.sep490.g58.otcms_be.visit.CreateWalkInVisitT043PostgresIntegrationTest#createsQueuedWalkInForAccountlessPatientProfile`
- `fu.fa26.sep490.g58.otcms_be.visit.CreateWalkInVisitT043PostgresIntegrationTest#priorAppointmentMustBelongToTheSuppliedPatient`
- `fu.fa26.sep490.g58.otcms_be.visit.CreateWalkInVisitT043PostgresIntegrationTest#deniesNonReceptionistAndMapsMissingPatientToScopedNotFound`
- `fu.fa26.sep490.g58.otcms_be.visit.CreateWalkInVisitT043PostgresIntegrationTest#exactRequestReplayReturnsTheSameCreationResponseWithoutDuplicateRows`
- `fu.fa26.sep490.g58.otcms_be.visit.CreateWalkInVisitT043PostgresIntegrationTest#auditRowsRejectUpdateDeleteAndTruncate`
- `fu.fa26.sep490.g58.otcms_be.visit.CreateWalkInVisitT043PostgresIntegrationTest#rejectsSameKeyWithDifferentRequestAndLeavesFirstVisitUnchanged`
- `fu.fa26.sep490.g58.otcms_be.visit.CreateWalkInVisitT043PostgresIntegrationTest#concurrentWalkInReplayCreatesExactlyOneVisitAndReception`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitCareCapacityT031PostgresIntegrationTest#capacityConflictKeepsAssignmentAbsentAndPersistsCoordinationAudit`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitCareCapacityT031PostgresIntegrationTest#cancellationQueueFailureRollsBackVisitClosureAndAudit`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitCareCapacityT031PostgresIntegrationTest#roomDeactivationAndCareStartSerializeUnderSharedGuard`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitCareCapacityT031PostgresIntegrationTest#cancellationIsRejectedAfterCareStarts`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitCareCapacityT031PostgresIntegrationTest#activeReservationBlocksCapabilityChangeAndDeactivationButAllowsLabelUpdate`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitCareCapacityT031PostgresIntegrationTest#careStartQueueFailureRollsBackCareAndRoomWrites`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitCareCapacityT031PostgresIntegrationTest#doctorClosureMovesWaitingWalkInQueueEntryToHistory`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitCareCapacityT031PostgresIntegrationTest#receptionistCancellationClosesWalkInAndMovesQueueEntryToHistory`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitCareCapacityT031PostgresIntegrationTest#roomCapacityConflictDoesNotCreateCareAssignmentAndPersistsBothAuditSignals`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitCareCapacityT031PostgresIntegrationTest#concurrentCareStartsCannotAllocateTheSameRoom`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitCareCapacityT031PostgresIntegrationTest#appointmentBookingAndDoctorCareStartCannotBothClaimOverlappingCapacity`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitCareCapacityT031PostgresIntegrationTest#createsCareReservationAndImmutableAuditThenCompletesWithActualEnd`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitReceptionReadT049PostgresIntegrationTest#receptionistQueueRequiresReceptionistAndValidPagination`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitReceptionReadT049PostgresIntegrationTest#receptionistGetsClinicDayFifoPagesAndOnlyCurrentRoomAssignment`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitReceptionReadT049PostgresIntegrationTest#patientSeesOnlyOwnProgressAndPositionOnlyWhileWalkInIsWaiting`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitRoomAdminT031PostgresIntegrationTest#staffReadReusesAdminProjectionAndDatabaseOrderingIncludingInactiveRooms`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitRoomAdminT031PostgresIntegrationTest#nonAdminCannotViewOrMutateRooms`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitRoomAdminT031PostgresIntegrationTest#adminCreatesListsDeactivatesAndReactivatesRoomWithImmutableAudit`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitT012PostgresIntegrationTest#cleanSchemaMigratesVisitReceptionClinicalAndReplayConstraints`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitT012PostgresIntegrationTest#recordedArrivalReaderRequiresAppointmentPatientAndInclusiveWindowMatch`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitT012PostgresIntegrationTest#followUpEvidenceRequiresSameDoctorCareCompletionAndIndication`
- `fu.fa26.sep490.g58.otcms_be.visit.VisitT012PostgresIntegrationTest#v018UpgradePreservesExistingVisitCareAndRoomRows`
- `fu.fa26.sep490.g58.otcms_be.visit.WalkInQueueT048MigrationPostgresIntegrationTest#upgradesV026AndEnforcesQueueExitActorByReason`
- `fu.fa26.sep490.g58.otcms_be.visit.WalkInReceptionQueueT045PostgresIntegrationTest#rejectsUnauthorizedRoleAndProtectsVisitSourceArrivalAndHistory`
- `fu.fa26.sep490.g58.otcms_be.visit.WalkInReceptionQueueT045PostgresIntegrationTest#rejectsInvalidInitialAndExitStatesAndProvidesFifoIndexes`
- `fu.fa26.sep490.g58.otcms_be.visit.WalkInReceptionQueueT045PostgresIntegrationTest#createsQueueEntryAndImmutableInitialHistoryAndAllowsANewEntryAfterExit`
- `fu.fa26.sep490.g58.otcms_be.visit.WalkInReceptionQueueT045PostgresIntegrationTest#serializesQueueCreationAgainstVisitClosure`
- `fu.fa26.sep490.g58.otcms_be.visit.WalkInReceptionQueueT045PostgresIntegrationTest#rejectsQueueRowsForScheduledVisits`
- `fu.fa26.sep490.g58.otcms_be.visit.WalkInReceptionQueueT045PostgresIntegrationTest#migrationUpgradesFromV022AndPreservesExistingVisitRows`
