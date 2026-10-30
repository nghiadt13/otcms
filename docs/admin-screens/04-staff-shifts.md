---
module: Ca làm việc
route: /admin/staff-shifts
ucs: [UC25, UC26, UC27, UC28]
screens: ["95 Clinic Staff Schedule", "96 Shift Management"]
br: [BR-20, BR-23, BR-24, BR-25]
msg: [MSG-04, MSG-33, MSG-34, MSG-35, MSG-36, MSG-93]
data_dependency: tài khoản + hồ sơ nhân viên (02, 03), phòng (06); kiểm tra lịch hẹn bị ảnh hưởng cần 05
---

# Ca làm việc (UC25–UC28)

Admin xem lịch và xếp, sửa, xóa ca của Lễ tân, Bác sĩ, KTV chẩn đoán hình ảnh, KTV xét nghiệm. Giờ nhận lịch khám của bác sĩ suy ra từ ca của bác sĩ (BR-20). Các nhân viên khác chỉ xem ca của chính họ (BR-23), không thuộc màn này.

**Đã chốt trong R3:** mỗi ca gắn với **một phòng** (BR-24), nên không còn chờ quyết định D7.

## Quy tắc cần nhớ
- Ca có: nhân viên, ngày, giờ bắt đầu, giờ kết thúc, **phòng**.
- Ca của một nhân viên **không chồng giờ**; một phòng khám chỉ có **một bác sĩ mỗi ca** (BR-24).
- Chỉ **xóa** được ca **tương lai**. Ca đã qua chỉ đọc.
- Trước khi sửa/xóa ca của bác sĩ mà đang có lịch hẹn trong ca, từng lịch hẹn phải được chuyển bác sĩ hoặc bệnh nhân hủy, bệnh nhân được báo (BR-25).
- Nhân viên chỉ nhận ca khi tài khoản **Active** và có **hồ sơ** (UC26 điều kiện).

## Màn 95 — Lịch ca (xem)
`PageHeader`: "Ca làm việc"; nút chính **Thêm ca**.

**Điều khiển**
| Điều khiển | Hành vi |
| --- | --- |
| Chọn nhân viên (có ô tìm) hoặc "Tất cả" | Lọc lịch theo người |
| Lọc Vai trò, Phòng | |
| Chuyển Tuần / Ngày; nút ‹ Hôm nay › | Điều hướng kỳ |
| Chế độ xem: **Lưới tuần** (hàng = nhân viên, cột = ngày) hoặc Danh sách | Mặc định lưới tuần |

Mỗi ô ca hiện: giờ, phòng. Màu chữ + chữ vai trò (không chỉ dùng màu). Nhân viên/phòng không còn ở trạng thái hợp lệ: `MSG-33` ("Không thể truy cập ca làm việc này").

## Màn 96 — Quản lý ca
| Nút | Vị trí | Hiện khi | Hành động |
| --- | --- | --- | --- |
| **Thêm ca** | Header; hoặc bấm ô trống trên lưới (điền sẵn nhân viên + ngày) | Luôn | Mở Dialog thêm |
| Sửa | Trên ô ca / dòng ca | Ca tương lai | Mở Dialog sửa |
| Xóa ca | Trong Dialog sửa hoặc menu ô ca | Ca tương lai | `AlertDialog` xác nhận |
| Xem | Trên ô ca | Ca đã qua | Chỉ xem chi tiết |

**Dialog thêm / sửa ca**
| Trường | Bắt buộc | Quy tắc |
| --- | --- | --- |
| Nhân viên | Có | Chỉ Active + có hồ sơ. Khi sửa: chỉ đọc |
| Ngày | Có | Không trước hôm nay khi thêm |
| Giờ bắt đầu · Giờ kết thúc | Có | Kết thúc sau bắt đầu |
| Phòng | Có | Chỉ phòng **Sẵn sàng**; gợi ý theo vai trò: bác sĩ → phòng khám, KTV CĐHA → phòng chẩn đoán hình ảnh, KTV xét nghiệm → phòng xét nghiệm |

| Nút | Hành động |
| --- | --- |
| **Lưu ca** (chính) | Gửi; thành công `MSG-93` ("Đã cập nhật ca làm việc.") |
| Hủy | Đóng |

**Khi sửa ca của bác sĩ**: trên đầu Dialog hiện khối "Lịch hẹn trong ca này" (số lượng + danh sách ngắn) lấy từ BR-25 để Admin thấy tác động trước khi lưu.

**Lỗi**
| MSG | Khi | Hiển thị |
| --- | --- | --- |
| MSG-34 | Ngày, giờ hoặc phân công không hợp lệ | Toast lỗi, giữ form |
| MSG-35 | Trùng ca của nhân viên, phòng khám đã có bác sĩ trong ca đó, hoặc nhân viên không nhận được ca | Toast lỗi; Admin đổi giờ/phòng/nhân viên |
| MSG-36 | Sửa/xóa ca bác sĩ còn lịch hẹn bị ảnh hưởng | Toast lỗi + nút **Chuyển lịch hẹn** đi `/admin/appointments` lọc theo bác sĩ và ca ([05](05-appointments.md)); không lưu |
| MSG-33 | Ca không còn / ngoài quyền / ca đã qua khi xóa | Toast lỗi; tải lại lưới |
| MSG-04 | Lỗi dịch vụ | Toast lỗi |

**AlertDialog xóa ca**: "Ca {giờ} ngày {ngày} của {nhân viên} sẽ bị xóa, giờ nhận lịch của bác sĩ trong ca cũng mất." → **Xóa ca** (destructive) / Hủy. Thành công `MSG-93`.

## Không có
- Ca lặp (lặp hằng tuần), sao chép tuần, nghỉ phép, tính công: không nằm trong R3 (`AGENTS.md` cũng loại nghỉ phép/lương).
- Nút hủy lịch hẹn: Admin chỉ chuyển bác sĩ (BR-22).

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/shifts?staffId=&role=&roomId=&from=&to=` | |
| POST | `/api/v1/admin/shifts` | `{staffId, date, startTime, endTime, roomId}` |
| PUT | `/api/v1/admin/shifts/{id}` | |
| DELETE | `/api/v1/admin/shifts/{id}` | `409` + `code: MSG-36` nếu còn lịch hẹn |
| GET | `/api/v1/admin/shifts/{id}/affected-appointments` | Cho khối cảnh báo lúc sửa |

## Câu hỏi mở
- **Lễ tân và kỹ thuật viên có "phòng" thế nào?** BR-24 nói mọi ca có phòng, nhưng Lễ tân làm ở quầy tiếp nhận (không có loại phòng này). Cần chốt: thêm loại "quầy" hay cho phép ca Lễ tân không phòng.
- Giới hạn độ dài một ca, bội số 15 phút?
- Sửa ca đã bắt đầu trong ngày hôm nay có được không? R3 chỉ cấm *xóa* ca không phải tương lai.


## Live backend integration (KAN-12 / KAN-13)

The route now uses the live API contract documented in [admin-backend-integration.md](../admin-backend-integration.md). The design above describes the earlier mock/SRS target; fields missing from the backend contract are not silently stored in a mock or sent as unsupported fields. See that document for the exact supported scope and acceptance checks.
