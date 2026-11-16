# HANDOFF: final-db — phase 00 và phần triển khai phase 01

Ngày: 13/11/2026. Nhánh làm việc: `final-db-p01-audit-logs`, tách từ `final-db` tại `610bea1c`. Người dùng yêu cầu hoàn tất phase 01, review kỹ, tách commit phase 00/01 và push nhánh này. Chưa merge vào `final-db` hoặc `product`; chưa làm phase 02.

## Đọc đúng phạm vi

1. Workspace `AGENTS.md`, repo `AGENTS.md`.
2. `plans/261113-final-db/agent-rules.md` và `plan.md` mục 2–3.
3. `phase-01-verification.md` để lấy kết quả test/review mới nhất; không suy trạng thái từ bản checklist Claude cũ.
4. `phase-01-existing-billing-race.md` cho phát hiện ngoài phạm vi.
5. File phase được giao tiếp theo. Không đọc toàn bộ transcript hay plan cũ.

## Phase 00 đã hoàn tất kiểm chứng

- Docker container `otcms-final-db`, PostgreSQL 17-bookworm, loopback port 5433 trên máy NghiaDT. Có `otcms_dev` và `otcms_test_db`.
- Profile `final-db`, test profile và 9 test tự mở JDBC đọc biến port thích hợp. Không dùng Supabase cho refactor.
- Baseline OpenAPI: 203 paths, 279 schemas, lưu trong `api-baseline.json`.
- Đã rà đủ staged V109–V120, viết V120, chạy chuỗi trên DB nháp `chk`: 35 bảng đúng tên, không trigger function mồ côi hay tham chiếu SQL tĩnh tới bảng đã bỏ; audit immutable/dedup đạt.
- Mốc phase 00: 2051 backend test, 3 failures + 2 errors đúng 5 lỗi baseline. App profile final-db khởi động được trên Docker; OpenAPI không lệch.
- Chi tiết: `phase-00-verification.md`. Không chạy lại các việc này chỉ vì handoff Claude trước đây còn ghi migration chưa viết/chưa kiểm.

## Phase 01 đã triển khai

- V109 đã chuyển sang `db/migration/`; V110–V120 còn ở `db/migration-staged/`. V001–V108 không sửa.
- Có `AuditEntry`, `AuditLogWriter`, entity và repository chung. Ghi trong transaction bắt buộc, native INSERT chống trùng, JSON được che theo cấu trúc trước khi lưu; actor/time/MDC được giữ.
- `transaction_guard` được thay bằng advisory transaction lock dùng khóa 4903490001. Giữ READ COMMITTED, lock_timeout 2s và statement_timeout 5s.
- View activity log đọc audit_logs. Reader xử lý nhãn NULL; API/DTO không đổi.
- Filter đặt và khôi phục MDC; không để correlation ID rò sang request sau.
- Có 16 test mới cho audit/rollback/dedup/concurrency/immutability/MDC/authorization/view/payment-hold.
- Các test liên quan khóa và view cũ đã cập nhật, không xoá hay Disabled test. Test lịch sử content projection ghim V108.
- Bàn giao phase 04: bỏ đoạn chép fixture audit tạm trong test payment hold khi writer thật đã chuyển sang audit_logs. Phase 07: khôi phục kỳ vọng API service/room có log sau khi chuyển writer. Cả hai đã ghi ngay trong file phase tương ứng.
- Kết quả hồi quy cuối, lệnh chạy và giới hạn xem `phase-01-verification.md`. Log thô nằm trong `.snapshots/`, không commit.

## Phát hiện billing cần theo dõi riêng

Test webhook đối đầu người dùng nhả giữ lịch đã lộ race: invoice bị đọc trước khi lấy pessimistic lock, webhook cập nhật version ở giữa, Hibernate báo stale version trước bước refresh. Cơ chế này nằm trong code không đổi từ 610bea1c; đường lỗi không gọi audit writer/view, coordination guard mới hay servlet filter.

Đây là phát hiện mới về cơ chế race có sẵn; không tự gộp vào 5 lỗi baseline lịch sử. Chưa chạy nguyên commit cũ để xác nhận lịch sử test thất bại. Không sửa billing trong phase 01. Đã ghi đầy đủ ở `phase-01-existing-billing-race.md` và trỏ tới từ phase 08; cần xử lý hoặc quyết định rõ trước nghiệm thu phase 10.

## Mốc 33 bảng và trần 35

Người dùng chốt 33, cho phép tối đa 35. Bản staged hiện giữ thêm website_images và imaging_reconciliation_cases nên là 35; đã ghi rõ trong plan. Kiểm chuỗi SQL trên chk không chứng minh mọi tính năng chạy trên schema cuối. DB app hiện mới áp V109, chưa gộp toàn bộ xuống 35 bảng.

## Môi trường và Git

- Không đọc/sửa `.env*`, không in mật khẩu hay khoá; không restore tài liệu bị sparse-checkout ẩn.
- Docker dùng OTCMS_DB_PORT=5433; app dùng SPRING_PROFILES_ACTIVE=final-db. Test dùng OTCMS_TEST_DB_PORT=5433, OTCMS_TEST_DB_PASSWORD hợp lệ, APP_AUTH_LOGIN_REQUESTS=10000, APP_AUTH_LOGIN_WINDOW_SECONDS=3600, SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS=*:missing và APP_PAYMENT_PAYOS_ENABLED=false.
- Không chạy hai Maven trên cùng thư mục. Log đọc từ `.snapshots/`, không từ backend/target.
- `plans/` bị ignore: force-add đúng file được giao; tuyệt đối không add `.snapshots/` hay bản probe Git trong đó.
- Kiểm trạng thái commit/push bằng git status/log và remote; không suy từ handoff. Người dùng đã cho phép commit/push toàn bộ phase 00/01, chưa yêu cầu merge.

## Việc tiếp theo

Sau khi tích hợp phase 01 vào final-db theo quyết định của NghiaDT: phase 02 là phần core tiếp theo; các thành viên làm cụm theo thứ tự/phụ thuộc trong plan. Chỉ kích hoạt migration cùng code của phase. Không tự làm phase 02 khi yêu cầu hiện tại mới tới phase 01. Jira chưa tạo trong phiên này.
