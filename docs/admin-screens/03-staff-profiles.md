---
module: Hồ sơ nhân viên
route: /admin/staff-profiles
ucs: [UC16 (phần Admin), UC17, UC18, UC19]
screens: ["91 Clinic Staff Profile List", "92 Clinic Staff Profile Detail"]
br: [BR-09, BR-18, BR-20]
msg: [MSG-04, MSG-22, MSG-23, MSG-36, MSG-92]
data_dependency: tài khoản nhân viên (02); gỡ hồ sơ bác sĩ cần biết lịch hẹn tương lai (05)
---

# Hồ sơ nhân viên (UC16–UC19)

Admin tạo, sửa, gỡ hồ sơ của **Lễ tân, Bác sĩ chỉnh hình, KTV chẩn đoán hình ảnh, KTV xét nghiệm**. Đây là mục đã đổi tên từ "Hồ sơ bác sĩ" (xem [audit](00-sidebar-audit-vs-r3.md)).

## Quy tắc cần nhớ
- **Tài khoản đăng nhập tách khỏi hồ sơ** (BR-09). Hồ sơ chỉ tạo cho một tài khoản nhân viên đã có; mỗi tài khoản tối đa một hồ sơ.
- Hồ sơ cơ bản (mọi vai trò, nội bộ): họ tên hiển thị, ảnh, liên hệ công việc.
- Chỉ **bác sĩ** có thêm: chuyên khoa, trình độ, tiểu sử, thông tin khám, cờ **Hiển thị công khai**. Chỉ thông tin bác sĩ đã bật công khai mới xuất hiện trên website.
- **Gỡ hồ sơ ≠ xóa**: tài khoản, lịch hẹn, bệnh án cũ giữ nguyên (BR-18). Bác sĩ bị gỡ biến khỏi trang công khai và danh sách chọn bác sĩ khi đặt lịch.
- Bác sĩ còn **lịch hẹn tương lai** thì chưa gỡ được; phải chuyển bác sĩ (UC22) hoặc để bệnh nhân hủy trước (BR-18).

## Màn 91 — Danh sách hồ sơ
`PageHeader`: "Hồ sơ nhân viên"; nút chính **Tạo hồ sơ**.

**Bộ lọc:** tìm kiếm (họ tên, tên đăng nhập); Vai trò; Hồ sơ (Đang hoạt động / Đã gỡ); Công khai (Có / Không; chỉ áp cho bác sĩ).

| Cột | Ghi chú |
| --- | --- |
| Ảnh + Họ tên hiển thị | |
| Vai trò | |
| Tên đăng nhập | Tài khoản liên kết |
| Liên hệ công việc | |
| Hiển thị công khai | Badge Có/Không; bác sĩ mới có, vai trò khác ghi "—" |
| Trạng thái hồ sơ | Đang hoạt động / Đã gỡ |

| Nút dòng | Hiện khi | Hành động |
| --- | --- | --- |
| Xem | Luôn | Mở màn 92 dạng Sheet |
| Sửa | Hồ sơ đang hoạt động | Mở màn 92 ở chế độ sửa |

## Màn 92 — Tạo / xem / sửa hồ sơ
**Tạo hồ sơ (Dialog, từ nút chính)**
1. Chọn **Tài khoản nhân viên**: danh sách chỉ gồm nhân viên Active **chưa có hồ sơ**. Tài khoản không đủ điều kiện → `MSG-23`.
2. Form hiện trường theo vai trò đã chọn.

| Trường | Áp cho | Bắt buộc | Ghi chú |
| --- | --- | --- | --- |
| Họ tên hiển thị | Mọi vai trò | Có | |
| Ảnh đại diện | Mọi vai trò | Không | Tải ảnh; xem trước; giới hạn dung lượng/định dạng do FE-BE chốt |
| Liên hệ công việc | Mọi vai trò | Có | Số điện thoại hoặc email công việc (R3 chưa chốt cụ thể) |
| Chuyên khoa | Bác sĩ | Không | Chọn nhiều |
| Trình độ | Bác sĩ | Không | |
| Tiểu sử | Bác sĩ | Không | Văn bản nhiều dòng |
| Thông tin khám | Bác sĩ | Không | Văn bản nhiều dòng (lịch tư vấn, lưu ý) |
| Hiển thị công khai | Bác sĩ | — | `Switch`; mặc định **tắt** |

