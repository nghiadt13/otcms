---
module: Đánh giá bệnh nhân
route: /admin/reviews
ucs: [UC61, UC62]
screens: ["99 Review Moderation", "100 Clinic Responses"]
br: [BR-55, BR-56]
msg: [MSG-04, MSG-50, MSG-56, MSG-57, MSG-58, MSG-59]
data_dependency: đánh giá do bệnh nhân gửi sau lượt khám Completed (chưa có BE) → FE dùng dữ liệu giả
---

# Đánh giá bệnh nhân (UC61–UC62)

Admin duyệt/ẩn đánh giá của bệnh nhân và đăng phản hồi của phòng khám.

## Quy tắc cần nhớ
- Đánh giá chỉ **công khai sau khi Admin duyệt** (BR-55). Ẩn một đánh giá đã công khai thì gỡ khỏi website nhưng **giữ lịch sử kiểm duyệt**.
- Chỉ Admin trả lời nhân danh phòng khám. Phản hồi **chỉ hiển thị công khai khi đánh giá đang công khai** (BR-56).
- Chỉ bệnh nhân có lượt khám **Completed** mới gửi đánh giá (BR-54); không phải việc của Admin.
- Admin không sửa nội dung đánh giá và không xóa.

## Màn 99 — Danh sách kiểm duyệt
`PageHeader`: "Đánh giá bệnh nhân"; không có nút chính.

**Tab theo trạng thái:** Chờ duyệt (mặc định, kèm số đếm) · Đang hiển thị · Đã ẩn · Tất cả.
**Bộ lọc:** khoảng ngày; số sao (1–5); Đã phản hồi / Chưa phản hồi.

| Cột | Ghi chú |
| --- | --- |
| Ngày gửi | |
| Bệnh nhân | Họ tên (có thể rút gọn tên; đề xuất, R3 không quy định) |
| Ngày khám · Bác sĩ | Ngữ cảnh lượt khám |
| Điểm | Sao + số, kèm chữ (ví dụ "4/5") |
| Nội dung | Rút gọn 2 dòng |
| Trạng thái | Chờ duyệt / Đang hiển thị / Đã ẩn |
| Phản hồi | Đã trả lời / Chưa |

Bấm dòng: mở Sheet chi tiết (màn 100).

## Sheet chi tiết và phản hồi (màn 99 + 100)
Nội dung đầy đủ, điểm, ngữ cảnh lượt khám (ngày, bác sĩ, dịch vụ), trạng thái, phản hồi hiện có (nếu có).

| Nút | Hiện khi | Hành động |
| --- | --- | --- |
| **Duyệt và hiển thị** | Đánh giá Chờ duyệt | Gửi quyết định; thành công `MSG-58` ("Đã cập nhật trạng thái hiển thị của đánh giá.") |
| **Ẩn đánh giá** (destructive) | Đang hiển thị | `AlertDialog`: "Đánh giá sẽ không còn hiển thị trên website. Lịch sử kiểm duyệt được giữ." → **Ẩn đánh giá** / Hủy. Thành công `MSG-58` |
| Từ chối (không hiển thị) | Chờ duyệt | Dùng cùng hành động **Ẩn đánh giá** (R3 chỉ có Approve hoặc Hide) |
| **Gửi phản hồi** | Ô phản hồi có nội dung | Lưu phản hồi; thành công `MSG-59` ("Đã lưu phản hồi của phòng khám.") |
| Đóng | Luôn | |

**Ô phản hồi:** `Textarea` "Phản hồi của phòng khám". Gợi ý dưới ô: "Phản hồi chỉ hiển thị công khai khi đánh giá đang được hiển thị." Nội dung không hợp lệ (rỗng, quá dài): `MSG-57` đỏ dưới ô.

**Lỗi**
| MSG | Khi | Hiển thị |
| --- | --- | --- |
| MSG-56 | Trạng thái đã đổi nên quyết định không còn áp dụng, hoặc đánh giá không được phản hồi | Toast lỗi; tải lại đánh giá |
| MSG-50 | Đánh giá không còn / không đủ quyền | Toast lỗi, đóng Sheet |
| MSG-57 | Phản hồi không hợp lệ | Đỏ dưới ô |
| MSG-04 | Lỗi dịch vụ | Toast lỗi |

## Không có
- Sửa nội dung đánh giá, xóa đánh giá, tự viết đánh giá. Gửi thông báo cho bệnh nhân khi duyệt: không có trong R3.

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/reviews?status=&rating=&replied=&from=&to=&page=` | |
| GET | `/api/v1/admin/reviews/{id}` | Kèm `allowedActions` |
| POST | `/api/v1/admin/reviews/{id}/approve` · `/hide` | |
| PUT | `/api/v1/admin/reviews/{id}/reply` | `{text}` |

## Câu hỏi mở
- Đánh giá **đã ẩn** có được duyệt lại không? R3 UC61 chỉ nói Approve hoặc Hide. FE cứ dựa `allowedActions` do server trả để không phải sửa lại.
- Phản hồi đã đăng có được sửa không? R3 chưa có.
- Có cho trả lời đánh giá **chưa duyệt** không (phản hồi sẽ chưa hiện)? Đề xuất cho phép.
