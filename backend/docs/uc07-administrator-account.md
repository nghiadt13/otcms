# UC07 — Clinic Owner creates Administrator account

Nguồn yêu cầu: requirement UC07 do người dùng cung cấp trong phiên làm việc này.
Phạm vi chỉ gồm tạo Administrator và phần authentication cần để bắt buộc đổi issued password.
Không triển khai UC08/UC09, frontend, tạo staff profile, commit hoặc push.

## Contract

- `POST /api/v1/owner/administrator-accounts`: authenticated `OWNER`, có CSRF token.
- Body: `{"username":"clinic.admin"}`. Field ngoài contract, kể cả `role`, `accountType`,
  `password`, `mustChangePassword`, bị từ chối.
- Username phải là chuỗi không rỗng, tối đa 64 UTF-16 code units; không chứa ký tự điều khiển
  hoặc surrogate đơn lẻ. Giới hạn 64 theo convention mã định danh ngắn như roomCode;
  project chưa có giới hạn username riêng. DTO và service dùng chung AdministratorUsernamePolicy,
  giữ nguyên giá trị và so sánh unique chính xác theo PostgreSQL, không thêm regex/normalization.
- Thành công: `201`, `Cache-Control: no-store`, body gồm `accountId`, `username`,
  `accountType=ADMIN`, `mustChangePassword=true`, `temporaryPassword`.
- Temporary password chỉ tồn tại trong create result; không có endpoint đọc lại credential.
  Không log response này. DTO có `toString()` đã redact.
- Duplicate: `409 USERNAME_ALREADY_EXISTS`; dữ liệu không hợp lệ: `400`.
- `401` khi chưa xác thực, `403` khi không phải OWNER.

## Initial status — ACTIVE

Requirement clarification do người dùng cung cấp, được nhóm xác nhận: Administrator do Clinic Owner
tạo có initial status `ACTIVE`. Đây là nguồn business requirement; không suy từ demo/test seed.

`OwnerAdministratorService.create()` gán trực tiếp `AccountStatus.ACTIVE` trước khi persist account.
Minimal refactor đã bỏ lớp `AdministratorInitialStatusPolicy` chỉ trả về hằng số này.
Không có runtime configuration cho initial status, nhánh 501 hoặc yêu cầu bổ sung cấu hình.
Các test create assert status được lưu là `ACTIVE`.

## Credential và first login

- `TemporaryPasswordGenerator` dùng `SecureRandom` 256 bits, Base64 URL không padding:
  43 ASCII characters, đáp ứng policy hiện có 12–64 characters, tối đa 72 UTF-8 bytes.
- Dùng bean `PasswordEncoder` hiện có để hash; database chỉ lưu hash.
- Không tạo email giả hoặc yêu cầu email ngoài SRS. New Administrator có thể không có email.
- `POST /api/auth/staff/administrator/login`: body `username`, `password`; dùng cùng
  credential/rate/lockout logic và staff network policy. API email login hiện có giữ nguyên.
- Login response bổ sung `mustChangePassword`; không chứa temporary password hoặc hash.
- Với issued-password session, backend chỉ cho `POST /api/auth/change-password`,
  `POST /api/auth/logout`, `GET /api/auth/csrf`. Chức năng khác trả `403 PASSWORD_CHANGE_REQUIRED`.
- Refresh token cũng không được rotate khi còn required change, kể cả request không có JWT.
- Change-password dùng endpoint hiện có, yêu cầu current password và policy hiện có;
  hash mới, xóa flag và revoke refresh sessions trong cùng transaction.
- Sau đổi password phải login lại. Với mọi Administrator, kể cả legacy email-based ADMIN, filter kiểm tra session
  hiện hành để access JWT thuộc session đã revoke không tự có quyền khi flag đã được xóa.

## Persistence và transaction

Migration UC07: `V020__add_administrator_credentials.sql`.
Trạng thái schema hiện tại còn bao gồm `V021__allow_legacy_administrator_password_reissue.sql` của UC08.

- Thêm nullable `username` TEXT có UNIQUE constraint; account cũ giữ username NULL.
- Không có `loginName`/username cũ trong business account model. Staff email login hiện có dùng
  `normalized_email`; chỉ giữ một field username mới, không tạo thêm loginName.
- Thêm `must_change_password` BOOLEAN NOT NULL DEFAULT FALSE; account cũ không bị buộc đổi.
- V020 ban đầu thêm CHECK yêu cầu username cho ADMIN có issued-password flag. V021 bỏ
  `ck_accounts_issued_admin_username` để legacy email-based ADMIN có thể nhận password reissue
  mà không phải được tạo username. UC07 vẫn bắt buộc username tại request/service boundary.
  Không áp username NOT NULL cho Patient hoặc legacy staff/owner/admin; không yêu cầu backfill.
