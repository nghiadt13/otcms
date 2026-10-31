# Đặc tả màn hình khu Admin (cho Frontend)

Mỗi file mô tả một mục sidebar: màn hình, bảng/cột, form, **từng nút** (hiện khi nào, bấm ra gì, thông báo nào), trạng thái và những nút **không được có**. Nguồn nghiệp vụ: Report 3 trên Drive (bản 20/10/2026, UC01–UC78). Đối chiếu sidebar: [00-sidebar-audit-vs-r3.md](00-sidebar-audit-vs-r3.md).

Backend chưa có. FE dựng trước với lớp API giả cùng chữ ký REST dự kiến (bảng "API dự kiến" trong mỗi file); khi BE xong chỉ thay lớp gọi API.

## Chỉ mục

| File | Mục sidebar | Route | UC | Màn R3 |
| --- | --- | --- | --- | --- |
| [01-dashboard.md](01-dashboard.md) | Bảng điều khiển | `/admin` | UC77 | 67 |
| [02-user-accounts.md](02-user-accounts.md) | Tài khoản người dùng | `/admin/accounts` | UC07–09 | 68–71 |
| [03-staff-profiles.md](03-staff-profiles.md) | Hồ sơ nhân viên | `/admin/staff-profiles` | UC16–19 | 91–92 |
| [04-staff-shifts.md](04-staff-shifts.md) | Ca làm việc | `/admin/staff-shifts` | UC25–28 | 95–96 |
| [05-appointments.md](05-appointments.md) | Lịch hẹn (mới) | `/admin/appointments` | UC20, 22, 24 | 93–94 |
| [06-rooms.md](06-rooms.md) | Phòng khám | `/admin/rooms` | UC64–67 | 76–79 |
| [07-services-and-prices.md](07-services-and-prices.md) | Dịch vụ và giá | `/admin/services` | UC68–71 | 72–75, 97 |
| [08-patient-reviews.md](08-patient-reviews.md) | Đánh giá bệnh nhân | `/admin/reviews` | UC61–62 | 99–100 |
| [09-website-content.md](09-website-content.md) | Nội dung website | `/admin/website-content` | UC72–73 | 98 |
| [10-announcements.md](10-announcements.md) | Thông báo phòng khám | `/admin/announcements` | UC74 | 82–85 |
| [11-payment-history.md](11-payment-history.md) | Lịch sử thanh toán | `/admin/payments` | UC56 | 101 |
| [12-unpaid-items.md](12-unpaid-items.md) | Khoản chưa thanh toán | `/admin/unpaid-items` | UC57 | 102 |
| [13-reports.md](13-reports.md) | Báo cáo | `/admin/reports` | UC78 | 103 |
| [14-activity-log.md](14-activity-log.md) | Nhật ký hoạt động | `/admin/activity-logs` | UC76 | 86–87 |
| [15-personal-area.md](15-personal-area.md) | Menu tài khoản + chuông | `/admin/account`, `/admin/notifications` | UC03–05, 63 | 40, 80–81, 88–90 |

"Màn R3" là số thứ tự trong ma trận quyền R3 mục 1.4.3 (Screen Authorization), cột #.

## Quy ước chung cho mọi màn

### Quyền và phạm vi
- Chỉ vai trò **Administrator** vào khu `/admin`. Admin **không** thấy, tạo hay quản lý tài khoản Administrator/Clinic Owner (BR-08). Clinic Owner có khu riêng, ngoài phạm vi các file này.
- Ẩn nút ở FE chỉ để gọn giao diện; backend kiểm quyền từng thao tác (BR-01). FE phải xử lý lỗi từ chối quyền như một trạng thái bình thường (`MSG-50` hoặc `MSG-14`).
- **Nên để server quyết định nút nào hiện**: mỗi bản ghi trả `allowedActions: string[]` (ví dụ `["deactivate","reissuePassword"]`); FE chỉ vẽ nút theo mảng này thay vì tự tính trạng thái. Việc này tránh lệch luật khi R3 còn đổi.

### Bố cục và component (theo `docs/ui-guidelines.md`)
- Màn chuẩn: `PageHeader` (tiêu đề, một câu mô tả, nút chính bên phải) → thanh lọc → bảng/nội dung. Thêm/sửa dùng `Dialog` hoặc `Sheet`; thao tác không hoàn tác dùng `AlertDialog`.
- **Một nút `default` (màu chính) mỗi màn**; nút khác `outline`/`ghost`. Nhãn nút là động từ, câu ngắn, chỉ viết hoa chữ đầu.
- Nút nguy hiểm (Vô hiệu hóa, Gỡ hồ sơ, Ngừng cung cấp, Ẩn đánh giá…) dùng `destructive` và luôn có `AlertDialog` giải thích hậu quả.
- Trạng thái luôn có chữ đi kèm màu (badge).

### Bốn trạng thái bắt buộc
| Trạng thái | Cách hiển thị |
| --- | --- |
| Đang tải | `Skeleton` đúng hình bảng/form |
| Trống | Một dòng giải thích trong khung (R3 gọi là "empty result"); kèm gợi ý hành động nếu có |
| Lỗi tải | Chữ `destructive` + nút "Thử lại"; hiện `MSG-04` |
| Đang gửi | Khóa nút, đổi nhãn ("Đang lưu...") |

### Thông báo (MSG)
- R3 phân hai kiểu: **Toast** (thành công / lỗi / cảnh báo) và **lỗi đỏ dưới ô nhập** (`In red, under the text box`). Mỗi file ghi rõ MSG nào thuộc kiểu nào.
- Lỗi từ backend trả dạng `ProblemDetail` có trường `code` = mã MSG (ví dụ `MSG-13`). FE tra nội dung từ một bảng MSG duy nhất; chỗ có `{tham số}` thì thay giá trị.
- `MSG-04` là lỗi chung khi dịch vụ lỗi. Mọi màn dùng được.

### Bộ lọc, tìm kiếm
- Tiêu chí tìm kiếm/lọc sai → `MSG-54` (đỏ dưới ô). Không có kết quả → trạng thái trống, không báo lỗi.

### Tiền, ngày giờ
- Tiền VND, định dạng `1.250.000 ₫`. Ngày `dd/MM/yyyy`, giờ 24h `HH:mm`, múi giờ Asia/Ho_Chi_Minh.

### Những thứ Admin KHÔNG làm (đừng dựng nút)
- Đổi vai trò tài khoản; xóa tài khoản; xem/quản lý tài khoản Admin/Owner (BR-08, BR-10).
- **Hủy lịch hẹn** hay đổi giờ lịch hẹn (BR-22, chỉ bệnh nhân hủy).
- Xem nội dung y khoa, tiền sử, kết quả khám (BR-12, BR-62).
- Hoàn tiền, sửa hay hủy khoản thanh toán, thu tiền, in phiếu thu (BR-52, BR-53; Lễ tân thu và in).
- Xóa phòng; xóa cứng dịch vụ, hồ sơ nhân viên, đánh giá.

## Giai đoạn FE-trước với dữ liệu giả
- Đặt lớp API ở `src/features/<feature>/api/`, trả đúng hình DTO ghi trong file; khi nối BE mock ở ranh giới `apiRequest` (theo `AGENTS.md`).
- Dữ liệu giả phải đủ ca: rỗng, nhiều trang, mọi trạng thái, và mỗi lỗi MSG chính để thử giao diện.
