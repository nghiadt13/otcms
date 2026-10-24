# Giao diện lễ tân

Nguồn: Report 3 bản (7), mục 1.4.2.3 và các UC chi tiết, do người dùng cung cấp ngày 24/10/2026. Các UC lễ tân chưa có trong docs/requirements; bản report mới được chỉ định làm nguồn cho công việc này. Không tự khôi phục quyền từ report cũ.

Nhánh làm việc: `thien-quang`. Mở `/reception` sau `cd frontend` và `npm.cmd run dev`.

## Phạm vi và ánh xạ

| Màn trong SRS                                                                    | Đường dẫn / thành phần                                                             |
| -------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Account Profile, Update Account Profile, Change Password                         | /reception/account; đổi ảnh và hộp thoại mật khẩu                                  |
| Notifications, Notification Details                                              | /reception/notifications; hộp thoại nội dung                                       |
| Patient Search, Walk-in Patient Profile, Patient Details, Care Link Confirmation | /reception/patients; tìm định danh, tạo/sửa, xác minh tại quầy                     |
| Appointments, Book Appointment, Appointment Details, Doctor Availability         | /reception/appointments; đặt lịch, chi tiết đổi bác sĩ, giờ trống                  |
| Check-In, Reception Queue                                                        | /reception; tiếp nhận và danh sách đã vào hàng đợi                                 |
| Manage Room Queue                                                                | /reception/queues; khám và đọc kết quả, xem phòng dịch vụ, điều chỉnh vị trí/phòng |
| Visit Progress                                                                   | /reception/visits; chi tiết, chuyển bác sĩ phụ trách                               |
| Payment Items, Record Payment                                                    | /reception/payments; tiền mặt, vòng đời VNPay mô phỏng                             |
| Payment History, Receipt                                                         | /reception/payment-history; biên lai và in/lưu PDF                                 |
| Service Order Status                                                             | /reception/orders                                                                  |
| Examination Result Sheet                                                         | /reception/results; chỉ đọc và in phiếu phát hành                                  |
| Rooms & Availability, Service Catalog Lookup, My Work Schedule                   | /reception/rooms, /services, /schedule; chỉ xem                                    |

Giữ đúng 26 màn theo mục 1.4.2.3; màn con phù hợp được đặt trong dialog, không tạo 26 mục menu.

## Quy tắc được kiểm tra

- UC10/11: đối chiếu điện thoại + ngày sinh, thêm tên khi nhiều kết quả; kiểm tra trùng định danh. Hồ sơ mới không tạo tài khoản. Dữ liệu lễ tân không chứa tiền sử/dị ứng.
- UC13 AF2: chỉ xác minh yêu cầu liên kết có sẵn khi hồ sơ thiếu email hoặc định danh; yêu cầu xác nhận và căn cứ giấy tờ.
- UC20/21/22/24: giờ trống từ ca 20 phút mẫu; bác sĩ tùy chọn, kiểm tra tranh chấp khung giờ. Không đổi giờ hoặc hủy lịch bằng vai trò lễ tân. Bác sĩ vắng được đổi đúng giờ; nếu không có người thay thì giữ lịch và tạo thông báo mô phỏng.
- UC29: có mặt và trả đủ phí trước tiếp nhận; chặn trùng lượt. Khách trực tiếp có khoản phí trước khi tạo lượt, không tự tạo lịch hẹn. Lượt chờ kết luận trong ngày 1–7 tiếp tục cùng lượt, không thu lại phí khám; ngày 8 hết hạn.
- UC30–34: hàng chờ theo ưu tiên cấu hình và thời điểm vào; đổi thủ công phải ghi lý do và lịch sử. Trạng thái đang gọi là calledAt trên entry Waiting, không thêm trạng thái ngoài SRS. Chỉ lễ tân đã gọi được ghi vắng/quay lại cùng ngày. Chặn gọi thêm khi phòng bận hoặc bệnh nhân đang ở phòng khác. Chỉ kỹ thuật viên gọi phòng dịch vụ.
- UC31: chuyển phòng dịch vụ chỉ sang phòng hỗ trợ đủ dịch vụ đang chờ. Chuyển phòng khám cùng với chuyển trách nhiệm cho bác sĩ trực khi bác sĩ cũ vắng.
- UC53/54/56/57/58: một giao dịch cho các khoản cùng người khám và cùng lịch/lượt, trả trọn từng khoản; giá cố định theo khoản thu; chống thu trùng, xác nhận tiền mặt và tính tiền thừa; một biên lai bất biến cho một payment.
- VNPay mô phỏng: đang chờ không đánh dấu Paid; callback thành công idempotent; hết hạn/chưa rõ kết quả khóa thu lại đến khi đối soát. Callback và kết quả chỉ là adapter kiểm thử, không giao dịch tiền thật.
- Sau thu phí dịch vụ: một entry cho một bệnh nhân tại một phòng, dùng phòng phù hợp ít chờ; không tự cho bệnh nhân quay về bác sĩ ngay khi chỉ một dịch vụ xong.
- Lễ tân không đóng lượt, không sửa kết quả, không ra chỉ định, không kê đơn, không sửa ca/phòng/danh mục, không xem tiền sử hoặc dị ứng.

