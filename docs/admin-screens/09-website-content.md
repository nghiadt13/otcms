---
module: Nội dung website
route: /admin/website-content
ucs: [UC72, UC73]
screens: ["98 Website Content"]
br: [BR-61]
msg: [MSG-04, MSG-50, MSG-68, MSG-69]
data_dependency: không (làm được ngay); trang chủ public thay dữ liệu giả bằng API này
---

# Nội dung website (UC72–UC73)

Admin thêm và sửa nội dung công khai của website: thông tin phòng khám, bài viết sức khỏe, trang giới thiệu. Bài đăng thông báo nằm ở [10-announcements.md](10-announcements.md).

## Quy tắc cần nhớ
- Chỉ Admin đăng và sửa (BR-61). Guest và Bệnh nhân chỉ thấy nội dung **đã đăng**.
- Loại nội dung (R3, thực thể WebsiteContent): thông tin phòng khám (gồm giờ mở cửa), bài viết sức khỏe, trang giới thiệu.
- Đăng là **đăng ngay** khi bấm xác nhận: bước tạo (UC72) và sửa (UC73) đều kết thúc bằng "công khai". R3 không có nháp.

## Màn 98 — Quản lý nội dung
`PageHeader`: "Nội dung website"; nút chính **Thêm nội dung**.

**Bộ lọc:** tìm kiếm (tiêu đề); Loại nội dung.

| Cột | Ghi chú |
| --- | --- |
| Tiêu đề | |
| Loại | Thông tin phòng khám / Bài viết sức khỏe / Trang giới thiệu |
| Ngày đăng | |
| Cập nhật lần cuối | |
| Người đăng | Tài khoản Admin |
| (thao tác) | |

| Nút dòng | Hành động |
| --- | --- |
| Sửa | Mở form sửa |
| Xem trên website | Mở tab mới trang công khai tương ứng (đề xuất) |

## Form thêm / sửa (Sheet rộng hoặc trang riêng)
| Trường | Bắt buộc | Ghi chú |
| --- | --- | --- |
| Loại nội dung | Có | Khi sửa: chỉ đọc |
| Tiêu đề | Có | |
| Nội dung | Có | Trình soạn văn bản; định dạng cơ bản (đoạn, in đậm, danh sách, liên kết, ảnh). Chọn editor là việc của FE (R3 không quy định) |
| Ảnh minh họa | Không | Dùng cho bài viết (đề xuất) |

| Nút | Hành động |
| --- | --- |
| **Đăng nội dung** (khi thêm) / **Đăng bản cập nhật** (khi sửa) — chính | `AlertDialog` xác nhận ngắn "Nội dung sẽ hiển thị công khai ngay." → gửi; thành công `MSG-68` ("Nội dung trên website đã được cập nhật.") |
| Xem trước | Mở bản xem trước, không lưu (đề xuất) |
| Hủy | Đóng, hỏi nếu có thay đổi chưa lưu |

**Lỗi**
| MSG | Khi | Hiển thị |
| --- | --- | --- |
| MSG-69 | Thiếu hoặc không phù hợp để đăng | Đỏ dưới ô |
| MSG-50 | Nội dung không còn / không đủ quyền | Toast lỗi, đóng form |
| MSG-04 | Lỗi dịch vụ | Toast lỗi |

## Không có
- Xóa, gỡ đăng, lưu nháp, lên lịch đăng: R3 chưa có. Xem câu hỏi mở.

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/website-contents?type=&q=&page=` | |
| POST | `/api/v1/admin/website-contents` | `{type, title, body}` |
| PUT | `/api/v1/admin/website-contents/{id}` | |
| GET | `/api/v1/public/website-contents?type=` | Phía công khai, chỉ nội dung đã đăng |

## Câu hỏi mở
- Cần thêm **Gỡ đăng** (ẩn khỏi website) không? BR-61 nói người xem chỉ thấy nội dung "published", ngụ ý có trạng thái chưa đăng nhưng không UC nào tạo ra nó. Nếu cần, bổ sung UC.
- Giờ mở cửa và thông tin liên hệ: là một nội dung loại "thông tin" hay cấu hình riêng?
