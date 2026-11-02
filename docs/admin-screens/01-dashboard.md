---
module: Bảng điều khiển
route: /admin
ucs: [UC77]
screens: ["67 Admin Dashboard"]
br: [BR-62]
msg: [MSG-04, MSG-50, MSG-72]
data_dependency: appointments, visits, queues, orders, payments, reviews (chưa có BE, FE dùng dữ liệu giả)
---

# Bảng điều khiển (UC77)

Trang đầu khi Admin đăng nhập: tóm tắt vận hành phòng khám theo một kỳ. **Chỉ đọc**, không có nút thay đổi dữ liệu.

## Bố cục
`PageHeader` ("Bảng điều khiển", "Tóm tắt vận hành theo kỳ") → chọn kỳ → các khối số liệu → lối tắt tới module.

## Chọn kỳ
| Điều khiển | Hành vi |
| --- | --- |
| Nhóm nút kỳ nhanh: Hôm nay · 7 ngày · 30 ngày · Tháng này | Chọn là tải lại số liệu. Mặc định "7 ngày" (đề xuất; R3 chỉ nói "default permitted period") |
| Chọn khoảng ngày (từ – đến) + nút **Xem** | Dùng khi cần kỳ tùy chọn |

- Kỳ không hợp lệ (từ > đến, vượt phạm vi cho phép): `MSG-72` đỏ dưới ô ngày; giữ số liệu cũ.

## Các khối số liệu (theo UC77 bước 5)
| Khối | Nội dung gợi ý |
| --- | --- |
| Lịch hẹn | Tổng; theo trạng thái Booked / Checked In / Cancelled / No-show |
| Lượt khám và hàng đợi | Lượt khám theo trạng thái; số người đang chờ theo phòng |
| Cận lâm sàng | Số chỉ định chẩn đoán hình ảnh và xét nghiệm đã thực hiện |
| Dịch vụ | Dịch vụ dùng nhiều nhất |
| Thanh toán đã thu | Tổng tiền; tách Tiền mặt / VNPay |
| Chỉ định chờ và đã hủy | Số chỉ định *Pending Payment* và *Cancelled* |
| Phản hồi bệnh nhân | Điểm trung bình; số đánh giá chờ duyệt |

Mỗi khối có link "Xem chi tiết" tới module tương ứng (Lịch hẹn, Khoản chưa thanh toán, Đánh giá, Báo cáo). Không có nút xuất ở đây; xuất nằm ở [13-reports.md](13-reports.md).

## Nút
| Nút | Vị trí | Hiện khi | Hành động |
| --- | --- | --- | --- |
| Xem | Cạnh ô chọn khoảng ngày | Luôn | Gọi API số liệu theo kỳ |
| Xem chi tiết | Cuối mỗi khối | Luôn | Điều hướng sang module liên quan, mang theo kỳ đang chọn |
| Thử lại | Trạng thái lỗi | Khi tải lỗi | Gọi lại API |

## Trạng thái
- Tải: skeleton cho từng khối.
- Kỳ không có hoạt động: hiện **0** hoặc khối trống, không báo lỗi (AF1).
- Lỗi chung `MSG-04`; không đủ quyền `MSG-50`.

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/dashboard?from=&to=` | Trả đủ các khối; trường rỗng là 0 |

## Câu hỏi mở
- Kỳ mặc định và kỳ tối đa là bao lâu? (R3 chưa nêu.)