- Sau V021, UNIQUE username và CHECK username không rỗng khi có giá trị vẫn giữ nguyên;
  `must_change_password` vẫn BOOLEAN NOT NULL DEFAULT FALSE, account cũ không bị buộc đổi password.
- Cho phép email NULL chỉ khi là username-based ADMIN; account khác vẫn cần email.
- Không sửa các migration đã áp dụng; không thay đổi role enum; không tạo staff profile.
- Service create có `@Transactional`, kiểm tra OWNER trước khi truy cập persistence,
  cố định ADMIN, khóa username bằng Auth advisory-lock infrastructure, kiểm tra duplicate
  và `saveAndFlush`. Unique constraint là lớp bảo vệ cuối cùng.
- Chỉ duplicate đúng username constraint mới map về conflict; persistence failure khác có lỗi an toàn,
  không đính kèm exception chứa SQL parameter vào lỗi trả client.

## Shadow Plan và Action Record

- Authorization: yêu cầu trực tiếp implement UC07 của người dùng; không coi đó là Human Final Review.
- File boundary: module `auth`, `config/SecurityConfig.java`, V020, test auth và tài liệu UC07 này.
- Trình tự: kiểm tra account/repository/encoder/login/role/security → xác định migration →
  create và first-login enforcement → compile → test UC07 → auth regression → PostgreSQL integration.
- Không thêm dependency, không gọi dịch vụ email, không triển khai UC08/UC09.
- Compile: `.\mvnw.cmd -DskipTests test-compile` → PASS sau khi chốt initial status ACTIVE.
- Toàn bộ UC07: `.\mvnw.cmd '-Dtest=UC07*Test' test` → 19 tests: 17 PASS, 0 assertion failures,
  2 integration context errors do PostgreSQL local từ chối kết nối. Suite trả BUILD FAILURE do môi trường.
- Auth regression không cần database: 45 tests PASS, 0 failures/errors/skipped.
- Exact UC07 unit-test command:

```powershell
.\mvnw.cmd '-Dtest=UC07CreateAdministratorAccountTest,UC07AdministratorAccountControllerTest,UC07FirstLoginSecurityTest,UC07AdministratorLoginTest' test
```

- Exact auth-regression command (45 tests, không cần database):

```powershell
.\mvnw.cmd '-Dtest=PatientRegistrationT004Test,T005ContractTest,T005CredentialLoginServiceTest,T006SecurityContractTest,T006HttpSecurityEvidenceTest,T006A12EvidenceTest,T007GoogleAuthenticationServiceTest,T008PasswordRecoveryServiceTest,T009SmtpEmailDeliveryTest,T010PasswordRecoveryControllerTest,T010CommonSecurityContractTest,SessionCsrfWebMvcTest' test
```

- Integration command: `.\mvnw.cmd '-Dtest=UC07AdministratorAccountPostgresIntegrationTest' test`.
- Integration result của phiên triển khai ban đầu: ENVIRONMENT FAILURE — 2 tests, 0 assertion failures, 2 context errors;
  PostgreSQL `127.0.0.1:5432` từ chối kết nối. Chưa chạy được assertion và chưa xác minh V020 trên DB.
- Không còn clarification nghiệp vụ initial status. Database/migration vẫn cần PostgreSQL local test evidence.
- Sync-back: tài liệu này mô tả implementation; không sửa approval/status của artifact FEAT-001 cũ.
- Human Final Review: PENDING HUMAN REVIEW. Không đánh dấu approval thay Human.

## Scope correction

- Initial-status requirement đã được nhóm chốt là ACTIVE qua hướng dẫn mới nhất của người dùng.
  Giữ username và các phần UC07 khác; không thay đổi enum status hoặc tự ánh xạ thêm Inactive.
- V020 được chỉnh trước khi có successful migration evidence; không thay đổi migration cũ V001–V019.
- Dữ liệu trước V020 có email NOT NULL, username mới NULL và flag mới FALSE, nên các CHECK mới
  không yêu cầu username/backfill cho account cũ. Đây là kiểm tra tĩnh theo schema, chưa phải
  bằng chứng chạy migration trên database thực tế.
- Bổ sung PostgreSQL integration case cho Patient và legacy staff/admin/owner không có username;
  các test này vẫn cần local PostgreSQL để thực thi.

## PostgreSQL verification sau khi Docker Engine hoạt động — 2026-10-23

- Container riêng `otcms-postgres-test`, PostgreSQL 18.6, bind `127.0.0.1:5432`, database
  `otcms_test_db`; dùng credential từ application-test.yml, không dùng Supabase/production.
