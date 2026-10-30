# Nối frontend/backend: quên và đặt lại mật khẩu

## Luồng đã nối

1. `/forgot-password`: nhập email; frontend lấy CSRF từ `GET /api/auth/csrf`, rồi gọi `POST /api/auth/patient/forgot-password` với `{ email }`, cookie và header `X-CSRF-TOKEN`.
2. Backend trả `202` và thông báo chung, bất kể email có tài khoản đủ điều kiện hay không. Không được hiểu thông báo này là bằng chứng email đã được gửi thành công.
3. Với tài khoản đủ điều kiện và SMTP hoạt động, email chứa link frontend `/reset-password?portal=patient&token=...`. Backend lưu hash token, token có hiệu lực 15 phút và chỉ dùng một lần.
4. Frontend nhận token, bỏ token khỏi thanh địa chỉ sau khi mở trang, kiểm tra mật khẩu và xác nhận, rồi gửi `{ token, newPassword }` đến `POST /api/auth/patient/reset-password` cùng CSRF/cookie. Không gửi confirmPassword.
5. Chỉ khi nhận `204` mới hiện thành công. Backend đổi password hash, thu hồi refresh sessions. Frontend bỏ phiên và thông tin tài khoản đang nhớ, hiển thị liên kết đăng nhập.

Backend có endpoint tương ứng `/api/auth/staff/...`. Link email cho nhân viên mang `portal=staff`; frontend chuyển đúng endpoint và link đăng nhập `/staff-login`. Backend vẫn kiểm tra mạng phòng khám/VPN; query `portal` không cấp quyền truy cập. Không thay đổi chính sách hiển thị link tự khôi phục ở trang đăng nhập nhân viên trong đợt này.

## Cấu hình chạy thực tế

Frontend dùng `NEXT_PUBLIC_API_BASE_URL`, ví dụ `http://localhost:8080`.

Backend đọc qua cơ chế cấu hình môi trường hiện có:

```dotenv
APP_AUTH_PASSWORD_RESET_URL=http://localhost:3000/reset-password
APP_AUTH_PATIENT_ORIGINS=http://localhost:3000
APP_AUTH_STAFF_ORIGINS=http://localhost:3000
APP_AUTH_COOKIE_SECURE=false
APP_EMAIL_ENABLED=true
```

- `APP_AUTH_PASSWORD_RESET_URL` phải trỏ tới địa chỉ frontend người dùng truy cập được; nếu frontend dùng cổng 3002 thì đổi cả reset URL và origin sang cổng đó.
- SMTP cần `APP_EMAIL_HOST`, `APP_EMAIL_PORT`, `APP_EMAIL_USERNAME`, `APP_EMAIL_PASSWORD`, `APP_EMAIL_FROM` hợp lệ. Mật khẩu SMTP chỉ cấu hình bên ngoài Git; không đưa vào frontend.
- Local dùng cùng hostname cho frontend và backend (ví dụ đều `localhost`), tránh trộn `localhost` với `127.0.0.1` làm cookie SameSite bị chặn. HTTPS môi trường thật dùng `APP_AUTH_COOKIE_SECURE=true` và origin cụ thể.
- URL khôi phục từ cấu hình backend, không lấy từ request của trình duyệt. Chỉ chấp nhận HTTPS hoặc HTTP loopback; không nhận userinfo, query hoặc fragment trong URL cấu hình.
- Không bật hoặc thử gửi email tới người dùng thật trong kiểm thử tự động.

## Kiểm tra và lỗi

- API khôi phục công khai nhưng vẫn yêu cầu CSRF. Frontend không dùng client bắt buộc đăng nhập và không gọi refresh để mở trang quên mật khẩu.
- CSRF cũ: lấy lại token và thử một lần; không tự thử lại lỗi mạng vì request trước có thể đã được xử lý.
- Token sai/hết hạn/đã dùng: yêu cầu link mới.
- Mật khẩu trùng mật khẩu cũ: giữ form để nhập mật khẩu khác.
- Lỗi mạng/5xx: không báo gửi hoặc đổi thành công.
- Rate limit và mạng nhân viên không hợp lệ: thông báo riêng, không lộ tài khoản.

## Chênh lệch yêu cầu cần nhóm thống nhất

Backend hiện có `PasswordPolicy` và DTO yêu cầu **12–64 UTF-16 code units, tối đa 72 byte UTF-8**, không bắt buộc tổ hợp chữ/số. Frontend dùng chung cho đăng ký/đổi mật khẩu vẫn có quy tắc 8 ký tự và chữ/số. Đợt tích hợp này chỉ làm form reset tương thích hợp đồng backend đang chạy; không đổi chính sách toàn hệ thống hoặc âm thầm sửa BR-04.

## Kiểm thử

- Kết quả xác nhận: 84 test frontend trong 11 file và 20 test backend đều đạt; ESLint, production build và `git diff --check` đạt.
- Kiểm tra Chrome với bản production: gửi yêu cầu khôi phục, reset thành công sau khi bỏ token khỏi URL, token hết hạn trên portal staff, chặn thiếu token và mobile 390px không tràn ngang đều đạt; không có lỗi JavaScript trên trang. Phản hồi HTTP được giả lập tại biên trình duyệt, không phải kiểm thử đầu cuối với SMTP/database thật.
- Frontend: toàn bộ test `src/features/auth` và `src/lib/auth`, bao gồm submit form qua lớp API thật với fetch giả tại biên HTTP, payload/CSRF/204, lỗi token, reuse, network, giới hạn mật khẩu.
- Backend: `PasswordRecoveryFlowTest`, `T008PasswordRecoveryServiceTest`, `T010PasswordRecoveryControllerTest`, `T009SmtpEmailDeliveryTest`, `SessionCsrfWebMvcTest`.
- Flow test chạy controller + recovery service + BCrypt thực, giả lập repository và email; xác nhận password hash thay đổi, thu hồi sessions, từ chối token đã dùng. MVC security test đi qua CSRF filter thực. SMTP test kiểm tra nội dung email bằng mail sender giả.
- Đây chưa phải bằng chứng gửi/nhận email thật hoặc kiểm thử với database triển khai.

## Action Record

- Yêu cầu người dùng: nối quên mật khẩu và đặt lại mật khẩu frontend/backend.
- Chuẩn bị: lưu refactor imaging tại `58b041b`; tích hợp `origin/nghiadt13` tại `44d62c2` để dùng backend và lớp đăng nhập hiện có.
- Phạm vi thay đổi: recovery API/forms/schema/tests frontend; tạo link SMTP theo portal và cấu hình URL backend; tài liệu vận hành. Không đổi schema database, không thao tác dữ liệu bệnh nhân, không gửi email thật.
- Kiểm tra: frontend lint/build và nhóm test xác thực; backend Maven nhóm test nêu trên. Kết quả cuối được báo trong phiên làm việc.
