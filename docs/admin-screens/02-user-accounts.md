---
module: Tài khoản người dùng
route: /admin/accounts
ucs: [UC07, UC08, UC09]
screens: ["68 Account List", "69 Create Account", "70 Account Detail", "71 Edit Account"]
br: [BR-02, BR-04, BR-07, BR-08, BR-10, BR-11]
msg: [MSG-04, MSG-11, MSG-12, MSG-13, MSG-14, MSG-15, MSG-16, MSG-42, MSG-54]
data_dependency: không (làm được ngay, cần Supabase Auth ở BE)
---

# Tài khoản người dùng (UC07–UC09)

Admin tạo tài khoản nhân viên, bật/tắt trạng thái và cấp lại mật khẩu cho nhân viên, tra cứu tài khoản **Bệnh nhân** và **nhân viên**. Không thấy tài khoản Administrator hay Clinic Owner (BR-08).

## Quy tắc cần nhớ
- Nhân viên đăng nhập bằng **tên đăng nhập + mật khẩu**, **không có email** (BR-11). Bệnh nhân dùng email.
- Admin tạo 4 vai trò: **Lễ tân, Bác sĩ chỉnh hình, KTV chẩn đoán hình ảnh, KTV xét nghiệm**. Vai trò cố định, **không đổi được sau khi tạo** (BR-08).
- Mật khẩu khởi tạo **chỉ hiện một lần** cho Admin, người này đưa trực tiếp cho nhân viên; nhân viên bắt buộc đổi ở lần đăng nhập đầu (BR-07).
- Đổi trạng thái chỉ chặn/mở quyền truy cập, **không xóa** tài khoản hay dữ liệu liên quan (BR-10). Không có nút xóa tài khoản.
- Mật khẩu: ≥ 8 ký tự, có cả chữ và số (BR-04).
- Quên mật khẩu: nhân viên nhờ Admin cấp lại. Bệnh nhân tự khôi phục bằng OTP email, nên **không có** "Cấp lại mật khẩu" cho tài khoản bệnh nhân.

## Màn 68 — Danh sách tài khoản
`PageHeader`: "Tài khoản người dùng"; nút chính **Tạo tài khoản**.

**Bộ lọc:** ô tìm kiếm (tên đăng nhập / email / họ tên); chọn Loại tài khoản (Tất cả, Bệnh nhân, Lễ tân, Bác sĩ chỉnh hình, KTV chẩn đoán hình ảnh, KTV xét nghiệm); chọn Trạng thái (Tất cả, Đang hoạt động, Đã vô hiệu hóa). Tiêu chí sai → `MSG-54`.

**Cột bảng**
| Cột | Ghi chú |
| --- | --- |
| Định danh | Tên đăng nhập (nhân viên) hoặc email (bệnh nhân) |
| Họ tên | Từ hồ sơ nhân viên / hồ sơ bệnh nhân; "Chưa có hồ sơ" nếu nhân viên chưa có |
| Loại tài khoản | Chữ vai trò |
| Trạng thái | Badge: Đang hoạt động / Đã vô hiệu hóa |
| Ngày tạo | `dd/MM/yyyy` |
| (thao tác) | Nút "Xem" và menu ⋯ |

Phân trang phía server.

**Nút trên dòng**
| Nút | Hiện khi | Hành động |
| --- | --- | --- |
| Xem | Luôn | Mở màn 70 (Sheet chi tiết) |
| Vô hiệu hóa (menu ⋯) | `allowedActions` có `deactivate` | Mở xác nhận (xem dưới) |
| Kích hoạt (menu ⋯) | `allowedActions` có `activate` | Mở xác nhận |

## Màn 69 — Tạo tài khoản (Dialog)
Chỉ tạo tài khoản **nhân viên**. Không có lựa chọn Bệnh nhân hay Administrator.

| Trường | Bắt buộc | Quy tắc |
| --- | --- | --- |
| Vai trò | Có | Chọn 1 trong 4 vai trò nhân viên |
| Tên đăng nhập | Có | Duy nhất (BR-11). Gợi ý FE: chữ thường, số, `.`/`_`, 4–30 ký tự (chưa có trong R3, chờ chốt) |
| Mật khẩu khởi tạo | Có | Theo BR-04. Nút phụ **Tạo ngẫu nhiên** (đề xuất, tiện cho Admin) |

| Nút | Hành động |
| --- | --- |
| **Tạo tài khoản** (chính) | Gửi. Đang gửi: "Đang tạo..." |
| Hủy | Đóng Dialog |