## Chênh lệch nội dung cần lưu ý

Screen description 1.4.2.3 dòng 2 ghi sửa contact và avatar, nhưng UC04 chi tiết chỉ cho staff đổi avatar; thông tin nhân viên do Administrator quản lý. UI theo UC04: contact chỉ đọc. Một lần quay lại khi chỉ còn chỉ định chưa trả phí dẫn tới thu phí dịch vụ, không tự cấp lượt mới.

## Kiến trúc và dữ liệu thử

Root layout `(staff)` dùng cùng token Teal/Coral, Be Vietnam Pro, shadcn base-nova, PageHeader và các component dùng chung. Routes mỏng. `patient-reception` sở hữu prototype quy trình tiếp nhận; không import feature khác. Không ảnh hưởng kho mock admin.

`api/receptionApi.ts` là adapter mock nhất quán dùng chung giữa các màn. Commands kiểm tra version, clone dữ liệu rồi commit một lần; lỗi không để lại khoản thu hoặc hàng đợi dở dang. Provider giữ phiên dữ liệu qua navigation; hard reload đặt lại dữ liệu. Không lưu dữ liệu bệnh nhân hoặc mật khẩu vào localStorage. Không có gọi ghi Supabase, email, VNPay hay backend thật.

Đồng hồ mẫu lấy ngày hiện tại tại Việt Nam, cố định 09:10 để review ngoài giờ làm việc. Priority cấu hình mẫu: cấp cứu trước, các nhóm ưu tiên còn lại ngang nhau, sau cùng thông thường. Cần thay bằng cấu hình phòng khám thực tế khi nối API.

Theo mẫu admin hiện có, bản UI chưa có xác thực vai trò thật. Layout chặn bản production mặc định; chỉ `NEXT_PUBLIC_RECEPTION_DEMO=true` mới cho mở demo khi build. Không bật chế độ này cho vận hành. Trước khi production, thay adapter bằng API Spring có kiểm tra role, record scope, transaction, idempotency, thời gian máy chủ, xác minh chữ ký callback và lưu lịch sử bền vững. Không dùng query role hoặc Supabase user metadata tự khai để cấp quyền.

Form sử dụng controlled React state và validator TypeScript thuần trong `schema/receptionRules.ts`, cùng phương pháp hiện có ở RoomFormDialog. Chưa thêm thư viện form thứ hai. Tiền và trạng thái được kiểm tra lại ở adapter; validator frontend không thay thế backend.

## Kịch bản xem thử

