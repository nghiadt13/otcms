---
module: Dịch vụ và giá
route: /admin/services
ucs: [UC68, UC69, UC70, UC71 (phần Admin)]
screens: ["72 Services List", "73 Create Service", "74 Service Detail", "75 Edit Service", "97 Laboratory Indicator Catalog"]
br: [BR-58, BR-59, BR-60]
msg: [MSG-04, MSG-50, MSG-54, MSG-65, MSG-66, MSG-67]
data_dependency: không (làm được ngay); phòng (06) dùng danh mục này
---

# Dịch vụ và giá (UC68–UC71)

Admin quản lý danh mục dịch vụ: **khám**, **chẩn đoán hình ảnh**, **xét nghiệm**, kèm giá và chỉ số xét nghiệm. UC71 (xem dịch vụ và giá) là phần công khai cho Guest/Bệnh nhân/Lễ tân; với Admin chính là màn danh sách này.

## Quy tắc cần nhớ
- Loại dịch vụ: `examination`, `imaging`, `laboratory`.
- **Chẩn đoán hình ảnh** có **phương thức**: X-quang, Siêu âm, CT, MRI.
- **Xét nghiệm** có **danh sách chỉ số**; mỗi chỉ số có tên, đơn vị, **khoảng tham chiếu riêng cho nam và nữ** (BR-58). Hệ thống chỉ gắn cờ bất thường, không có cờ nguy kịch.
- **Giá mới chỉ áp cho lần sau**; lịch hẹn và chỉ định đã tạo giữ giá cũ. Đổi chỉ số/khoảng tham chiếu chỉ áp cho chỉ định tạo sau; kết quả đã duyệt giữ chỉ số cũ (BR-59). Hiện chú thích này ở form sửa.
- "Xóa dịch vụ" thực chất là **Ngừng cung cấp**: không còn cho đặt mới, vẫn giữ trong lịch sử khám/thanh toán (BR-60). Không xóa cứng.

## Màn 72 — Danh sách dịch vụ
`PageHeader`: "Dịch vụ và giá"; nút chính **Thêm dịch vụ**.

**Bộ lọc:** tìm kiếm (tên); Loại; Trạng thái (Đang cung cấp / Đã ngừng). Sai tiêu chí → `MSG-54`.

| Cột | Ghi chú |
| --- | --- |
| Tên dịch vụ | |
| Loại | Khám / Chẩn đoán hình ảnh / Xét nghiệm |
| Phương thức | Chỉ dịch vụ chẩn đoán hình ảnh; còn lại "—" |
| Giá | VND |
| Trạng thái | Badge Đang cung cấp / Đã ngừng |
| (thao tác) | Xem, Sửa |

| Nút dòng | Hiện khi | Hành động |
| --- | --- | --- |
| Xem | Luôn | Sheet chi tiết (màn 74) |
| Sửa | Dịch vụ đang cung cấp | Mở form sửa |

## Màn 73 — Thêm dịch vụ (Dialog hoặc trang riêng, vì có bảng chỉ số)
| Trường | Áp cho | Bắt buộc | Ghi chú |
| --- | --- | --- | --- |
| Loại dịch vụ | Tất cả | Có | Đổi loại làm hiện/ẩn các trường dưới |
| Tên dịch vụ | Tất cả | Có | Trùng với dịch vụ đang cung cấp → `MSG-66` |
| Mô tả | Tất cả | Không | |
| Giá (VND) | Tất cả | Có | Số nguyên ≥ 0 |
| Chi tiết đặt lịch | Tất cả | Không | R3 chưa nêu cụ thể (xem câu hỏi mở) |
| Phương thức | Chẩn đoán hình ảnh | Có | X-quang / Siêu âm / CT / MRI |
| Bảng chỉ số | Xét nghiệm | Có (≥ 1 chỉ số) | Xem bảng chỉ số dưới |

