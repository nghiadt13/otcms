---
module: Nhật ký hoạt động
route: /admin/activity-logs
ucs: [UC76]
screens: ["86 Activity Log List", "87 Activity Log Detail"]
br: [BR-47, BR-62]
msg: [MSG-04, MSG-50, MSG-54]
data_dependency: backend ghi nhật ký mỗi thao tác ghi (làm cùng module đầu tiên); không phụ thuộc module khác
---

# Nhật ký hoạt động (UC76)

Admin xem lại ai đã làm gì, lúc nào, trên bản ghi nào. **Chỉ đọc.** R3 20/10 gọi là *Activity Log* (trước đây *Audit Log*).

## Quy tắc cần nhớ
- Ghi nhận thao tác liên quan tài khoản, hồ sơ y khoa (kể cả **sửa kết quả đã duyệt kèm lý do**) và vận hành phòng khám.
- **Không bao giờ hiển thị nội dung y khoa hoặc giá trị cũ** (BR-62, BR-47). Chỉ hiện: người thực hiện, thời gian, hành động, bản ghi bị tác động, lý do chỉnh sửa (nếu có).
- Admin chỉ thấy nhật ký trong **phạm vi giám sát** của mình (BR-62); Clinic Owner có phạm vi riêng.
- Không sửa, không xóa, không xuất.

## Màn 86 — Danh sách
`PageHeader`: "Nhật ký hoạt động"; không có nút chính.

**Bộ lọc:** khoảng thời gian (mặc định 7 ngày); Loại hành động (Tài khoản, Hồ sơ nhân viên, Ca làm việc, Phòng, Dịch vụ, Nội dung, Thông báo, Đánh giá, Thanh toán, Sửa kết quả…); Bản ghi bị tác động (nhập mã/tên); (đề xuất) Người thực hiện. Sai tiêu chí → `MSG-54`.

| Cột | Ghi chú |
| --- | --- |
| Thời gian | `dd/MM/yyyy HH:mm:ss` |
| Người thực hiện | Tên đăng nhập + vai trò |
| Hành động | Câu ngắn ("Tạo tài khoản Lễ tân", "Vô hiệu hóa tài khoản") |
| Bản ghi | Loại + mã/tên hiển thị |
| Lý do chỉnh sửa | Chỉ với sửa kết quả đã duyệt; còn lại "—" |

Sắp xếp mới nhất trước; phân trang phía server.

| Nút | Hành động |
| --- | --- |
| Xem (dòng) | Mở Sheet chi tiết (màn 87) |

## Màn 87 — Chi tiết (Sheet)
Các trường trên + mã bản ghi + ghi chú hệ thống nếu có. **Không** có trường nội dung y khoa hay giá trị trước/sau. Nút duy nhất **Đóng**.

## Trạng thái
- Không có mục nào: trạng thái trống "Không có hoạt động khớp bộ lọc." (AF1).
- Mục không còn hoặc ngoài phạm vi: `MSG-50`, đóng Sheet.
- Lỗi tải: `MSG-04` + Thử lại.

## Không có
- Xuất nhật ký, xóa/sửa mục, xem nội dung thay đổi.

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/activity-logs?from=&to=&action=&record=&actor=&page=` | |
| GET | `/api/v1/admin/activity-logs/{id}` | |

## Câu hỏi mở
- Danh mục "Loại hành động" chuẩn (R3 chưa liệt kê): BE và FE cần thống nhất mã hành động ngay từ module đầu tiên để nhật ký ghi đúng.
- Phạm vi giám sát của Admin so với Owner cụ thể là gì?
