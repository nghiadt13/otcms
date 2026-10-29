# Ghi chú Merge: `nghiadt13` vào `product` (29/10/2026)

Tài liệu này ghi lại chi tiết các xung đột và cách giải quyết khi tích hợp toàn bộ tính năng từ nhánh `nghiadt13` vào nhánh `product`.

---

## 1. Bối cảnh
- Nhánh `product` đang ở trạng thái phiên bản cũ (236500a), các module xác thực và giao diện bệnh nhân còn phụ thuộc vào API giả lập (mock) hoặc Supabase cũ.
- Nhánh `nghiadt13` đã phát triển và hoàn thiện:
  - Tích hợp toàn bộ backend thật (JWT authentication, endpoint `GET /api/v1/me`, quản lý Owner Admin UC07-UC09 của Hùng, Flyway migrations V032-V033).
  - Tích hợp luồng tiếp tân và hàng đợi khám vãng lai của Quang (ưu tiên thai sản, độ tuổi, chống trùng lịch hẹn).
  - Tái cấu trúc khu vực bệnh nhân (PortalAreaFrame, live appointment booking wizard, màn thông tin cá nhân).
  - Hoàn thiện toàn diện cơ chế bảo mật CSRF (gửi `X-CSRF-TOKEN` trên request POST/PUT/PATCH/DELETE và tự động lấy lại token mới khi bị 403).
  - Vượt qua 100% test (1,260 tests Vitest, compile backend thành công).

---

## 2. Chi tiết 10 file xung đột và cách giải quyết (Ưu tiên bản của `nghiadt13`)

Tất cả các xung đột đều nằm ở `frontend/` và đều được giải quyết bằng cách chọn phiên bản của `nghiadt13`:

### 1. `frontend/src/lib/auth/authSession.ts`
* **Xung đột:** `product` chưa có các hàm quản lý CSRF token cho API dữ liệu.
* **Cách giải quyết:** Giữ bản `nghiadt13`, bổ sung `getCsrfToken(refresh)` (quản lý token CSRF cho mutation requests) và `peekSession()` (đọc nhanh session hiện hành trong tab mà không kích hoạt refresh).

### 2. `frontend/src/lib/api/client.ts`
* **Xung đột:** `product` chỉ gửi Authorization header cơ bản; `nghiadt13` có cơ chế bảo vệ CSRF và retry.
* **Cách giải quyết:** Giữ bản `nghiadt13`. Đối với các phương thức ghi (`POST`, `PUT`, `DELETE`), client tự động gửi kèm `X-CSRF-TOKEN` và `credentials: "include"`. Khi gặp mã lỗi 403 do lệch CSRF cookie, client tự động refresh token mới và retry lại 1 lần duy nhất.

### 3. `frontend/src/features/auth/api/authApi.ts`
* **Xung đột:** `product` chỉ gọi đăng nhập xong rồi kết thúc.
* **Cách giải quyết:** Giữ bản `nghiadt13`. Sau khi `signIn` thành công, client gọi ngay `fetchCurrentAccount()` để nạp sẵn thông tin tài khoản (họ tên, vai trò) vào bộ nhớ, giúp header hiển thị ngay tên người dùng mà không cần chờ.

### 4. `frontend/src/features/auth/components/SignInForm.tsx`
* **Xung đột:** Điểm điều hướng fallback khi không có tham số `next`.
* **Cách giải quyết:** Giữ bản `nghiadt13`. Điều hướng về trang chủ `/` (nơi header đã hiển thị tên và nút truy cập cổng cá nhân) thay vì ép về `/portal`.

### 5. `frontend/src/components/brand/PublicBookAppointmentLink.tsx` và `PublicBookAppointmentLink.test.tsx`
* **Xung đột:** `product` kiểm tra phiên và rẽ hướng người dùng qua `/login?next=...`; `nghiadt13` cho phép truy cập thẳng wizard đặt lịch.
* **Cách giải quyết:** Giữ bản `nghiadt13`. Nút "Đặt lịch khám" dẫn thẳng vào `/portal/appointments/new`, bước 1 của wizard sẽ điều hướng đăng nhập nếu người dùng chưa có tài khoản/phiên, mang lại trải nghiệm mượt mà hơn.

### 6. `frontend/src/features/clinic-services/components/PublicServiceDetail.test.tsx`
* **Xung đột:** `product` mock Supabase Auth client cũ.
* **Cách giải quyết:** Giữ bản `nghiadt13`. Test kiểm tra hiển thị giá dịch vụ và thời lượng thật từ backend, đồng thời xác nhận quyền đặt lịch online theo cờ `onlineBookable`.

### 7. `frontend/src/features/my-account/components/RoleHomeRedirect.tsx` và `RoleHomeRedirect.test.tsx`
* **Xung đột:** `product` gọi `getMyAccount` mock; `nghiadt13` gọi `fetchCurrentAccount()`.
* **Cách giải quyết:** Giữ bản `nghiadt13`. Tại `/workspace`, hệ thống gọi API backend thật `GET /api/v1/me` và chuyển hướng vai trò dựa trên `AccountType` (ADMIN / OWNER -> `/admin`, RECEPTIONIST -> `/reception`, PATIENT -> `/portal`).

### 8. `frontend/src/features/patient-shell/components/PatientPortalHeader.test.tsx`
* **Xung đột:** Test header dựng phiên theo mock cũ.
* **Cách giải quyết:** Giữ bản `nghiadt13`, đồng bộ mock theo cấu trúc dữ liệu trả về của `currentAccountApi`.

---

## 3. Các bước kiểm chứng sau khi giải quyết
1. `mvnw.cmd test-compile`: 445 source files + 104 test classes compile thành công (0 errors).
2. `npx tsc --noEmit`: Kiểm tra toàn bộ kiểu dữ liệu TypeScript của Frontend đạt 0 lỗi.
3. `npm test -- --run`: Toàn bộ 179 test files, 1,260 tests đều PASS 100%.
