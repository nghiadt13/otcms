# Chủ phòng khám: quản trị tài khoản Admin (UC07–UC09)

Route `/owner/accounts`, khu riêng `(owner)`, khung `owner-shell`, nghiệp vụ `owner-administrators`. Dùng theme và component chung với Admin. `/owner` và điều hướng OWNER sau đăng nhập dẫn tới danh sách này.

- UC07: tạo Admin bằng tên đăng nhập; vai trò cố định, mật khẩu tạm do backend cấp.
- UC08: xác nhận kích hoạt/vô hiệu hóa; xác nhận cấp lại mật khẩu. Không xóa tài khoản, không sửa vai trò.
- UC09: tìm theo tên đăng nhập, lọc trạng thái, phân trang server và xem chi tiết.
- Gọi API thật `/api/v1/owner/administrator-accounts` qua client chung (JWT/CSRF). Không có fallback dữ liệu giả trong sản phẩm.
- Mật khẩu tạm chỉ tồn tại trong state của dialog, không lưu storage/log. Đóng dialog gỡ state; Admin phải đổi mật khẩu theo chính sách backend.
- Guard frontend kiểm tra OWNER trước khi gắn màn hình; backend vẫn chịu trách nhiệm kiểm tra vai trò, mạng phòng khám và quyền thao tác.

## Nguồn Specification và phạm vi

Bản trích nội dung: [srs4-uc07-09-extract.md](srs4-uc07-09-extract.md).

Nguồn chuẩn của đợt sửa: `FA26_SEP490_G58_Report3_Software_Requirement_Specification (4).docx`, mục 2.2.1–2.2.3 và BR/MSG được tham chiếu trong chính tài liệu đó. Phạm vi vẫn là Chủ phòng khám quản lý Admin, theo yêu cầu ban đầu.

- UC07 AF1: chỉ Administrator, định danh username duy nhất; cấp mật khẩu khởi tạo một lần, yêu cầu đổi khi đăng nhập đầu, bàn giao trực tiếp. AF1 không ghi rõ chủ phòng khám tự nhập hay hệ thống sinh mật khẩu; giữ cơ chế backend sinh theo lựa chọn trước đó của người dùng. N1 yêu cầu Admin tự nhập mật khẩu khi tạo **Clinic Staff**, nằm ngoài màn Owner này.
- UC08 AF1/AF2: mở chi tiết, xem trạng thái và thao tác kích hoạt/vô hiệu hóa/cấp lại mật khẩu; không xóa tài khoản hay dữ liệu liên quan. Chủ phòng khám không tự cấp lại mật khẩu cho chính mình.
- UC09 AF1: danh bạ chỉ có Admin, tìm theo username, hiển thị loại tài khoản/trạng thái và chi tiết; đủ trạng thái trống/lỗi. Không thêm chức năng sửa hồ sơ nhân viên hoặc dữ liệu y khoa.
- BR-07 trong file (4) cho phép hiển thị mật khẩu khởi tạo một lần. Không còn coi đây là ngoại lệ đối với SRS mới. Nội dung appendix trong repo đang khác file (4); không dùng appendix cũ cho ba màn này.
- Thông báo bám MSG-11/12/13/14/15/16/42/54 của file (4); lỗi nhập liệu dưới trường, thông báo thao tác qua toast.

## Giới hạn tích hợp backend

Frontend không sửa backend hoặc tạo tài khoản thật trong kiểm thử. Backend hiện chưa trả `allowedActions`; frontend hiển thị thao tác theo trạng thái hiện tại, quyền thực phải do backend kiểm tra. Service activate/deactivate hiện gán trực tiếp trạng thái, chưa thể hiện đầy đủ kiểm tra chuyển trạng thái và trả MSG-42 như UC08 E2. Frontend đã xử lý phản hồi 409 khi backend bổ sung kiểm tra này.

Chính sách mật khẩu backend hiện có cũng cần nhóm đối chiếu BR-04 của SRS mới (ít nhất 8 ký tự, cả chữ và số); frontend Owner không tự thay chính sách Authentication Service.

## Kiểm chứng

- 25 test trong 4 file đạt: thao tác qua API boundary, xác nhận trước khi đổi trạng thái/cấp lại mật khẩu, xử lý trùng tên, bộ lọc, lỗi tải danh sách, quyền OWNER và điều hướng theo vai trò.
- Production build thành công, gồm `/owner` và `/owner/accounts`.
- Chrome kiểm tra trên bản production với HTTP giả lập: tạo Admin, mật khẩu một lần (Escape không làm mất bước kết quả), vô hiệu hóa, cấp lại mật khẩu, tìm kiếm rỗng, mobile 390px không tràn ngang trang; không có lỗi JavaScript.
- Chưa kiểm thử đầu cuối với backend/database đang triển khai. Cần phiên đăng nhập OWNER và backend hoạt động để dùng dữ liệu thật.
