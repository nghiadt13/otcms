---
module: Khu cá nhân của Admin (menu tài khoản, đổi mật khẩu, hộp thư thông báo)
route: /admin/account, /admin/notifications (+ trang đổi mật khẩu lần đầu)
ucs: [UC03, UC04, UC05, UC63]
screens: ["88 Account Profile", "89 Update Account Profile", "90 Change Password", "80 Notification List", "81 Notification Detail", "40 First-Login Password Change"]
br: [BR-01, BR-04, BR-06, BR-07, BR-12]
msg: [MSG-04, MSG-05, MSG-07, MSG-08, MSG-14, MSG-50, MSG-60]
data_dependency: Supabase Auth (đổi mật khẩu); thông báo gửi cho Admin do hệ thống tạo
---

# Khu cá nhân của Admin (UC03–UC05, UC63)

Không nằm trong sidebar: vào qua **menu tài khoản ở chân sidebar** (`AdminAccountMenu`) và **chuông ở header** (`NotificationBell`).

## Menu tài khoản (chân sidebar)
| Mục | Hành động |
| --- | --- |
| Hồ sơ tài khoản | Đi `/admin/account` |
| Đổi mật khẩu | Mở Dialog đổi mật khẩu |
| Đăng xuất | Kết thúc phiên, về trang đăng nhập nhân viên |

## Hồ sơ tài khoản (UC03, UC04 — màn 88, 89)
Hiển thị: **tên đăng nhập** (Admin **không có email**, BR-11), vai trò "Quản trị viên", trạng thái, **ảnh đại diện**. Không có dữ liệu y khoa.

| Nút | Hành động |
| --- | --- |
| Đổi ảnh đại diện | Chọn ảnh, xem trước, **Lưu** → `MSG-05` ("Thông tin tài khoản đã được cập nhật.") |
| Gỡ ảnh | Về ảnh mặc định (đề xuất) |
| Đổi mật khẩu | Mở Dialog bên dưới |

- Ảnh không hợp lệ: `MSG-08`; không được đổi mục khác: `MSG-14`; lỗi lưu `MSG-04`.
- Màn 89 "Update Account Profile" ở R3 có chữ "contact details" nhưng UC04 chỉ cho đổi **ảnh đại diện** (Patient mới có thêm thông tin hành chính). Làm theo UC04.

## Đổi mật khẩu (UC05 — màn 90)
| Trường | Quy tắc |
| --- | --- |
| Mật khẩu hiện tại | Bắt buộc (BR-06) |
| Mật khẩu mới | ≥ 8 ký tự, có cả chữ và số (BR-04); ô hiện tiêu chí đạt/chưa |
| Nhập lại mật khẩu mới | Phải trùng |

| Nút | Hành động |
| --- | --- |
| **Đổi mật khẩu** (chính) | Thành công `MSG-07` ("Mật khẩu đã được thay đổi.") |
| Hủy | Đóng |

Lỗi: mật khẩu hiện tại sai hoặc mật khẩu mới không đạt chính sách → `MSG-08` (không nói rõ cái nào sai); lỗi dịch vụ `MSG-04`. Có nút hiện/ẩn mật khẩu (`aria-label`).

## Đổi mật khẩu lần đầu (màn 40)
- Sau lần đăng nhập đầu bằng mật khẩu khởi tạo, **chặn mọi route khác** cho tới khi đổi xong (BR-07). Cờ `mustChangePassword` từ tài khoản.
- Cùng form như trên nhưng **không có nút Hủy**, tiêu đề "Đặt mật khẩu mới", mô tả "Bạn đang dùng mật khẩu khởi tạo". Xong `MSG-07` rồi vào `/admin`.
- Admin do Clinic Owner cấp nên cũng đi qua bước này.

## Hộp thư thông báo (UC63 — màn 80, 81)
**Chuông ở header:** số đếm chưa đọc; bấm mở danh sách rút gọn (5 mới nhất) + liên kết **Xem tất cả** tới `/admin/notifications`.

**Màn 80 — Danh sách:** mỗi dòng có chấm chưa đọc, tiêu đề, thời gian. Bộ lọc: Tất cả / Chưa đọc.
| Nút | Hành động |
| --- | --- |
| Bấm dòng | Mở Sheet chi tiết (màn 81) |
| Đánh dấu tất cả đã đọc | Đề xuất (R3 chỉ có từng thông báo) |

**Màn 81 — Chi tiết (Sheet):** nội dung đầy đủ và bản ghi liên quan (link "Mở"), nếu có.
| Nút | Hiện khi | Hành động |
| --- | --- | --- |
| Đánh dấu đã đọc | Chưa đọc | Thành công `MSG-60` ("Đã đánh dấu thông báo là đã đọc.") |
| Mở bản ghi liên quan | Có liên kết | Đi tới màn tương ứng; nếu bản ghi không còn: `MSG-50`, thông báo vẫn giữ |
| Đóng | Luôn | |

- Mở thông báo đã đọc: giữ trạng thái đã đọc (AF1). Danh sách rỗng: "Bạn chưa có thông báo nào." (AF2).
- Thông báo không thuộc người dùng: `MSG-50`. Một người chỉ đọc thông báo gửi cho mình (BR-01).
- Lỗi tải/cập nhật `MSG-04`.

## Không có
- Xóa thông báo; quên mật khẩu bằng OTP (Admin nhờ Clinic Owner cấp lại qua UC08).

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/me` | Tài khoản hiện tại (kèm `mustChangePassword`) |
| PUT | `/api/v1/me/avatar` | |
| POST | `/api/v1/me/password` | `{currentPassword, newPassword}` |
| GET | `/api/v1/me/notifications?unread=&page=` · `/unread-count` | |
| POST | `/api/v1/me/notifications/{id}/read` | |

## Câu hỏi mở
- Admin có nhận loại thông báo nào (R3 UC63 chỉ nêu chung)? Cần liệt kê để FE đặt icon và link đúng (ví dụ: lịch hẹn cần chuyển bác sĩ).