1. Hồ sơ: tìm `0900000001`, ngày sinh `1992-04-15`; sửa thông tin hành chính. Tạo trùng họ tên + điện thoại + ngày sinh phải bị chặn.
2. Tiếp nhận Nguyễn Minh Anh: chưa thu phí -> mở Thu phí -> chọn phí LH001 -> tiền mặt đủ -> xác nhận -> biên lai -> quay lại Tiếp nhận -> xác nhận có mặt -> vào hàng đợi.
3. Trần Văn Bình đã thanh toán online: tiếp nhận ngay khi có mặt. Lê Thị Hạnh: BS. Lan vắng, đổi người khác cùng 10:00 trước tiếp nhận.
4. Phòng 101: gọi Gia Bảo -> ghi vắng -> gọi Long; không gọi người thứ hai khi còn người đang được gọi/đang khám; bệnh nhân vắng có thể quay lại cùng ngày.
5. Đỗ Thu Hà đang chờ kết luận: thu X-quang và xét nghiệm; tự vào đúng hai phòng, không thêm phí khám. Thử QR đang chờ, không rõ kết quả, rồi xác nhận từ cổng mô phỏng; không cho thu tiền mặt trùng.
6. Hàng đợi P.102 đọc kết quả: Vũ Văn Nam đã đủ kết quả của đợt; xem tiến trình không lộ nội dung y khoa.
7. Phiếu kết quả của Bùi Thanh Mai: xem/in, không có thao tác sửa. Biên lai luôn giữ tên bệnh nhân lúc phát hành kể cả khi hồ sơ thay đổi sau đó.
8. Quan hệ chăm sóc Nguyễn An Nhiên: tìm `0900000009`, ngày sinh `2018-01-05`, mở hồ sơ -> xác minh giấy tờ; không tự liên kết bằng tên nhập tự do.
9. Cá nhân: xem ca của Thiên Quang, đọc thông báo, tải ảnh hợp lệ, thử đổi mật khẩu mẫu từ `Demo12345`.

## Kết quả kiểm tra ngày 24/10/2026

- `npm run lint`: đạt, bao gồm rào chắn import giữa các feature.
- `npm test`: lượt chạy toàn bộ cuối cùng **561/561 đạt**; trong đó **39 kiểm thử mới** của phần lễ tân (33 nghiệp vụ, 6 component qua adapter thật của bản mock).
- `npm run build`: đạt, bao gồm kiểm tra TypeScript và sinh các route.
- Chrome: mở toàn bộ 14 trang ở 1440px và 375px; không tràn ngang toàn trang, không có lỗi JavaScript/console trong lượt cuối. Bảng rộng cuộn bên trong khung.
- Kiểm thử trình duyệt xuyên màn: tạo hồ sơ → đặt lịch; thay bác sĩ vắng giữ nguyên giờ; tiền mặt → biên lai → check-in; gọi → vắng → quay lại → vào phòng; QR chưa rõ kết quả khóa thu lại → xác nhận thành công → đưa vào phòng dịch vụ → chuyển phòng tương thích; đổi mật khẩu sai/đúng; điều hướng bằng menu mobile và đóng dialog bằng Escape.
- Bản in: biên lai và phiếu kết quả nằm đúng trang, không kèm menu, không bị cắt bởi dialog; xuất PDF A4 từ Chrome.
- Có ghi nhận test có sẵn `rooms/components/RoomManagement.test.tsx` (“409 phòng đang dùng: Switch giữ giá trị cũ”) thất bại gián đoạn ở các lượt trước: assertion chạy khi danh sách còn tải lại. Lượt cuối test này đạt. Không thay đổi feature hoặc test phòng để che lỗi.

Để chạy riêng kiểm thử mới: `npm.cmd test -- src/features/patient-reception`.

## Mở giao diện trên máy

```powershell
cd E:\SEP490\otcms\frontend
npm.cmd run dev
```

Mở `http://localhost:3000/reception`. Bản UI mẫu không cần chạy backend; `.env.local` và cấu hình Supabase hiện có được giữ nguyên. Có thêm đường dẫn từ trang `/workspace` sau đăng nhập. Đổi mật khẩu trong khu vực lễ tân chỉ tác động tài khoản mẫu, không đổi tài khoản Supabase.

Không dùng `npm audit fix --force` cho phần thay đổi này. Không thêm dependency hoặc đổi package-lock. Mã nguồn được quản lý trên nhánh `thien-quang`; việc push lên GitHub thực hiện riêng theo yêu cầu của người phụ trách nhánh.
