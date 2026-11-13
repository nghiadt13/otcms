# Luật chung cho người và AI agent — đợt gộp DB, tối đa 35 bảng

> Đọc HẾT file này trước khi mở file phase của mình. Có chỗ nào phase ghi khác file này thì làm theo phase. Có chỗ nào không rõ thì DỪNG và hỏi NghiaDT; không đoán.

## 1. Đọc gì, theo thứ tự

1. File này.
2. `plan.md` (cùng thư mục): bảng tên bảng cuối cùng, thứ tự merge, số migration.
3. File phase được giao, ví dụ `phase-05-visits-queue.md`. Chỉ làm đúng phase đó.
4. Khi cần chi tiết cột của bảng mới: `schema-33-tables.md` (cùng thư mục).
5. Quy ước code có sẵn: `otcms/AGENTS.md`, `otcms/backend/CONSTITUTION.md` (chỉ phần SEC, DATA, API, ARCH, TEST), package mẫu `review` và `notification`.

Không đọc thêm: `old/`, `docs/requirements/`, các plan cũ trong `otcms/plans/` khác thư mục này.

## 2. Mục tiêu của đợt này (và KHÔNG phải mục tiêu)

- Mốc người dùng chốt là 33 bảng, cho phép tối đa 35. Bản migration staged hiện giữ thêm `website_images` và `imaging_reconciliation_cases`, nên có 35 bảng; xem `plan.md` mục 2 và ghi chú bàn giao. **API giữ nguyên tuyệt đối**: URL, method, request body, response body, mã HTTP, mã MSG. FE không phải sửa (trừ 2 tính năng bị bỏ: nghỉ phép bác sĩ, đăng nhập Google).
- KHÔNG làm: thêm tính năng, đổi tên class/hàm cho đẹp, format lại file, "dọn" code không liên quan, nâng thư viện, sửa lỗi ngoài phạm vi phase. Thấy lỗi ngoài phạm vi → ghi vào mục "Ghi chú cho NghiaDT" trong mô tả PR.

## 3. Nhánh và PR

- Nhánh tích hợp: `final-db` (tách từ `product`). KHÔNG commit thẳng vào `final-db` hay `product`.
- Mỗi phase một nhánh con: `final-db-pNN-<tên-ngắn>`, ví dụ `final-db-p05-visits-queue`. Tách từ `final-db` mới nhất.
- Trước khi mở PR: `git fetch && git rebase origin/final-db`, chạy lại kiểm tra ở mục 7.
- Một phase = một PR vào `final-db`. Tiêu đề PR: `refactor(final-db): PNN <tên phase>`. Mô tả PR dùng mẫu ở mục 8.
- Commit theo conventional commit, không nhắc tới AI.

## 4. Database: chỉ dùng Docker local

- Khởi động: trong `otcms/` chạy `docker compose -f docker-compose.final-db.yml up -d`. Postgres 17 (image `postgres:17-bookworm`, vì bản Debian mới không nhận múi giờ `Asia/Saigon` của JVM) chạy ở `127.0.0.1:${OTCMS_DB_PORT:-5432}` với 2 DB: `otcms_dev` (chạy app) và `otcms_test_db` (chạy test).
- **Mật khẩu container phải bằng `OTCMS_TEST_DB_PASSWORD` trong file `backend/.env` của bạn** (Spring đọc `.env` và giá trị đó thắng biến môi trường). Đặt `OTCMS_DB_PASSWORD=<giá trị đó>` trong shell khi chạy `docker compose … up` lần đầu (mật khẩu chỉ áp lúc tạo volume; đổi thì phải `down -v`). Không ghi mật khẩu vào file nào.
- Máy đã có Postgres local chiếm 5432: đặt `OTCMS_DB_PORT=5433` (cho Docker và app) và `OTCMS_TEST_DB_PORT=5433` (cho test).
- **CẤM** trỏ app nhánh refactor vào Supabase (`SUPABASE_DB_URL`). Chạy app dùng profile `final-db` (`SPRING_PROFILES_ACTIVE=final-db`), profile này trỏ vào Docker.
- Sau mỗi lần pull/rebase có migration mới của người khác: reset DB rồi chạy lại: `docker compose -f docker-compose.final-db.yml down -v && docker compose -f docker-compose.final-db.yml up -d`. Lý do: các phase merge không theo thứ tự số migration; Flyway không cho chạy migration số nhỏ hơn số đã chạy.
- Không sửa file `.env*`. Không in mật khẩu ra log hay vào PR.

