# Dựng Docker + DB giống máy NghiaDT (đợt final-db)

Làm đúng thứ tự. Xong mục 6 mà kết quả khớp thì môi trường của bạn đã chuẩn. Luật chung vẫn ở `agent-rules.md` (đọc hết trước khi code).

## 1. Cần cài

- Docker Desktop (đang chạy), Java 21, Node 24, Git.
- Máy NghiaDT: Docker 29.x, image `postgres:17-bookworm` (PostgreSQL 17.11), `max_connections` = 100 (mặc định), timezone container = UTC (mặc định, KHÔNG đổi).

## 2. Lấy code

```bash
git fetch origin
git switch final-db-p03-diagnostic-imaging   # nhánh P03 đang làm dở
```

- `final-db` @ `bbb9ad7c`: đã merge P00, P01, P02, P04, P05, P06, P07, P08, P09. Còn P03 (đang làm) và P10.
- Không commit thẳng vào `final-db` hay `final-db-p03-diagnostic-imaging`. Tạo nhánh con từ nhánh P03, ví dụ `final-db-p03-long-diagnostic`, rồi mở PR vào `final-db-p03-diagnostic-imaging`.

## 3. Bật Postgres Docker

Trong thư mục gốc repo (`otcms/`), PowerShell:

```powershell
# Mật khẩu = giá trị OTCMS_TEST_DB_PASSWORD trong backend/.env CỦA BẠN (Spring đọc .env, giá trị đó thắng).
# Gõ tay vào shell, không ghi vào file, không in ra.
$env:OTCMS_DB_PASSWORD = '<giá trị OTCMS_TEST_DB_PASSWORD của bạn>'
# Máy có Postgres local chiếm 5432 thì dùng 5433 (máy NghiaDT dùng 5433):
$env:OTCMS_DB_PORT = '5433'
docker compose -f docker-compose.final-db.yml up -d
```

- Compose tạo container `otcms-final-db` và 2 DB `otcms_dev` (chạy app), `otcms_test_db` (test), qua `docker/final-db-init/01-create-databases.sql`.
- Mật khẩu chỉ áp lúc tạo volume. Đã từng tạo với mật khẩu khác, hoặc volume cũ từ trước 13/11: `docker compose -f docker-compose.final-db.yml down -v` rồi `up -d` lại (chỉ trên máy mình).
- Kiểm tra:

```powershell
docker exec otcms-final-db psql -U postgres -At -c "select version()" -c "show max_connections" -c "show timezone" -c "select datname from pg_database order by 1"
```

Phải thấy `PostgreSQL 17.x`, `100`, `Etc/UTC`, có `otcms_dev` và `otcms_test_db`.

## 4. DB test riêng cho phần bạn làm

```powershell
docker exec otcms-final-db psql -U postgres -c "CREATE DATABASE otcms_test_db_p03"
```

Làm sạch (sau khi pull migration mới, hoặc Flyway báo checksum lệch):

```powershell
docker exec otcms-final-db psql -U postgres -c "DROP DATABASE otcms_test_db_p03 WITH (FORCE)" -c "CREATE DATABASE otcms_test_db_p03"
```

Lý do phải làm sạch: các phase merge không theo thứ tự số migration (ví dụ V115 vào sau V119); Flyway không chạy migration số nhỏ hơn số đã chạy, và V118 đã được sửa trong lúc làm.

## 5. Biến môi trường mỗi lần chạy test (PowerShell, trong `otcms/backend`)

```powershell
$env:OTCMS_TEST_DB_PASSWORD = '<như mục 3>'   # phải là biến môi trường thật: vài test tự mở JDBC
$env:OTCMS_TEST_DB_PORT = '5433'              # đúng cổng ở mục 3
$env:OTCMS_TEST_DB_NAME = 'otcms_test_db_p03'
$env:APP_AUTH_LOGIN_REQUESTS = '10000'
$env:APP_AUTH_LOGIN_WINDOW_SECONDS = '3600'
$env:SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS = '*:missing'
$env:APP_PAYMENT_PAYOS_ENABLED = 'false'
```

## 6. Kiểm môi trường chuẩn (phải khớp)

```powershell
./mvnw.cmd test "-Dtest=OpenApiContractSnapshotTest"
```

→ `Tests run: 1, Failures: 0, Errors: 0`, BUILD SUCCESS (Flyway chạy 93 migration tới V119 trên DB trống, Hibernate `ddl-auto: validate` qua).

Từ `otcms/`:

```powershell
node plans/261113-final-db/tools/compare-openapi.mjs plans/261113-final-db/api-baseline.json plans/261113-final-db/.snapshots/openapi-current.json --allow "/time-offs|/api/auth/patient/google" --allow-schema "GoogleExchangeRequest|TimeOff|ItemsDoctorIntervalResponse"
```

→ `No differences`. (Git Bash: thêm `MSYS_NO_PATHCONV=1` trước `node`.)

Máy NghiaDT, 13/11 tối, nhánh P03 @ commit bàn giao: cả hai lệnh trên đạt như trên.

## 7. Bộ test đầy đủ: kết quả chuẩn để so

`./mvnw.cmd test` trên `final-db` @ `bbb9ad7c`: **2097 chạy, 2 lỗi, 7 bỏ qua**, khoảng 6 phút. Hai lỗi có sẵn (không phải do bạn, không sửa):

- `T005RealLoginIntegrationTest.wrongPasswordsPersistLockoutAndSuccessResetsCounter`
- `AdministratorAccountDatabaseVerificationTest.upgradeFromV019PreservesExistingAccountsAndSessionLinks`

7 bỏ qua có sẵn: Kan27 (2), Kan29 (2), ContentAuditMigrationOrder (2), MedicalHistoryR3 migrationAppliesAfterV072… (1). `DiagnosticEvidenceT013…` thỉnh thoảng lỗi do đồng hồ Docker lệch vài giây: chạy lại riêng class đó, đạt thì bỏ qua. Lỗi khác ngoài danh sách này là do thay đổi mới.

## 8. Chạy app (khi cần thử bằng tay)

```powershell
$env:SPRING_PROFILES_ACTIVE = 'final-db'   # profile này trỏ Docker, DB otcms_dev
$env:SERVER_PORT = '18003'                 # cổng riêng, không đụng app khác
./mvnw.cmd spring-boot:run
```

`otcms_dev` đã chạy migration trước khi pull bản mới thì xoá và tạo lại DB đó (như mục 4), không dùng `repair`.

## 9. Cấm

- Trỏ app hoặc test nhánh final-db vào Supabase (`SUPABASE_DB_URL`).
- Sửa file `.env*`, in mật khẩu ra log, PR, Jira.
- Chạy 2 lệnh `mvnw` cùng lúc trong một thư mục (hỏng class, sinh lỗi giả). Đọc log để lấy kết quả, không tin mã thoát.
- `down -v` khi trên máy có người/agent khác đang chạy test.
- Fixture "vừa hết hạn" lệch 1 giây: dùng ít nhất `now() - interval '10 minutes'` (đồng hồ Docker lệch 1–3 giây).
