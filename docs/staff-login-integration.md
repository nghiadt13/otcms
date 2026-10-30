# Đăng nhập Admin, Chủ phòng khám và nhân viên

Màn `/staff-login` có hai lựa chọn. Admin và Chủ phòng khám dùng username tại `POST /api/auth/staff/administrator/login`. Nhân viên khác dùng username tại `POST /api/auth/staff/login`; endpoint này vẫn nhận trường `email` để tài khoản cũ tiếp tục đăng nhập. Backend kiểm tra mạng phòng khám và mật khẩu cho cả hai.

Phản hồi đăng nhập có `mustChangePassword`. Khi cờ này là `true`, frontend chuyển tới `/first-login-password`, gọi `POST /api/auth/change-password` với access token, CSRF và mật khẩu hiện tại/mới. Backend thu hồi mọi phiên sau khi đổi; frontend bỏ phiên cũ và yêu cầu đăng nhập lại. Mật khẩu không được lưu trong localStorage hoặc log.

Luồng bệnh nhân vẫn dùng email tại `/login`. Backend hiện được chạy chẩn đoán với Flyway tắt để kiểm tra kết nối/schema mà không tự áp dụng migration trên database thật. Việc kết nối API và 93 test frontend, 11 test backend đã đạt; chưa kiểm thử đăng nhập bằng mật khẩu của người dùng thật.
