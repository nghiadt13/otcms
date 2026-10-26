---
module: Phòng khám
route: /admin/rooms
ucs: [UC64, UC65, UC66, UC67]
screens: ["76 Rooms List", "77 Create Room", "78 Room Detail", "79 Room Service"]
br: [BR-57]
msg: [MSG-04, MSG-50, MSG-54, MSG-61, MSG-62, MSG-63, MSG-64, MSG-98]
data_dependency: danh mục dịch vụ (07); trạng thái "đang dùng" cần ca/hàng đợi (xử lý ở BE)
---

# Phòng khám (UC64–UC67)

Admin thêm phòng, sửa mô tả và dịch vụ hỗ trợ, đặt trạng thái Sẵn sàng / Tạm ngưng.

## Quy tắc cần nhớ
- **3 loại phòng:** Phòng khám (examination), Chẩn đoán hình ảnh (imaging), Xét nghiệm (laboratory). **Không có phòng thủ thuật** (R3 loại thủ thuật khỏi phạm vi). Bản FE hiện có `procedure` cần bỏ và thêm `laboratory`.
- Dịch vụ hỗ trợ lấy từ **danh mục dịch vụ** ([07](07-services-and-prices.md)), không phải danh sách cố định. Phòng phải **khớp** dịch vụ (BR-57): phòng khám ↔ dịch vụ khám, phòng chẩn đoán hình ảnh ↔ dịch vụ chẩn đoán hình ảnh, phòng xét nghiệm ↔ dịch vụ xét nghiệm.
- Đổi trạng thái **không được xung đột** với việc đang dùng (phòng đang có bệnh nhân hoặc đã phân công) (BR-57).
- Không có nút xóa phòng.

## Màn 76 — Danh sách phòng
`PageHeader`: "Phòng khám"; nút chính **Thêm phòng**.

**Bộ lọc:** tìm kiếm (tên/mã); Loại phòng; Trạng thái; Dịch vụ hỗ trợ. Tiêu chí sai → `MSG-54`.

| Cột | Ghi chú |
| --- | --- |
| Tên / mã phòng | |
| Loại phòng | Chữ loại |
| Dịch vụ hỗ trợ | Chip, tối đa 3 rồi "+N" |
| Trạng thái | `Switch` + chữ "Sẵn sàng" / "Tạm ngưng" |
| (thao tác) | Xem, Sửa |

| Nút dòng | Hiện khi | Hành động |
| --- | --- | --- |
| Xem | Luôn | Sheet chi tiết (màn 78) |
| Sửa | Luôn | Dialog sửa (màn 79 nằm trong đây) |
| `Switch` trạng thái | Luôn | Đổi trạng thái (xem dưới) |

## Màn 77 — Thêm phòng (Dialog)
| Trường | Bắt buộc | Quy tắc |
| --- | --- | --- |
| Tên hoặc mã phòng | Có | Duy nhất; trùng → `MSG-98` |
| Loại phòng | Có | 3 loại trên |
| Mô tả | Không | |
| Dịch vụ hỗ trợ | Có (≥ 1) | Chọn nhiều, **chỉ liệt kê dịch vụ đang cung cấp thuộc đúng loại phòng**. Đổi loại phòng thì xóa lựa chọn không còn khớp |

| Nút | Hành động |
| --- | --- |
| **Thêm phòng** (chính) | Gửi; thành công `MSG-61` ("Đã lưu thông tin phòng.") |
| Hủy | Đóng |

Lỗi: `MSG-62` đỏ dưới ô (thông tin phòng hoặc dịch vụ không hợp lệ); `MSG-98` đỏ dưới ô tên; `MSG-04` toast.

## Màn 78 và 79 — Chi tiết, sửa phòng và dịch vụ của phòng
- Chi tiết (Sheet): tên, loại, mô tả, trạng thái, dịch vụ hỗ trợ; có thể kèm "ca sắp tới của phòng" (chỉ đọc, đề xuất).
- **Sửa** chỉ đổi **mô tả** và **dịch vụ hỗ trợ** (UC66). **Tên/mã và loại phòng chỉ đọc** (đề xuất: R3 không cho đổi). Lưu → `MSG-61`; không hợp lệ → `MSG-62`; phòng không còn → `MSG-50`.
- Màn 79 "Room Service" của R3 là phần chọn dịch vụ trong form sửa; không cần màn riêng.

## Đổi trạng thái phòng (UC67)
1. Bấm `Switch` hoặc nút **Đánh dấu tạm ngưng / Đánh dấu sẵn sàng** trong Sheet.
2. `AlertDialog` xác nhận. Tạm ngưng: "Phòng sẽ không được dùng để phân công bệnh nhân hay xếp ca." Sẵn sàng: "Phòng được dùng lại."
3. Thành công: toast `MSG-63` ("Đã cập nhật trạng thái phòng thành {status}.").
4. Xung đột đang dùng: toast `MSG-64` ("Phòng đang được sử dụng hoặc đã được phân công. Vui lòng kiểm tra trước khi đổi trạng thái."); `Switch` trở về giá trị cũ.
5. Phòng ngoài quyền: `MSG-50`.

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/rooms?q=&type=&status=&serviceId=` | |
| POST | `/api/v1/admin/rooms` | `{name, type, description, serviceIds[]}` |
| GET | `/api/v1/admin/rooms/{id}` | |
| PUT | `/api/v1/admin/rooms/{id}` | `{description, serviceIds[]}` |
| PUT | `/api/v1/admin/rooms/{id}/status` | `{status: AVAILABLE \| UNAVAILABLE}`; `409` + `MSG-64` |

## Câu hỏi mở
- Có cần "quầy tiếp nhận" như một phòng để gán ca Lễ tân? (xem [04](04-staff-shifts.md))
- Phòng đang có ca trong tương lai mà đặt Tạm ngưng: coi là xung đột (`MSG-64`) hay chỉ cảnh báo?
