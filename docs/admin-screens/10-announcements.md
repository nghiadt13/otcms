---
module: Thông báo phòng khám
route: /admin/announcements
ucs: [UC74]
screens: ["82 Announcement List", "83 Create Announcement", "84 Announcement Detail", "85 Announcement Edit"]
br: [BR-61]
msg: [MSG-04, MSG-50, MSG-70, MSG-71]
data_dependency: dịch vụ gửi email (Notification Service); không phụ thuộc module khác
---

# Thông báo phòng khám (UC74)

Admin đăng thông báo cho một nhóm người nhận và gửi qua các kênh có sẵn. Đã đổi tên từ "Thông báo" để khỏi nhầm với **hộp thư cá nhân** của Admin ([15](15-personal-area.md)).

## Quy tắc cần nhớ
- Chỉ Admin đăng (BR-61). Thông báo chỉ tới **nhóm người nhận đã chọn**.
- Nhóm **Công khai**: hiện cho Guest và Bệnh nhân ở trang thông tin phòng khám (AF1).
- Kênh gửi: **Email** và **trong hệ thống**; không có SMS.
- Kết quả giao hiển thị khi hệ thống hỗ trợ (trạng thái giao).

## Màn 82 — Danh sách thông báo
`PageHeader`: "Thông báo phòng khám"; nút chính **Đăng thông báo**.

**Bộ lọc:** tìm kiếm (tiêu đề); nhóm người nhận; khoảng ngày đăng.

| Cột | Ghi chú |
| --- | --- |
| Tiêu đề | |
| Nhóm người nhận | Công khai / Bệnh nhân / theo vai trò nhân viên |
| Kênh | Email, Trong hệ thống |
| Ngày đăng | |
| Trạng thái giao | "Đã gửi" / "Một phần lỗi" / "Chưa hỗ trợ" (chỉ khi BE trả) |
| (thao tác) | Xem |

## Màn 83 — Đăng thông báo (Dialog)
| Trường | Bắt buộc | Ghi chú |
| --- | --- | --- |
| Tiêu đề | Có | |
| Nội dung | Có | Văn bản nhiều dòng |
| Nhóm người nhận | Có | Chọn từ danh sách server trả ("eligible audience groups"); mặc định không chọn sẵn |
| Kênh gửi | Có (≥ 1) | Checkbox các kênh server cho phép |

| Nút | Hành động |
| --- | --- |
| **Đăng thông báo** (chính) | `AlertDialog` xác nhận: "Thông báo sẽ gửi tới {nhóm} qua {kênh}." → gửi; thành công `MSG-70` ("Đã đăng thông báo cho nhóm người nhận đã chọn.") + hiện kết quả giao |
| Hủy | Đóng |

Lỗi: `MSG-71` đỏ dưới ô nội dung/nhóm; `MSG-50` toast (không đủ quyền); `MSG-04` toast.

## Màn 84 — Chi tiết (Sheet)
Tiêu đề, nội dung, nhóm, kênh, ngày đăng, người đăng, trạng thái giao. Chỉ đọc. Nút **Đóng**.

## Màn 85 — Sửa thông báo
R3 có màn "Announcement Edit" (cập nhật nội dung và thông tin đăng) nhưng **UC74 chỉ mô tả đăng mới**, không có luồng sửa. Đề xuất: dựng nút **Sửa** trong Sheet chi tiết nhưng để sau, khi nhóm chốt luật sửa (có gửi lại không, báo lại người đã nhận không). **Chưa dựng nút này ở bản đầu.**

## Không có
- Gửi SMS; hẹn giờ đăng; xóa thông báo đã gửi.

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/announcements?q=&audience=&from=&to=&page=` | |
| GET | `/api/v1/admin/announcements/options` | Nhóm người nhận và kênh khả dụng |
| POST | `/api/v1/admin/announcements` | `{title, body, audience, channels[]}`; trả kết quả giao |
| GET | `/api/v1/admin/announcements/{id}` | |

## Câu hỏi mở
- Danh sách **nhóm người nhận** cụ thể (R3 chỉ nói "chosen group"): Công khai, Bệnh nhân, từng vai trò nhân viên?
- Sửa/thu hồi thông báo đã đăng (màn 85) hoạt động ra sao?
- Thông báo công khai có cần xuất hiện trong [09-website-content.md](09-website-content.md) hay chỉ trang "Thông báo" của website? R3 coi announcement là một loại WebsiteContent.
