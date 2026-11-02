---
module: Báo cáo
route: /admin/reports
ucs: [UC78]
screens: ["103 Clinic Reports"]
br: [BR-62]
msg: [MSG-04, MSG-50, MSG-72, MSG-73]
data_dependency: toàn bộ dữ liệu vận hành (chưa có BE) → FE dùng dữ liệu giả
---

# Báo cáo (UC78)

Admin tạo báo cáo theo kỳ và chủ đề; xem trên màn hình hoặc xuất file.

## Chủ đề báo cáo (theo UC78 bước 6)
| Chủ đề | Nội dung |
| --- | --- |
| Lịch hẹn | Theo trạng thái, theo bác sĩ, theo ngày |
| Lượt khám | Theo trạng thái, loại (khám lần đầu / tái khám) |
| Cận lâm sàng | Chẩn đoán hình ảnh và xét nghiệm đã thực hiện |
| Dịch vụ | Số lần dùng theo dịch vụ |
| Thanh toán | Theo **ngày**, theo **Lễ tân**, theo **phương thức** (Tiền mặt / VNPay) |
| Chỉ định chờ và đã hủy | *Pending Payment* và *Cancelled* |
| Phản hồi bệnh nhân | Điểm và số đánh giá |

## Màn 103 — Tạo báo cáo
`PageHeader`: "Báo cáo"; không có nút chính ở header (nút **Tạo báo cáo** nằm trong form).

**Form tạo báo cáo (đầu trang)**
| Trường | Bắt buộc | Ghi chú |
| --- | --- | --- |
| Chủ đề báo cáo | Có | Chọn 1 trong 7 chủ đề trên; chỉ liệt kê chủ đề trong phạm vi quyền của Admin |
| Từ ngày · Đến ngày | Có | |
| Tách theo (chỉ với Thanh toán) | Không | Ngày / Lễ tân / Phương thức |

| Nút | Hành động |
| --- | --- |
| **Tạo báo cáo** (chính) | Gọi API; đang chạy "Đang tạo..."; thành công toast `MSG-73` ("Báo cáo đã sẵn sàng để xem hoặc xuất.") rồi hiện kết quả bên dưới |
| Đặt lại | Xóa form và kết quả |

**Kết quả:** bảng số liệu + (tuỳ chọn) biểu đồ cột/đường theo `dataviz` của dự án; dòng tổng.
| Nút | Hiện khi | Hành động |
| --- | --- | --- |
| **Xuất báo cáo** | Có kết quả | Tải file (định dạng chọn từ menu: **Excel (.xlsx)**, **PDF**; R3 chưa quy định, đề xuất hai loại này) |

- Kỳ hoặc chủ đề không hợp lệ: `MSG-72` đỏ dưới ô; chủ đề ngoài quyền: `MSG-50`; lỗi tạo: `MSG-04`.
- Kỳ không có dữ liệu: vẫn tạo báo cáo, hiện "Không có hoạt động trong kỳ này" (AF1); vẫn cho xuất.

## Không có
- Lưu báo cáo, lên lịch gửi báo cáo, sửa số liệu.

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/reports/subjects` | Chủ đề theo quyền |
| POST | `/api/v1/admin/reports` | `{subject, from, to, breakdown?}`; trả dữ liệu bảng |
| GET | `/api/v1/admin/reports/export?...&format=xlsx\|pdf` | |

## Câu hỏi mở
- Định dạng xuất (xlsx/pdf/csv) và có cần biểu đồ không? R3 chỉ nói "viewing or export".
- Khác biệt phạm vi báo cáo giữa Admin và Clinic Owner (R3: "oversight scope", chưa liệt kê).
