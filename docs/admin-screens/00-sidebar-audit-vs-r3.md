# Đối chiếu sidebar Admin với Report 3 (SRS)

Nguồn: Report 3 trên Google Drive, bản 20/10/2026 (UC01–UC78, 37 màn hình Admin ở mục 1.4.2.4, ma trận quyền 1.4.3). Đối chiếu với `frontend/src/features/admin-shell/adminNavigation.ts`.

**Kết luận:** cả 13 mục sidebar đều có UC tương ứng trong R3 (không mục nào thừa). Cần **thêm 1 mục (Lịch hẹn)**, **đổi tên 3 mục**, **sửa số UC cũ ở mọi mục** và **sửa lệch ở 3 màn đã code** (Tài khoản, Phòng, Hồ sơ bác sĩ).

## 1. Sidebar đề xuất

| Nhóm | Mục | Route | UC | Thay đổi |
| --- | --- | --- | --- | --- |
| Tổng quan | Bảng điều khiển | `/admin` | UC77 | Giữ (trong code là nhóm `standalone`, ảnh chụp chưa thấy vì cắt phía trên) |
| Tài khoản và nhân sự | Tài khoản người dùng | `/admin/accounts` | UC07–09 | Giữ tên; sửa nội dung màn (mục 3) |
| | **Hồ sơ nhân viên** | `/admin/staff-profiles` | UC17–19 (+UC16) | **Đổi tên** từ "Hồ sơ bác sĩ" |
| | Ca làm việc | `/admin/staff-shifts` | UC25–28 | Giữ |
| Vận hành phòng khám | **Lịch hẹn** | `/admin/appointments` | UC20, UC22, UC24 | **Thêm mới** |
| | Phòng khám | `/admin/rooms` | UC64–67 | Giữ; sửa loại phòng (mục 3) |
| | Dịch vụ và giá | `/admin/services` | UC68–70 (+UC71) | Giữ; thêm chỉ số xét nghiệm |
| Nội dung và phản hồi | Đánh giá bệnh nhân | `/admin/reviews` | UC61–62 | Giữ |
| | Nội dung website | `/admin/website-content` | UC72–73 | Giữ |
| | **Thông báo phòng khám** | `/admin/announcements` | UC74 | **Đổi tên** từ "Thông báo" (tránh nhầm với hộp thư cá nhân) |
| Tài chính và báo cáo | Lịch sử thanh toán | `/admin/payments` | UC56 | Giữ |
| | **Khoản chưa thanh toán** | `/admin/unpaid-items` | UC57 | **Đổi tên + route** từ "Hóa đơn chưa thanh toán" |
| | Báo cáo | `/admin/reports` | UC78 | Giữ |
| Hệ thống | Nhật ký hoạt động | `/admin/activity-logs` | UC76 | Giữ tên; route đổi từ `audit-logs` (R3 gọi là Activity Log) |
| Không nằm sidebar | Menu tài khoản (chân sidebar) + chuông trên header | `/admin/account`, `/admin/notifications` | UC03–05, UC63 | Xem `15-personal-area.md` |

## 2. Vì sao đổi / thêm

1. **Hồ sơ bác sĩ → Hồ sơ nhân viên.** UC17–19 áp dụng cho 4 vai trò: Lễ tân, Bác sĩ chỉnh hình, KTV chẩn đoán hình ảnh, KTV xét nghiệm (BR-09). Bác sĩ chỉ là trường hợp có thêm thông tin công khai. R3 gọi màn là *Clinic Staff Profile List/Detail*.
2. **Thêm Lịch hẹn.** R3 giao cho Admin 3 màn mà sidebar chưa có chỗ: Doctor Availability Overview (UC20), Appointment Reassignment (UC22), xem lịch hẹn (UC24). Luồng này bắt buộc để xử lý lỗi `MSG-36` khi sửa/xóa ca hoặc gỡ hồ sơ bác sĩ còn lịch hẹn. Admin **không được hủy** lịch hẹn (BR-22), chỉ chuyển bác sĩ.
3. **Hóa đơn → Khoản chưa thanh toán.** R3 chốt mô hình *phiếu thu*, không có hóa đơn; không thanh toán từng phần (BR-49); UC57 liệt kê phí khám và chỉ định đang *Pending Payment*. Mô tả cũ trong code ("trả một phần, số tiền còn nợ") sai.
4. **Thông báo → Thông báo phòng khám.** R3 có hai khái niệm: bài đăng thông báo cho nhóm người nhận (UC74, màn 82–85) và hộp thư cá nhân của người đăng nhập (UC63, màn 80–81, chuông header).
5. **Nhật ký hoạt động.** R3 20/10 đã đổi *Audit Log* thành *Activity Log*; đổi route cho thống nhất, không ảnh hưởng nghiệp vụ.