**Bảng chỉ số xét nghiệm** (màn 97, nhúng trong form và trong Sheet chi tiết dịch vụ xét nghiệm)
| Cột | Ghi chú |
| --- | --- |
| Tên chỉ số | Bắt buộc |
| Đơn vị | Bắt buộc |
| Tham chiếu nam (từ – đến) | Số; từ ≤ đến |
| Tham chiếu nữ (từ – đến) | Số; từ ≤ đến |
| (xóa) | Nút **Xóa chỉ số** (icon thùng rác, `aria-label`) |

Nút dưới bảng: **Thêm chỉ số**.

| Nút | Hành động |
| --- | --- |
| **Thêm dịch vụ** (chính) | Gửi; thành công `MSG-65` ("Đã lưu thông tin dịch vụ."), dịch vụ xuất hiện ngay trong danh mục |
| Hủy | Đóng |

Lỗi: `MSG-66` đỏ dưới ô sai (không hợp lệ hoặc trùng dịch vụ đang cung cấp); `MSG-04` toast.

## Màn 74/75 — Chi tiết và sửa
- **Chi tiết (Sheet):** mọi trường như form; dịch vụ xét nghiệm hiện bảng chỉ số; dịch vụ ngừng thì có nhãn "Đã ngừng cung cấp" và **không có nút sửa**.
- **Sửa** (cùng form như thêm): **không đổi được Loại dịch vụ** (đề xuất) ; được đổi mô tả, giá, chi tiết đặt lịch, phương thức, và với xét nghiệm: thêm/sửa/xóa chỉ số và khoảng tham chiếu (AF2 UC69).
  - Gợi ý dưới ô giá: "Giá mới chỉ áp dụng cho lịch hẹn và chỉ định tạo sau."
  - Gợi ý trên bảng chỉ số: "Thay đổi chỉ áp dụng cho chỉ định tạo sau. Kết quả đã duyệt giữ nguyên."
- Nút **Lưu thay đổi** (chính) → `MSG-65`; **Hủy**.
- Lỗi: `MSG-66`; dịch vụ không còn hoặc ngoài quyền `MSG-50`; `MSG-04`.

## Ngừng cung cấp (UC70)
Nút **Ngừng cung cấp** (destructive, trong Sheet chi tiết, chỉ khi dịch vụ đang cung cấp) → `AlertDialog`:
"Dịch vụ sẽ không còn nhận đặt mới và khỏi danh mục. Lịch hẹn, bệnh án và thanh toán cũ vẫn giữ dịch vụ này." → **Ngừng cung cấp** / Hủy.
Thành công `MSG-67` ("Dịch vụ đã được ngừng cung cấp cho lượt đặt mới."). Dịch vụ đã ngừng hoặc không còn: `MSG-50`.

## Không có
- Xóa cứng; đổi loại dịch vụ; cờ kết quả nguy kịch; PDF kết quả cho từng xét nghiệm.
- Nút "Khôi phục dịch vụ đã ngừng": R3 chưa có UC (xem câu hỏi mở).

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/services?q=&type=&status=&page=` | |
| POST | `/api/v1/admin/services` | `{type, name, description, price, bookingDetails, modality?, indicators?[{name, unit, refMale:{min,max}, refFemale:{min,max}}]}` |
| GET | `/api/v1/admin/services/{id}` | |
| PUT | `/api/v1/admin/services/{id}` | |
| POST | `/api/v1/admin/services/{id}/discontinue` | |

## Câu hỏi mở
- **"Chi tiết đặt lịch" gồm gì?** R3 nhắc "booking details" nhưng không liệt kê. Đề xuất cho dịch vụ khám: độ dài khung giờ (phút, ≥ 10 theo BR-20) và ghi chú dặn bệnh nhân. Cần chốt trước khi dựng form.
- Khoảng tham chiếu là số (min–max) hay có thể là văn bản ("âm tính")? MSG-80 nói "ngoài khoảng giá trị hợp lệ" nên đề xuất số.
- Có cho khôi phục dịch vụ đã ngừng không?


## Live backend integration (KAN-12 / KAN-13)

The route now uses the live API contract documented in [admin-backend-integration.md](../admin-backend-integration.md). The design above describes the earlier mock/SRS target; fields missing from the backend contract are not silently stored in a mock or sent as unsupported fields. See that document for the exact supported scope and acceptance checks.
