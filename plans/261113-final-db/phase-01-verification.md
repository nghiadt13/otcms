# Kiểm chứng phase 01 — audit chung và khóa giao dịch

Ngày: 13/11/2026. Nhánh: `final-db-p01-audit-logs`.

## Thay đổi

- V109 chuyển từ staged sang migration active; V001–V108 không đổi. Tạo `audit_logs`, view `activity_log_entries` đọc bảng mới, bỏ `transaction_guard`.
- `AuditEntry` + `AuditLogWriter` dùng chung. Bắt buộc có transaction nghiệp vụ; INSERT thường hoặc INSERT chống trùng theo `dedup_key`. Repository không công khai UPDATE/DELETE.
- JSON before/after/metadata được chuẩn hoá qua ObjectMapper rồi che theo cấu trúc, kể cả object/list lồng nhau và tên trường camelCase/snake_case. Không đưa chuỗi JSON bị thay regex trực tiếp vào jsonb.
- Correlation ID được đưa vào MDC trong request, khôi phục/xoá trong finally, lưu cùng audit. Có actor ID nhưng thiếu actor type thì dùng ACCOUNT.
- Khóa điều phối dùng `pg_advisory_xact_lock(4903490001)` trong transaction; giữ kiểm tra READ COMMITTED, lock_timeout 2s và statement_timeout 5s.
- Truy vấn nhật ký xử lý record_label NULL nhất quán giữa danh sách và chi tiết; DTO/API không đổi.

## Phạm vi test

- `AuditLogWriterIntegrationTest`: snapshot JSON, thời điểm, actor/MDC, MANDATORY, rollback cả hai chiều, chống trùng tuần tự/đồng thời, trigger UPDATE/DELETE/TRUNCATE, rate-limit theo outcome/cutoff.
- `CoordinationAdvisoryLockIntegrationTest`: loại trừ giữa transaction, giải phóng khi commit/rollback, cấu hình timeout 2s, SQLSTATE 55P03 khi chờ quá hạn, bắt buộc transaction.
- `CorrelationIdMdcTest`: truyền correlation ID hợp lệ, thay input sai, dọn/khôi phục MDC kể cả khi request lỗi.
- `ActivityLogViewOverAuditLogsTest`: Admin/Owner đọc được, Patient bị từ chối, không lộ snapshot/metadata; visible=false không truy cập được; nhãn NULL vẫn xuất hiện; payment hold và lịch hết hạn bị ẩn còn CONFIRMED hiển thị; lọc record không biến thành SQL injection.
- Test chờ khóa lịch hẹn và tài nguyên quản trị quan sát advisory lock thay cho row lock. Không thay logic nghiệp vụ appointment/visit.
- Test view cũ dùng fixture mới hoặc xác nhận trạng thái chuyển tiếp được plan cho phép. Không bỏ test hay thêm Disabled.

## Kết quả

- Hồi quy cuối: `./mvnw.cmd -B -ntp test` với env Docker trong `agent-rules.md`: **2067 test, 3 failures + 2 errors, 0 skipped**, 10 phút 11 giây, Maven exit 1. Cả 5 đúng baseline phase 00: hai method `Kan27StaffAccountMigrationPostgresTest`, `T005RealLoginIntegrationTest.wrongPasswordsPersistLockoutAndSuccessResetsCounter`, `AdministratorAccountDatabaseVerificationTest.upgradeFromV019PreservesExistingAccountsAndSessionLinks`, `ContentAuditMigrationOrderTest.contentFirstThenActivityLogIncludesEveryContentEventExactlyOnce`.
- **16 test mới đạt**: writer 8, advisory lock 2, MDC 2, view/API 4. Các test chờ khóa và test cũ sửa fixture cũng đạt. Không có Disabled mới.
- Lượt tập trung ban đầu: 13/13 đạt. Lượt full đầu phát hiện fixture cần sửa và race billing; sau sửa, chạy nhóm 55 test (53 đạt, lỗi content baseline và race billing), rồi chạy lại toàn bộ. Lượt full cuối không lặp lại race billing; không coi như race đã được sửa.
- OpenAPI từ snapshot test: `node plans/261113-final-db/tools/compare-openapi.mjs plans/261113-final-db/api-baseline.json plans/261113-final-db/.snapshots/openapi-current.json` → `no differences`, không dùng allow-list.
- Smoke app thật: profile `final-db`, DB `otcms_dev` trên Docker port 5433, HTTP tạm 18080; Flyway lên V109, Hibernate validate đạt. `/v3/api-docs` HTTP 200, 203 paths/279 schemas; so trực tiếp baseline → `no differences`. Đã dừng app sau kiểm tra. Log: `.snapshots/phase01-app-smoke.log`; JSON: `.snapshots/phase01-live-api.json` (không commit).
- Review chỉ đọc độc lập: đã sửa phát hiện redaction snake_case và nhãn NULL; lượt review tiếp theo không tìm thấy blocker trong code phase 01. Review billing xác định đường lỗi không gọi thành phần phase 01; chi tiết `phase-01-existing-billing-race.md`.
- Chạy lại active V001–V109 và staged V110–V120 trên DB Docker nháp `chk`: đạt, **35 bảng** đúng danh sách; 0 hàm trigger mồ côi/tham chiếu SQL tĩnh tới bảng đã bỏ; trigger audit và dedup đạt.
- Script kiểm schema đọc cả migration active V109+ và staged, nên không bỏ sót bảng đã drop khi migration được kích hoạt.
- Không còn `transaction_guard` trong Java đang chạy. Các migration lịch sử vẫn chứa tên bảng này để tái tạo rồi drop đúng thứ tự.
- `git diff --check` và kiểm index: đạt; không sửa migration V001–V108. Không sửa frontend, SecurityConfig hay code nghiệp vụ billing.
- Log thô (không commit): `.snapshots/phase01-focused.log`, `phase01-full.log`, `phase01-focused-final.log`, `phase01-regression-final.log`.

## Giới hạn và bàn giao

- Phase 01 không chuyển writer nghiệp vụ của auth, catalog, appointment, visit, clinical… sang audit mới. Nhật ký tạm thiếu các nguồn legacy tới phase sở hữu chuyển writer; đây là thay đổi chuyển tiếp đã ghi trong plan.
- DB chạy app chỉ áp V109, **chưa phải schema cuối 35 bảng**. Con số 35 ở trên là kết quả chuỗi migration đầy đủ trên DB nháp.
- V110–V120 vẫn staged. Chỉ kích hoạt cùng code phase tương ứng.
- Năm lỗi backend baseline vẫn được theo dõi riêng; không sửa ngoài phạm vi.
- Race billing được quan sát hai lần trước khi lượt full cuối đạt lại; giữ nguyên test và bàn giao theo `phase-01-existing-billing-race.md`. Không lờ đi phát hiện chỉ vì lần cuối xanh ở nhóm billing.
- Chưa bắt đầu phase 02 và chưa merge vào final-db/product.