- UC07AdministratorAccountPostgresIntegrationTest: 2 PASS trên PostgreSQL thật.
- Flyway public schema chạy đến V021; history ghi V019/V020/V021 success, không migration failed.
- AdministratorAccountDatabaseVerificationTest bổ sung upgrade schema riêng tại V019,
  seed các role hiện có và session links, chạy V020 rồi V021 và validate; dữ liệu cũ được giữ nguyên,
  username NULL/must_change_password=false cho legacy accounts, legacy ADMIN có thể set flag sau V021.
- Các ghi nhận connection refused phía trên là evidence lịch sử, đã được lượt chạy DB thành công thay thế.
- Xem phần PostgreSQL Docker verification mới nhất trong `uc08-uc09-administrator-accounts.md`
  để biết exact commands, test-fixture fixes và regression tổng hợp. Không thay Human approval.

## Minimal security fixes — 2026-10-26

### Shadow Plan / Action Record

- Authorization/checkpoint: người dùng yêu cầu sửa tối thiểu ba findings recovery/username,
  thêm test, giữ kiến trúc Controller → Service → Repository và behavior refresh hiện tại.
- File boundary: PasswordRecoveryServiceImpl, CreateAdministratorAccountRequest,
  OwnerAdministratorService, AdministratorUsernamePolicy mới; sáu test files UC07/UC08/T008/T010
  và tài liệu UC07 này. Không sửa frontend, migrations hoặc UC_description.html; không commit/push.
- Plan: kiểm tra convention username → sửa validation/supersession → regression unit/MVC/DB
  → git diff --check → ghi evidence. Không thêm dependency.
- change(): supersede mọi reset challenge chưa consumed/superseded trong cùng transaction
  đổi hash, xóa mandatory-change flag và revoke sessions. Reset link cũ không còn dùng được.
- reset(): dùng PasswordPolicy trước khi xử lý credential; Unicode vượt 72 UTF-8 bytes
  trả 400 VALIDATION_FAILED qua AuthExceptionHandler, không gọi encoder/persistence.
- Username: tối đa 64 UTF-16 code units; chặn ISO control và surrogate đơn lẻ, không regex;
  shared policy áp dụng tại DTO/service trước advisory lock hoặc persistence. Không trim/lowercase.
- Refresh production không đổi. Test PostgreSQL xác nhận sau rotation, access JWT cũ còn
  validate chữ ký/thời hạn nhưng session bị từ chối; access JWT mới có session được phép.

### Verification

JDK 21; Maven 3.9.16 đã có trong wrapper cache. Wrapper script lỗi khởi động
`Cannot index into a null array`, nên dùng executable cache đã được tài liệu kiểm chứng.
Lượt compile trong sandbox bị lỗi truy cập Maven cache; lượt ngoài sandbox compile thành công.
Lượt test đầu: 99 PASS, 0 assertion failures, 21 context errors do PostgreSQL connection refused.
Khởi động Docker Desktop; container có sẵn otcms-postgres-test tự chạy, healthy,
chỉ bind 127.0.0.1:5432. Không tạo/reset database hoặc thay cấu hình credential.

Exact command lượt cuối:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
& 'C:/Users/ad/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0/bin/mvn.cmd' '-Dmaven.repo.local=C:/Users/ad/.m2/repository' '-Dtest=UC07*Test,UC08*Test,UC09*Test,AdministratorAccountDatabaseVerificationTest,PatientRegistrationT004Test,T005ContractTest,T005CredentialLoginServiceTest,T006SecurityContractTest,T006HttpSecurityEvidenceTest,T006A12EvidenceTest,T007GoogleAuthenticationServiceTest,T008PasswordRecoveryServiceTest,T008PasswordRecoveryPostgresIntegrationTest,T009SmtpEmailDeliveryTest,T010PasswordRecoveryControllerTest,T010CommonSecurityContractTest,SessionCsrfWebMvcTest,T006PostgresIntegrationTest,T006A8EvidenceTest' test
git diff --check
```

- Final result: 120 PASS, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.
- UC07: 22 PASS; UC08: 20 PASS; UC09: 16 PASS; database verification: 4 PASS;
  auth/password/session regression còn lại: 58 PASS. Có 8 test mới.
- Evidence: target/owner-administrator-minimal-fixes-test.log và target/surefire-reports.
- git diff --check: PASS. Residual blocker trong phạm vi sửa tối thiểu: không có.
- Sync-back: cập nhật contract username và ghi evidence tại đây; không thay approval cũ.
- AI Agent Recommendation: PENDING HUMAN REVIEW. Human Final Review: PENDING HUMAN REVIEW;
  không tự ghi quyết định thay Human reviewer.
