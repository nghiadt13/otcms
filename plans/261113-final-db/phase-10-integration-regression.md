---
phase: 10
title: "Ghép, dọn cuối, hồi quy toàn bộ, merge vào product"
status: pending
priority: P1
effort: "3h"
owner: NghiaDT (Long, Hùng cùng đi tay)
dependencies: [1, 2, 3, 4, 5, 6, 7, 8, 9]
migration: V120__final_db_final_cleanup.sql
---

# Phase 10: Ghép, dọn cuối, hồi quy toàn bộ, merge vào product

## Overview
Khi PR 01–09 đã vào `final-db`, phase này làm 4 việc:
- drop các bảng dùng chung mà nhiều phase còn ghi trong lúc làm song song;
- kiểm đủ 33 bảng;
- chạy hồi quy toàn bộ (BE, FE, đi tay);
- merge một lần vào `product` và reset Supabase.

## Requirements
- V120 chỉ chứa phần dọn cuối:
  - `DROP TABLE staff_resource_audits` (phase 02 và 07 cùng ghi bảng này, nên phải đợi cả hai đã chuyển sang `AuditLogWriter`);
  - drop các hàm trigger cũ không còn bảng nào dùng (lấy danh sách bằng truy vấn ở bước 2).
- Không có tính năng nào trên `product` bị mất, trừ nghỉ phép và đăng nhập Google.

## Related Code Files
- Move: `db/migration-staged/V120__final_db_final_cleanup.sql` → `db/migration/`.
- Delete: thư mục `db/migration-staged/`. Phải rỗng sau bước này; nếu còn file thì phase tương ứng chưa xong.
- Modify: `otcms/AGENTS.md`: gỡ khối "Đang refactor DB trên nhánh `final-db`" ở đầu file.
- Modify: `otcms/docs/test-accounts.md` nếu seed tài khoản đổi (file này bị gitignore, chỉ sửa ở máy).

## Implementation Steps
1. Kiểm đủ phase: `ls backend/src/main/resources/db/migration-staged/` chỉ còn V120.
2. Trên DB Docker sạch, sau khi chạy app một lần để Flyway áp đủ:
   ```sql
   SELECT count(*) FROM information_schema.tables
    WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history';   -- phải ≤ 35, mục tiêu 35
   SELECT p.proname FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
    WHERE n.nspname='public' AND p.prorettype='trigger'::regtype
      AND NOT EXISTS (SELECT 1 FROM pg_trigger t WHERE t.tgfoid=p.oid);   -- hàm trigger mồ côi → đưa vào V120
   ```
   Với mỗi hàm còn dùng: kiểm thân hàm không nhắc tới bảng đã drop.
   ```sql
   SELECT proname FROM pg_proc WHERE prosrc ~* '<tên bảng cũ>'
   ```
3. Chuyển V120 sang `db/migration`, reset DB, chạy lại bước 2: phải ra 35 bảng và không còn hàm mồ côi.
4. Backend: chạy `./mvnw test`. Chỉ được còn 4 lỗi có sẵn và các test migration cũ đã `@Disabled` (gom danh sách từ các PR).
5. API: so OpenAPI với baseline. Chỉ được lệch ở endpoint đã bỏ:
   ```
   --allow "/time-offs|/api/auth/patient/google" --allow-schema "GoogleExchangeRequest|TimeOff"
   ```
6. Frontend (`otcms/frontend`): `npm run lint`, `npm test` (7 test `ReceptionScreen` lỗi có sẵn), `npx next build`.
7. Đi tay trên giao diện (app profile `final-db` + FE). Mỗi người một nhóm, ghi đạt/lỗi vào PR:
   - **NghiaDT:**
     - đăng nhập (BN, nhân viên bằng username, Admin);
     - nhập sai mật khẩu nhiều lần thì bị chặn;
     - đổi avatar;
     - quên mật khẩu;
     - đổi email;
     - quản lý tài khoản nhân viên và hồ sơ bác sĩ;
     - chỉ định cận lâm sàng → KTV upload DICOM → xem viewer → bác sĩ đọc và hoàn tất kết quả → lượt khám thấy kết quả;
     - nhật ký hệ thống hiện đủ 13 nguồn.
   - **Long:**
     - đặt lịch online có phí (giữ giờ 15 phút, hết hạn);
     - huỷ, đổi bác sĩ;
     - check-in (QR và tại quầy, gửi trùng);
     - walk-in;
     - hàng chờ tiếp đón → phòng khám;
     - bác sĩ khám, kê đơn, kết thúc lượt khám.
   - **Hùng:**
     - hồ sơ BN và tiền sử;
     - thêm người thân bằng OTP;
     - ca làm việc;
     - phòng (năng lực phòng, giữ phòng không trùng);
     - dịch vụ;
     - thanh toán payOS sandbox (webhook gửi lại 2 lần);
     - đánh giá và kiểm duyệt;
     - nội dung web có ảnh bìa;
     - thông báo chung, thông báo cá nhân.
8. Chạy thử migration trên một bản Supabase rỗng: nhánh Supabase tạm hoặc project rỗng. **Không** chạy trên DB Supabase chung.
9. Merge `final-db` → `product` bằng PR, NghiaDT duyệt.
10. Sau khi merge: reset DB Supabase chung (dữ liệu demo), để app tự chạy Flyway, rồi kiểm lại tài khoản test.
11. Báo cả nhóm: kéo `product` mới; xoá DB test local cũ (`otcms_test_db`) hoặc chuyển hẳn sang Docker.

## Success Criteria
- [ ] 35 bảng (≤ 35), 0 hàm trigger mồ côi, `db/migration-staged/` đã xoá.
- [ ] `./mvnw test`: chỉ lỗi có sẵn + test migration cũ đã `@Disabled` có lý do.
- [ ] OpenAPI chỉ lệch ở endpoint đã bỏ.
- [ ] FE lint / test / build đạt (trừ 7 lỗi có sẵn).
- [ ] Danh sách đi tay ở bước 7 đạt hết; mục lỗi nào cũng có ticket.
- [ ] `product` chạy được trên Supabase sau khi reset.

## Risk Assessment
- **Supabase khác Docker** (extension, quyền): bước 8 bắt lỗi trước khi merge. Nếu lỗi thì sửa migration trên nhánh refactor; không sửa tay trên Supabase.
- **Hồi quy tìm ra lỗi lớn ở một cụm sát giờ:** sửa trên nhánh của phase đó, đi lại bước 4–7 cho cụm đó. Không merge `product` khi chưa đạt.
