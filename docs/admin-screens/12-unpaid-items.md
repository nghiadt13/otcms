---
module: Khoản chưa thanh toán
route: /admin/unpaid-items
ucs: [UC57]
screens: ["102 Unpaid Items Overview"]
br: [BR-01, BR-48, BR-49]
msg: [MSG-04, MSG-50, MSG-54]
data_dependency: phí khám và chỉ định ở trạng thái Pending Payment (chưa có BE) → FE dùng dữ liệu giả
---

# Khoản chưa thanh toán (UC57)

Admin **chỉ xem** các phí khám và chỉ định đang *Pending Payment*. Đã đổi tên từ "Hóa đơn chưa thanh toán": R3 không có hóa đơn và không có thanh toán từng phần.

## Quy tắc cần nhớ
- Phí khám phải trả **trước khi check-in**; chỉ định phải trả **trước khi thực hiện**; chỉ định chưa trả vẫn trả được khi lượt khám còn *Awaiting Conclusion* (BR-48).
- Mỗi khoản trả trọn, không có "còn nợ một phần" (BR-49). Bỏ cột/số liệu "đã trả / còn nợ".
- Việc **thu tiền** là của Lễ tân (UC54). Admin không có nút thu.

## Màn 102 — Danh sách khoản chưa thanh toán
`PageHeader`: "Khoản chưa thanh toán"; không có nút chính.

**Bộ lọc:** khoảng ngày (theo thời gian chỉ định); tìm bệnh nhân (tên, SĐT); Dịch vụ; Loại khoản (Phí khám / Chỉ định). Sai tiêu chí → `MSG-54`.

| Cột | Ghi chú |
| --- | --- |
| Bệnh nhân | |
| Lượt khám / Lịch hẹn | Mã + ngày |
| Khoản | Tên dịch vụ hoặc "Phí khám" |
| Giá | VND, giá đã cố định lúc đặt/chỉ định (BR-59) |
| Thời gian chỉ định | |

Dòng tổng cuối (đề xuất): số khoản và tổng tiền đang chờ trong kỳ lọc.

| Nút | Hành động |
| --- | --- |
| Xem (dòng) | Sheet chi tiết: bệnh nhân, lượt khám/lịch hẹn, khoản, giá, thời gian chỉ định, bác sĩ chỉ định. Nút duy nhất **Đóng** |

**Lỗi / trống:** khoản không còn hoặc ngoài quyền `MSG-50`; không có khoản → trạng thái trống "Hiện không có khoản nào chờ thanh toán." (AF1); lỗi tải `MSG-04` + Thử lại.

## Không có
- Thu tiền, ghi nhận thanh toán, gửi nhắc, hủy khoản, in phiếu thu.

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/unpaid-items?from=&to=&q=&serviceId=&kind=&page=` | |
| GET | `/api/v1/admin/unpaid-items/{id}` | |

## Câu hỏi mở
- Có cần nút "Nhắc bệnh nhân" (gửi thông báo)? R3 không có; nếu cần phải thêm UC.