**Sau khi tạo thành công** (`MSG-11`: "Đã tạo tài khoản {accountRole} với tên đăng nhập {username}. Mật khẩu khởi tạo chỉ hiển thị một lần."): chuyển Dialog sang bước kết quả, hiện tên đăng nhập và mật khẩu khởi tạo, nút **Sao chép mật khẩu** và nút **Xong**. Đóng Dialog là mất mật khẩu; ghi rõ cảnh báo này. Gợi ý nhắc Admin: "Đưa mật khẩu trực tiếp cho nhân viên".

**Lỗi**
| MSG | Khi | Hiển thị |
| --- | --- | --- |
| MSG-12 | Thông tin không hợp lệ | Đỏ dưới ô sai |
| MSG-13 | Tên đăng nhập đã dùng | Toast lỗi; giữ form, Admin đổi tên rồi gửi lại |
| MSG-14 | Không có quyền tạo loại này | Toast lỗi, đóng form |
| MSG-16 | Tạo thất bại / kết quả không chắc | Toast lỗi; bảo Admin tải lại danh sách trước khi thử lại |

## Màn 70 — Chi tiết tài khoản (Sheet bên phải)
Hiển thị: định danh, loại tài khoản, trạng thái, ngày tạo; nếu là nhân viên có hồ sơ: họ tên, vai trò, liên hệ công việc (chỉ đọc, BR-09). Với bệnh nhân: họ tên, email. **Không** hiển thị dữ liệu y khoa (BR-12).

| Nút | Hiện khi | Hành động |
| --- | --- | --- |
| Vô hiệu hóa | Tài khoản Active | `AlertDialog`: "Người dùng sẽ không đăng nhập được. Dữ liệu và hồ sơ liên quan được giữ nguyên." → **Vô hiệu hóa** / Hủy. Thành công `MSG-15` ("Trạng thái tài khoản đã được cập nhật thành {status}.") |
| Kích hoạt | Tài khoản Inactive | `AlertDialog` ngắn → thành công `MSG-15` |
| Cấp lại mật khẩu | Chỉ nhân viên | `AlertDialog`: "Mật khẩu hiện tại sẽ bị thay. Nhân viên phải đổi mật khẩu ở lần đăng nhập kế." → hiện mật khẩu mới **một lần** (cùng bước kết quả như lúc tạo, có Sao chép) |
| Tạo hồ sơ nhân viên | Nhân viên chưa có hồ sơ | Điều hướng `/admin/staff-profiles` với tài khoản này đã chọn ([03](03-staff-profiles.md)) |
| Đóng | Luôn | Đóng Sheet |

Lỗi khi đổi trạng thái: `MSG-14` (không đủ quyền hoặc tài khoản không còn), `MSG-42` (trạng thái hiện tại không cho phép thao tác, ví dụ kích hoạt tài khoản đang Active; tải lại dòng), `MSG-16` (lỗi cập nhật).

## Màn 71 — Sửa tài khoản
Trong R3 phần "sửa" của Admin chỉ gồm **đổi trạng thái** và **cấp lại mật khẩu** (UC08). Không có form sửa họ tên, email hay vai trò; hai thao tác trên nằm sẵn ở màn 70, không cần màn riêng.

## Ghi chú về tài khoản đã code
Bản FE hiện có dùng email, "Chờ kích hoạt", "Đổi vai trò", lý do khóa: **bỏ hết** (xem [audit](00-sidebar-audit-vs-r3.md) mục 3). Nhãn trạng thái dùng "Đang hoạt động / Đã vô hiệu hóa" thay "Tạm khóa" để khỏi nhầm với khóa 15 phút do nhập sai mật khẩu (BR-02, tự động, không phải thao tác của Admin).

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/accounts?q=&role=&status=&page=` | Chỉ trả bệnh nhân và 4 loại nhân viên |
| POST | `/api/v1/admin/accounts` | `{role, username, initialPassword}`; trả `{id, username, initialPassword}` một lần |
| GET | `/api/v1/admin/accounts/{id}` | Kèm `allowedActions` |
| POST | `/api/v1/admin/accounts/{id}/activate` · `/deactivate` | |
| POST | `/api/v1/admin/accounts/{id}/reissue-password` | Chỉ nhân viên; trả mật khẩu mới một lần |

## Câu hỏi mở
- Quy tắc đặt tên đăng nhập (độ dài, ký tự) chưa có trong R3.
- Có nhập họ tên khi tạo tài khoản không? R3 chỉ yêu cầu vai trò, tên đăng nhập, mật khẩu; họ tên đi vào hồ sơ nhân viên (UC17).