## 5. Migration

- Migration của đợt này do NghiaDT viết sẵn trong `backend/src/main/resources/db/migration-staged/`, mỗi phase một file đã đánh số (xem bảng ở `plan.md`).
- Việc của phase: **chuyển** (git mv) đúng file của mình từ `migration-staged/` sang `db/migration/`, trong CÙNG PR với code. Lý do: Hibernate chạy `ddl-auto: validate`; migration vào mà code chưa sửa (hoặc ngược lại) thì app không khởi động.
- Thấy migration staged sai hoặc thiếu cột so với code cần → sửa trong PR của mình, ghi rõ trong mô tả PR mục "Đổi migration". Không viết migration mới ngoài file đã cấp, trừ khi phase cho phép.
- KHÔNG sửa bất kỳ file nào đã có trong `db/migration/` (V001–V108).

## 6. Cách code chuẩn (bắt buộc)

### 6.1 Entity và repository
- Mỗi bảng đúng 1 entity JPA. Xoá entity + repository của bảng bị bỏ; KHÔNG để lại class "deprecated" hay comment code cũ.
- Bảng bị gộp thành cột: dữ liệu đọc thẳng từ entity của bảng cha. **Không gọi thêm repository chỉ để lấy một cờ hay một mốc giờ.**
  - Sai (cũ): `accountAvatarRepository.existsById(accountId)` sau khi đã `findById` account.
  - Đúng (mới): `account.getAvatarContentType() != null` trên entity vừa đọc.
- Cột file/ảnh `bytea` (`accounts.avatar_content`, `website_contents.cover_image`) **không map vào entity**. Đọc/ghi bằng query riêng trong repository (`@Query(nativeQuery = true)` trả `byte[]`, `@Modifying` để ghi). Lý do: dự án không bật bytecode enhancement nên `@Basic(fetch = LAZY)` không có tác dụng; entity `accounts` được đọc ở mọi request.
- Cột `jsonb`: map bằng `@JdbcTypeCode(SqlTypes.JSON)` sang record/DTO có kiểu, không dùng `Map<String,Object>` trừ `metadata` của `audit_logs`.
- Cột mảng `text[]`: `@JdbcTypeCode(SqlTypes.ARRAY)` + `List<String>`.
- Cột `doctor_profile_id` ở các bảng khác **giữ tên**, nhưng giờ trỏ tới `staff_profiles(id)`. Không đổi tên field Java `doctorProfileId`.

### 6.2 Audit
- Mọi ghi log thao tác đi qua `AuditLogWriter` (package `common.audit`, có từ phase 01). Không tạo entity/repository audit riêng.
- Gọi trong CÙNG transaction với thao tác nghiệp vụ. Giá trị `source`/`category`/`action`/`record_type`/`entity_id`/`record_label` ghi đúng theo **bảng ánh xạ trong `phase-01-audit-logs-and-coordination-lock.md`**, để nhật ký hệ thống hiện y như cũ. `before_state`/`after_state` lấy đúng các trường bảng audit cũ đang lưu.
- Bảng audit cũ dùng chung bởi 2 phase (`staff_resource_audits`): mỗi phase chỉ chuyển phần code ghi của mình, KHÔNG drop bảng; phase 10 drop.
- Không ghi dữ liệu nhạy cảm (mật khẩu, token, mã OTP, CCCD) vào `before_state`/`after_state`/`metadata`.

### 6.3 Chống gửi trùng
- Bảng idempotency/replay/outbox bị bỏ. Thay bằng cột `*_idempotency_key` có `UNIQUE` trên bảng nghiệp vụ. Bắt `DataIntegrityViolationException` của đúng constraint đó rồi trả lại kết quả của bản ghi đã có (giữ hành vi cũ). Không kiểm trùng bằng SELECT trước rồi INSERT.

### 6.4 Ranh giới module
- Cần dữ liệu của module khác → gọi service công khai của module đó. Không thêm SQL đọc/ghi chéo bảng của module khác. Chỗ cũ đã đọc chéo (liệt kê trong phase) giữ nguyên cách cũ nhưng đổi sang bảng mới.
- Lỗi trả cho client mang mã MSG của R3 qua exception có sẵn của module. Không viết chuỗi thông báo mới.
- `SecurityConfig`: chỉ được thêm/xoá đúng dòng phase chỉ định.