| Nút | Hành động |
| --- | --- |
| **Lưu hồ sơ** (chính) | Gửi; thành công `MSG-92` ("Đã lưu thay đổi hồ sơ nhân viên.") |
| Hủy | Đóng |

- Lưu bác sĩ khi công khai = tắt là hợp lệ (AF1 của UC17): bác sĩ vẫn vắng trên trang công khai.

**Xem / sửa (Sheet)**: hiện toàn bộ trường trên, đổi sang ô nhập khi bấm **Sửa**.

| Nút | Hiện khi | Hành động |
| --- | --- | --- |
| Sửa | Chế độ xem | Mở ô nhập |
| Lưu thay đổi | Chế độ sửa | `MSG-92` |
| Công khai / Gỡ công khai | Bác sĩ | `Switch` đổi cờ; thông báo `MSG-92`; trang công khai cập nhật ngay (AF1 của UC18) |
| Xem trang công khai | Bác sĩ đã công khai | Mở tab mới trang hồ sơ trên website (đề xuất, giúp Admin kiểm tra) |
| Gỡ hồ sơ | Hồ sơ đang hoạt động | `AlertDialog` (xem dưới) |

**Gỡ hồ sơ — AlertDialog**
- Nội dung nêu hậu quả: "Hồ sơ sẽ khỏi danh sách nhân viên đang hoạt động. Bác sĩ sẽ khỏi trang công khai và việc chọn bác sĩ. Tài khoản, lịch hẹn và bệnh án được giữ." Nút **Gỡ hồ sơ** (destructive) / Hủy.
- Thành công: hồ sơ chuyển "Đã gỡ", `MSG-92`.
- Bác sĩ còn lịch hẹn tương lai: chặn gỡ, toast "Ca làm việc còn lịch hẹn bị ảnh hưởng. Vui lòng chuyển các lịch hẹn sang bác sĩ khác hoặc hủy trước." (R3 dùng mã `MSG-36` cho trường hợp này, UC19 E4) kèm nút **Xem lịch hẹn** đi tới `/admin/appointments?doctorId=…` ([05](05-appointments.md)).

**Lỗi**
| MSG | Khi | Hiển thị |
| --- | --- | --- |
| MSG-22 | Thông tin hồ sơ chưa hợp lệ | Toast lỗi, giữ form |
| MSG-23 | Tài khoản/hồ sơ không còn, không đủ điều kiện, đã có hồ sơ, hoặc ngoài quyền | Toast lỗi; với tạo mới thì cho chọn tài khoản khác |
| MSG-04 | Lỗi dịch vụ | Toast lỗi |

## Không có
- Nút Xóa hồ sơ, nút đổi tài khoản liên kết, tạo tài khoản đăng nhập từ màn này (đi sang [02](02-user-accounts.md)).

## API dự kiến
| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/api/v1/admin/staff-profiles?q=&role=&status=&public=&page=` | |
| GET | `/api/v1/admin/staff-profiles/eligible-accounts` | Nhân viên Active chưa có hồ sơ |
| POST | `/api/v1/admin/staff-profiles` | `{accountId, displayName, workContact, photo?, doctor?: {specialties, qualifications, biography, consultationInfo, isPublic}}` |
| PUT | `/api/v1/admin/staff-profiles/{id}` | |
| POST | `/api/v1/admin/staff-profiles/{id}/remove` | Trả `409` + `code: MSG-36` nếu còn lịch hẹn |

## Câu hỏi mở
- "Liên hệ công việc" gồm những trường nào (điện thoại, email)? Ảnh lưu ở đâu (Supabase Storage)?
- Có cho **khôi phục** hồ sơ đã gỡ không? R3 chưa có UC; hiện chỉ tạo lại (tài khoản đã gỡ hồ sơ có được xem là "chưa có hồ sơ" không?).
