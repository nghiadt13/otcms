---
module: Lịch sử thanh toán
route: /admin/payments
ucs: [UC56]
screens: ["101 Payment Overview"]
br: [BR-19, BR-49, BR-50, BR-51, BR-52, BR-53]
msg: [MSG-04, MSG-50, MSG-54]
data_dependency: thanh toán từ Lễ tân (UC54) và VNPay (UC55), chưa có BE → FE dùng dữ liệu giả
---

# Lịch sử thanh toán (UC56)

Admin **chỉ xem** các khoản thanh toán đã hoàn tất của phòng khám. Không có thao tác thay đổi nào.

## Quy tắc cần nhớ
- Một thanh toán trả trọn một hay nhiều khoản (phí khám, chỉ định); không trả từng phần, không đặt cọc, không giảm giá (BR-49).
- Phương thức: **Tiền mặt** (Lễ tân thu) hoặc **VNPay**. Chỉ lưu mã giao dịch VNPay, không có dữ liệu thẻ.
- Mỗi thanh toán có đúng **một phiếu thu**, không sửa sau khi phát hành (BR-52). R3 dùng *phiếu thu*, không phải hóa đơn.
- **Không hoàn tiền** trong mọi trường hợp (BR-53), nên không có nút hoàn/hủy.
- Phiếu thu hiển thị tên **bệnh nhân được khám**, không phải người trả (BR-50).

## Màn 101 — Danh sách thanh toán
`PageHeader`: "Lịch sử thanh toán"; không có nút chính.

**Bộ lọc:** khoảng ngày (mặc định hôm nay → 30 ngày trước, đề xuất); tìm kiếm (tên bệnh nhân, số phiếu thu); Phương thức (Tất cả / Tiền mặt / VNPay). Sai tiêu chí → `MSG-54`.

| Cột | Ghi chú |
| --- | --- |
| Số phiếu thu | |
| Ngày giờ | |
| Bệnh nhân | Người được khám |
| Khoản đã trả | Tóm tắt ("Phí khám + 2 chỉ định") |
| Số tiền | VND |
| Phương thức | Tiền mặt / VNPay |
| Thu ngân | Tên Lễ tân; trống nếu VNPay |

Dòng tổng cuối danh sách (đề xuất): tổng tiền của kỳ đang lọc, tách Tiền mặt / VNPay.

| Nút | Hành động |
| --- | --- |
| Xem (dòng) | Mở Sheet chi tiết |

**Sheet chi tiết:** các khoản (tên, giá), tổng, phương thức, mã giao dịch VNPay (nếu có), thu ngân, ngày giờ, số phiếu thu. Nút duy nhất: **Đóng**. Không có "In phiếu thu" (Lễ tân in, UC58).

**Lỗi / trống:** thanh toán không còn hoặc ngoài quyền `MSG-50`; không khớp tiêu chí → trạng thái trống; lỗi tải `MSG-04` + Thử lại.

## Không có
- Hoàn tiền, hủy thanh toán, sửa số tiền, thu tiền, in phiếu thu, xuất file (xuất nằm ở [13-reports.md](13-reports.md)).

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/payments?from=&to=&q=&method=&page=` | Trả kèm tổng kỳ |
| GET | `/api/v1/admin/payments/{id}` | |

## Câu hỏi mở
- Danh sách chỉ gồm thanh toán **Success** hay cả Failed/Pending (R3: "completed payments")? Đề xuất chỉ Success; thanh toán lỗi ghi vào nhật ký.