### 6.5 Test
- Test cũ chạy SQL thẳng vào bảng bị bỏ → sửa sang bảng mới, giữ nguyên ý nghĩa assert. KHÔNG xoá test để cho xanh. Test của tính năng bị bỏ (nghỉ phép, Google) thì xoá cùng tính năng.
- Test migration cũ kiểu `upgradeFromV0xx…` đọc bảng đã bỏ: đánh `@Disabled("final-db: bảng X đã gộp vào Y")`, ghi vào PR. Không sửa nội dung để giả xanh.
- Mỗi chỗ thay idempotency bằng UNIQUE phải có 1 test gửi trùng → nhận đúng kết quả cũ.

## 7. Kiểm tra trước khi mở PR (dán kết quả thật vào PR)

Trong `otcms/backend`, đặt env theo `otcms/AGENTS.md` mục test (`APP_AUTH_LOGIN_REQUESTS=10000`, `APP_AUTH_LOGIN_WINDOW_SECONDS=3600`, `OTCMS_TEST_DB_PASSWORD`, `SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS=*:missing`; `APP_PAYMENT_PAYOS_ENABLED` khác `true`).

1. Reset DB Docker (mục 4).
2. `./mvnw test` — báo số test chạy / lỗi. Lỗi có sẵn được phép: `AdministratorAccountDatabaseVerificationTest.upgradeFromV019…`, `T005RealLoginIntegrationTest.wrongPasswords…`, `Kan27StaffAccountMigrationPostgresTest`, `ContentAuditMigrationOrderTest` (và các test migration đã `@Disabled` theo 6.5). Lỗi khác = phải sửa.
3. So hợp đồng API: `./mvnw test -Dtest=OpenApiContractSnapshotTest` (ghi `plans/261113-final-db/.snapshots/openapi-current.json`), rồi từ thư mục `otcms/`: `node plans/261113-final-db/tools/compare-openapi.mjs plans/261113-final-db/api-baseline.json plans/261113-final-db/.snapshots/openapi-current.json`. Kết quả phải là "no differences". Chỉ phase được phép xoá endpoint mới thêm `--allow <regex đường dẫn>` và `--allow-schema <regex tên DTO>`, đúng như phase ghi. Từ sau phase 02 luôn dùng `--allow "/time-offs|/api/auth/patient/google" --allow-schema "GoogleExchangeRequest|TimeOff|ItemsDoctorIntervalResponse"`. Trong Git Bash phải đặt `MSYS_NO_PATHCONV=1` trước lệnh `node`, nếu không regex `/time-offs` bị đổi thành đường dẫn Windows.
   - Đồng hồ của Docker VM có thể lệch 1–3 giây so với máy. Fixture test đặt mốc "vừa hết hạn" phải lệch ít nhất vài phút (ví dụ `now() - interval '10 minutes'`), không dùng 1 giây.
   - Một số integration test tự mở JDBC cần `OTCMS_TEST_DB_PASSWORD` là biến môi trường thật (file `.env` không đủ). Đặt biến này trong shell, không in ra, không đọc `.env`.
4. Các lệnh grep "phải rỗng" ghi trong phase.
5. Không chạy 2 lệnh `mvnw` cùng lúc.

## 8. Mẫu mô tả PR

```
Phase: PNN <tên>   Người làm: <tên>
Bảng bỏ: ...       Bảng/cột mới: ...
Migration: Vxxx (đã chuyển từ staged) | Đổi migration: không / có — <lý do>
Kết quả mvnw test: <n> chạy, <m> lỗi (liệt kê, đánh dấu lỗi có sẵn)
So OpenAPI: no differences | <diff được phép>
Grep phải rỗng: <lệnh> → rỗng
File ngoài danh sách phase đã sửa: không / có — <file, lý do>
Ghi chú cho NghiaDT: <lỗi ngoài phạm vi, câu hỏi>
```

## 9. Khi nào DỪNG và hỏi NghiaDT

- Phải sửa file không có trong mục "Related Code Files" của phase (trừ test và chỗ gọi tới class bị xoá).
- R3 hoặc phase mâu thuẫn với code đang chạy.
- Một test cũ lỗi mà không hiểu vì sao.
- Muốn đổi chữ ký API, thêm bảng, hay đổi migration của phase khác.