## 3. Màn đã code nhưng lệch R3

| Màn | Code hiện tại | R3 yêu cầu |
| --- | --- | --- |
| Tài khoản | Đăng nhập bằng **email**, có SĐT, trạng thái "Chờ kích hoạt" (mời qua email) | Nhân viên đăng nhập bằng **tên đăng nhập + mật khẩu khởi tạo hiện một lần**, không có email (BR-11, BR-07). Chỉ 2 trạng thái Active/Inactive |
| Tài khoản | Nút **"Đổi vai trò"**, ghi lý do khóa | **Cấm đổi vai trò** (BR-08). Không có lý do khóa. Thêm "Cấp lại mật khẩu" cho nhân viên |
| Tài khoản | Thiếu vai trò KTV xét nghiệm; có `admin` trong danh sách vai trò | Admin tạo 4 vai trò (thêm Laboratory Technician); không thấy/không quản lý tài khoản Admin, Owner |
| Phòng | Loại phòng gồm **Thủ thuật** (`procedure`); thiếu xét nghiệm | 3 loại: Khám, Chẩn đoán hình ảnh, **Xét nghiệm**; không có phòng thủ thuật |
| Phòng | Dịch vụ hỗ trợ là danh sách chuỗi cố định (Bó bột, Tiêm khớp…) | Chọn từ danh mục dịch vụ thật, phải khớp loại phòng (BR-57) |
| Hồ sơ bác sĩ | Nút "Xóa hồ sơ bác sĩ" | "Gỡ hồ sơ" (không xóa), chặn khi bác sĩ còn lịch hẹn tương lai (BR-18) |
| Mọi mục | Số UC theo bản cũ (UC57, UC58, UC62…UC79) | R3 đã đánh số lại UC01–UC78, lệch 1 ở các UC từ nhóm hồ sơ nhân viên trở đi. `AGENTS.md` còn ghi "UC01–UC79" |

## 4. Tình trạng sẵn sàng code

| Nhóm | Mục | Ghi chú |
| --- | --- | --- |
| Tự đứng được (làm FE + BE ngay) | Tài khoản, Hồ sơ nhân viên, Phòng, Dịch vụ, Nội dung website, Thông báo phòng khám, Nhật ký, Khu cá nhân | Không phụ thuộc dữ liệu lượt khám/thanh toán |
| Cần Phòng + Tài khoản + Hồ sơ trước | Ca làm việc | **Đã gỡ chặn D7**: R3 chốt mỗi ca có một phòng (BR-24) |
| Cần đặt lịch (chưa có BE) | Lịch hẹn | FE dùng dữ liệu giả trước |
| Cần đánh giá của bệnh nhân | Đánh giá bệnh nhân | FE dữ liệu giả; BE cần seed đánh giá |
| Cần lượt khám, thanh toán | Lịch sử thanh toán, Khoản chưa thanh toán, Bảng điều khiển, Báo cáo | Chỉ đọc; FE dữ liệu giả, BE làm sau khi có visit/payment |

## 5. Việc cần làm ngoài tài liệu này

- Sửa `adminNavigation.ts` theo mục 1 (đổi tên, thêm Lịch hẹn, số UC mới, route).
- Sửa `AGENTS.md`: "UC01–UC79" thành "UC01–UC78".
- `docs/appendix-rules.md` trong repo là bản **17/10, đã cũ** (BR-57 vẫn nói hóa đơn, 71 BR so với 66 BR hiện tại sau gộp ngày 20/10). Các spec trong thư mục này trích BR/MSG theo **R3 Drive hiện tại**; nội dung MSG cần dùng đã chép sẵn vào từng file. Nên đồng bộ lại file appendix.
