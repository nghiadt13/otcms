# Bằng chứng kiểm phase 00 — Codex tiếp tục từ HANDOFF.md

Ngày: 13/11/2026. Nhánh: `final-db`. Commit nền của plan: `610bea1c`.

## Phạm vi đã làm

- Đọc handoff, quy ước repo và các file phase mà handoff trỏ tới.
- Rà V110/V111/V114/V115/V118 theo các quyết định trong handoff.
- Chạy nối tiếp 84 migration gốc và 10 migration staged, không di chuyển file staged vào thư mục Flyway đang chạy.
- Viết V120: bỏ bảng dùng chung `staff_resource_audits`, 2 trigger của bảng và hàm `reject_staff_resource_audit_mutation`. Không dùng `CASCADE`.
- Script `apply-migrations-scratch.sh` tìm repo tương đối theo vị trí script; chỉ cho phép tên DB nháp `chk`, `otcms_scratch`, `scratch_*`, tránh vô tình drop DB dev/test.
- Thêm `tools/verify-staged-schema.mjs`: đối chiếu tên bảng với plan, kiểm hàm trigger mồ côi/tham chiếu SQL tới bảng đã bỏ; thử chống trùng và UPDATE/DELETE/TRUNCATE trên audit trong transaction rồi rollback.

## Kết quả migration

Chạy từ repo bằng Git Bash:

```bash
SCRATCH_DB=chk bash plans/261113-final-db/tools/apply-migrations-scratch.sh \
  $(ls backend/src/main/resources/db/migration-staged/V*.sql | xargs -n1 basename)
node plans/261113-final-db/tools/verify-staged-schema.mjs chk
```

- 84 migration gốc: đạt.
- V109, V110, V111, V112, V113, V114, V115, V118, V119, V120: đạt, từng file chạy trong transaction.
- 35 bảng, đúng từng tên vật lý ở `plan.md` mục 2.
- 0 hàm trigger mồ côi trong schema public.
- 0 hàm public chứa tham chiếu SQL tĩnh tới bảng đã drop (kiểm FROM/JOIN/UPDATE/INTO/REFERENCES).
- Audit: UNIQUE dedup_key, chặn UPDATE/DELETE/TRUNCATE đều đạt.
- Không thay đổi migration gốc V001–V108.
- Đã dọn 5 DB `scratch_*` còn tồn tại lúc dọn. Giữ `chk` cho người rà xem cấu trúc.

## API và backend test

- So `api-baseline.json` với snapshot hiện có: `no differences`.
- Backend test chạy lại bằng `mvnw.cmd test`, Docker port 5433: **2051 test, 3 failures + 2 errors, 0 skipped**. Maven trả exit code 1 đúng vì 5 lỗi; không dựa vào exit code của shell bọc lệnh.
- Cả 5 trùng baseline trong handoff: 2 method `Kan27StaffAccountMigrationPostgresTest`, `T005RealLoginIntegrationTest.wrongPasswordsPersistLockoutAndSuccessResetsCounter`, `AdministratorAccountDatabaseVerificationTest.upgradeFromV019PreservesExistingAccountsAndSessionLinks`, `ContentAuditMigrationOrderTest.contentFirstThenActivityLogIncludesEveryContentEventExactlyOnce`. Không có lỗi mới.
- Log thô nằm ở `.snapshots/codex-baseline.log` (không commit).
- App profile `final-db` khởi động thành công với DB Docker `otcms_dev` qua port 5433; HTTP tạm port 18080. `/v3/api-docs` trả HTTP 200, 203 paths. So OpenAPI lấy trực tiếp từ app với baseline: `no differences`. Đã dừng app sau smoke test.
- Không lưu mật khẩu trong tài liệu này hoặc log bàn giao.

## Giới hạn kết luận

- Đây là kiểm cấu trúc và chạy chuỗi migration trên DB nháp dựng từ migration gốc; chưa chứng minh bảo toàn mọi trạng thái dữ liệu của từng module.
- Code nghiệp vụ chưa refactor. Các cổng test API/hành vi phải chạy lại khi thực hiện các phase 01–10.
- Bản staged hiện là phương án **35 bảng**: 33 bảng đã chốt cộng 2 bảng Claude giữ thêm để giảm sửa code. Người dùng vừa nhắc lại mốc 33; chưa tự bỏ 2 bảng vì sẽ kéo theo sửa API hoặc cơ chế đối soát ảnh.
- Phần kiểm phase 00 đã hoàn tất cho phương án staged 35 bảng. Các thay đổi vẫn ở working tree, chưa commit/push; chỉ commit nền plan đã có trên remote. Không tự chuyển sang phase 01.
