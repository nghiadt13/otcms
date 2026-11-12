---
phase: 0
title: "Nhánh, Docker, baseline API, migration staged"
status: pending
priority: P1
effort: "4h"
owner: NghiaDT
dependencies: []
---

# Phase 00: Nhánh, Docker, baseline API, migration staged

## Overview
Dựng nền cho mọi phase khác:
- nhánh `final-db`;
- Postgres trong Docker;
- profile `final-db`;
- ảnh chụp hợp đồng API trước khi sửa;
- toàn bộ file migration V109–V120 đặt sẵn trong `db/migration-staged/`.

## Requirements
- Tạo nhánh từ `product` @ 21e304cd (V108). Mọi người chạy được DB bằng 1 lệnh.
- Đường dẫn DB test vẫn mặc định `127.0.0.1:5432`, nhưng đổi được port qua biến môi trường. Lý do: máy NghiaDT có Postgres local chiếm 5432.
- Baseline OpenAPI chụp từ code `product`, CHƯA có thay đổi nào.
- Migration staged không được Flyway đọc. Flyway chỉ đọc `classpath:db/migration`; kiểm `spring.flyway.locations` để chắc chắn.

## Related Code Files
- Create: `otcms/docker-compose.final-db.yml`
  - service `postgres`, image `postgres:17` (kiểm lại bản Supabase đang dùng bằng `SELECT version();` trên Supabase, rồi dùng cùng bản chính);
  - port `${OTCMS_DB_PORT:-5432}:5432`;
  - volume tên `otcms-final-db-data`;
  - mount `docker/final-db-init/`.
- Create: `otcms/docker/final-db-init/01-create-databases.sql` (`CREATE DATABASE otcms_dev; CREATE DATABASE otcms_test_db;`).
- Create: `backend/src/main/resources/application-final-db.yml`
  - datasource `jdbc:postgresql://127.0.0.1:${OTCMS_DB_PORT:5432}/otcms_dev`;
  - user/password lấy từ `OTCMS_DB34_USERNAME` / `OTCMS_DB34_PASSWORD`;
  - không chứa mật khẩu thật.
- Modify: `backend/src/test/resources/application-test.yml`
  - chỉ đổi dòng `url` thành `jdbc:postgresql://127.0.0.1:${OTCMS_TEST_DB_PORT:5432}/otcms_test_db`;
  - giữ comment cấm dùng Supabase.
- Create: `backend/src/main/resources/db/migration-staged/` chứa:
  - `V109__final_db_audit_logs_and_drop_transaction_guard.sql`
  - `V110__final_db_accounts_auth_staff.sql`
  - `V111__final_db_patients_caregivers.sql`
  - `V112__final_db_shifts_rooms_services.sql`
  - `V113__final_db_reviews_content.sql`
  - `V114__final_db_appointments.sql`
  - `V115__final_db_visits_queue.sql`
  - `V118__final_db_diagnostic_imaging_orders.sql`
  - `V119__final_db_billing_payos.sql`
  - `V120__final_db_final_cleanup.sql`
- Create: `otcms/plans/261113-final-db/api-baseline.json` (chụp từ app chạy code `product`).
- Create: `otcms/plans/261113-final-db/tools/compare-openapi.mjs`
  - Script Node, không cài thêm thư viện. So `paths` (method + parameters + requestBody schema + responses) và `components.schemas` của 2 file.
  - In ra từng khác biệt. Không có khác biệt thì in "no differences".
  - Tham số `--allow <regex đường dẫn>` để bỏ qua các endpoint đã cho phép xoá.

## Implementation Steps
1. Tạo nhánh và đẩy lên:
   ```
   git fetch origin
   git checkout -b final-db origin/product
   git push -u origin final-db
   ```
2. Tạo file compose và init SQL. Chạy `docker compose -f docker-compose.final-db.yml up -d`, rồi kiểm có đủ 2 DB.
3. Tạo `application-final-db.yml`. Chạy app `SPRING_PROFILES_ACTIVE=final-db` để Flyway chạy V001–V108 trên `otcms_dev`.
4. Gọi `curl -s localhost:8080/v3/api-docs` và lưu thành `api-baseline.json`. Format JSON bằng `node -e` để diff dễ đọc.
5. Viết `compare-openapi.mjs`. Thử so baseline với chính nó, kết quả phải là "no differences".
6. Viết 10 file migration staged theo `schema-33-tables.md` và bảng tên vật lý ở `plan.md` mục 2. Mỗi file theo thứ tự:
   1. tạo bảng mới / thêm cột mới (kèm UNIQUE, CHECK, index cho mọi cột FK, GIN cho `text[]` và `jsonb` có lọc);
   2. chép dữ liệu demo từ bảng cũ (`INSERT … SELECT`) để seed có sẵn không mất;
   3. drop trigger/hàm cũ, viết lại trigger cần giữ (bảng ở `plan.md` mục 4);
   4. drop bảng cũ.

   Không dùng `IF EXISTS` để che lỗi thứ tự.
7. Kiểm từng file staged trên DB nháp: copy tạm sang `db/migration`, chạy Flyway trên một DB Docker rỗng, kiểm `\dt`, rồi trả file về staged. Không commit bản copy.
8. Đẩy lên `final-db`. Đây là commit duy nhất được làm thẳng trên nhánh tích hợp: `chore(final-db): add docker db, api baseline, staged migrations`.

## Success Criteria
- [ ] `docker compose -f docker-compose.final-db.yml up -d` tạo đủ 2 DB, app profile `final-db` khởi động được trên code `product`.
- [ ] `./mvnw test` trên nhánh mới cho cùng kết quả với `product` (chỉ 4 lỗi có sẵn), chạy với `OTCMS_TEST_DB_PORT` trỏ vào Docker.
- [ ] `node tools/compare-openapi.mjs api-baseline.json api-baseline.json` in ra "no differences".
- [ ] Áp lần lượt V109–V120 lên DB rỗng không lỗi; `\dt public.*` (không tính `flyway_schema_history`) ra 35 bảng.
- [ ] `grep -rn "supabase" backend/src/main/resources/application-final-db.yml` rỗng.

## Risk Assessment
- **Bản Postgres Docker khác Supabase làm cú pháp lệch.** Dấu hiệu: migration chạy được ở Docker nhưng lỗi trên Supabase lúc merge. Xử lý: dùng cùng bản chính; phase 10 chạy thử trên Supabase trước khi merge.
- **Viết hết migration trước có thể sai so với code.** Đã chấp nhận: phase sửa file staged trong PR của mình (agent-rules mục 5).
