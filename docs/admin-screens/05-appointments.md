---
module: Lịch hẹn (mục mới)
route: /admin/appointments
ucs: [UC20, UC22, UC24]
screens: ["93 Doctor Availability Overview", "94 Appointment Reassignment"]
br: [BR-16, BR-19, BR-20, BR-22, BR-25]
msg: [MSG-04, MSG-24, MSG-25, MSG-27, MSG-29, MSG-32, MSG-89, MSG-96]
data_dependency: đặt lịch của bệnh nhân (chưa có BE) → FE dùng dữ liệu giả
---

# Lịch hẹn (UC20, UC22, UC24)

Mục **mới** trong sidebar. Admin xem mọi lịch hẹn, xem giờ trống của bác sĩ và **chuyển lịch hẹn sang bác sĩ khác** khi bác sĩ vắng hoặc ca đổi. Đây là lối thoát cho `MSG-36` ở [03](03-staff-profiles.md) và [04](04-staff-shifts.md).

## Quy tắc cần nhớ
- **Admin không hủy lịch hẹn và không đổi giờ** (BR-22). Chỉ **bệnh nhân** hủy, trước khi check-in. Admin chỉ chuyển sang bác sĩ khác **cùng giờ**.
- Chỉ chuyển được lịch **Booked**, chưa check-in.
- Giờ trống tính từ ca của bác sĩ, độ dài khung giờ cấu hình (≥ 10 phút) và các lịch đã đặt (BR-20).
- Trạng thái lịch hẹn: Booked, Checked In, Cancelled, No-show.

## Bố cục: hai tab
`PageHeader`: "Lịch hẹn". Tab **Danh sách lịch hẹn** · Tab **Giờ trống của bác sĩ**. Không có nút chính ở header (màn không tạo mới).

## Tab 1 — Danh sách lịch hẹn (UC24, màn 94)
**Bộ lọc:** khoảng ngày (mặc định hôm nay → +7 ngày); Bác sĩ; Trạng thái; tìm bệnh nhân (tên, SĐT). Có thể mở sẵn với `?doctorId=&date=` từ màn ca/hồ sơ.

| Cột | Ghi chú |
| --- | --- |
| Ngày giờ | |
| Bệnh nhân | Họ tên (kèm "đặt cho người phụ thuộc" nếu có) |
| Dịch vụ | |
| Bác sĩ | "Chưa chỉ định" nếu bệnh nhân không chọn bác sĩ (BR-21) |
| Trạng thái | Badge 4 trạng thái |
| (thao tác) | |

| Nút dòng | Hiện khi | Hành động |
| --- | --- | --- |
| Xem | Luôn | Sheet chi tiết (ngày giờ, bệnh nhân, dịch vụ, bác sĩ, trạng thái, phí khám đã thanh toán hay chưa) |
| **Chuyển bác sĩ** | `allowedActions` có `reassign` (Booked, chưa check-in) | Mở Dialog chuyển bác sĩ |

### Dialog "Chuyển bác sĩ" (UC22)
1. Hiện thông tin lịch hẹn: bệnh nhân, ngày giờ (cố định, không sửa), bác sĩ hiện tại.
2. Danh sách **bác sĩ đủ điều kiện đang có ca cùng giờ** (BR-20, BR-25), chọn một (radio).
3. Nút **Chuyển lịch hẹn** (chính) / Hủy.

Thành công: toast `MSG-27` ("Đã chuyển lịch hẹn sang bác sĩ {doctor}. Bệnh nhân sẽ nhận được thông báo.") và hệ thống gửi email cho bệnh nhân (`MSG-96`); dòng cập nhật tại chỗ.

**Không có bác sĩ thay thế (AF1):** danh sách rỗng, hiện thông báo trong Dialog: "Không có bác sĩ nào có ca vào giờ này." Lịch giữ **Booked**; hệ thống báo bệnh nhân bằng email và trong hệ thống rằng bác sĩ không thể khám và họ có thể hủy (`MSG-89`). FE hiện nút **Báo cho bệnh nhân** để kích hoạt thông báo này (diễn giải của FE, xem câu hỏi mở). Dialog nhắc "Admin không thể hủy lịch hẹn".

**Lỗi**
| MSG | Khi | Hiển thị |
| --- | --- | --- |
| MSG-29 | Lịch hẹn không còn / ngoài quyền | Toast lỗi, đóng Dialog, tải lại |
| MSG-32 | Lịch không còn Booked | Toast lỗi, đóng Dialog; tải lại dòng |
| MSG-24 | Bác sĩ vừa chọn không còn khả dụng | Toast lỗi; Admin chọn người khác |
| MSG-04 | Lỗi dịch vụ | Toast lỗi |

## Tab 2 — Giờ trống của bác sĩ (UC20, màn 93)
| Điều khiển | Hành vi |
| --- | --- |
| Chọn bác sĩ (bắt buộc) | Chỉ bác sĩ đủ điều kiện nhận đặt lịch |
| Chọn ngày hoặc tuần | |
| Nút **Xem giờ trống** | Gọi API |

Kết quả là lưới các khung giờ: trống / đã đặt. **Chỉ đọc**, không đặt lịch từ đây.
- Bác sĩ không đủ điều kiện: `MSG-24`.
- Kỳ không còn giờ trống: toast cảnh báo `MSG-25` ("Không có giờ hẹn trống trong khoảng thời gian đã chọn. Vui lòng chọn thời gian khác.") kèm trạng thái trống.

## Không có
- Hủy lịch hẹn, đổi giờ, đặt lịch mới, check-in (việc của bệnh nhân / Lễ tân).

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/appointments?from=&to=&doctorId=&status=&q=&page=` | |
| GET | `/api/v1/admin/appointments/{id}/eligible-doctors` | Bác sĩ có ca cùng giờ |
| POST | `/api/v1/admin/appointments/{id}/reassign` | `{doctorId}` |
| POST | `/api/v1/admin/appointments/{id}/notify-no-replacement` | Cho AF1 (đề xuất) |
| GET | `/api/v1/admin/doctors/{id}/availability?from=&to=` | |

## Câu hỏi mở
- UC22 AF1: việc báo bệnh nhân khi không có bác sĩ thay do hệ thống tự làm hay do Admin bấm nút? R3 viết "the system keeps… and notifies"; chưa nói có thao tác Admin.
- Admin có xem được lịch hẹn của mọi bác sĩ hay chỉ lịch bị ảnh hưởng? R3 UC24: xem lịch "trong phạm vi" của mình; hiểu là tất cả (Admin là người điều phối).
