# Giao diện kỹ thuật viên chẩn đoán hình ảnh

Phạm vi theo SRS bản (3), mục 1.4.2.6 và các Specification UC01, UC03–UC05, UC25, UC34, UC46, UC47, UC52, UC63, UC64. Yêu cầu mới của người dùng được dùng thay cho danh sách UC cũ trong repository. Đây là bản frontend độc lập với database và backend.

Tham chiếu giao diện hiện có: bộ layout `screen-design/deliverables/layouts.json` ở thư mục cha của repository. Dùng sidebar trắng, màu xanh biển `#2563EB`, màu nhấn `#1E3A8A`, nền `#F4F7F8`, bảng dữ liệu và thẻ nội dung. Repository chưa có màn admin được triển khai để tái sử dụng trực tiếp.

## Mở giao diện

Chạy `npm run dev` trong `frontend`, mở `/imaging`. Có thể vào trực tiếp để xem thử, không cần đăng nhập Supabase hoặc chạy Spring Boot. `/imaging/login` là màn đăng nhập nhân viên minh họa, tách với đăng nhập bệnh nhân hiện có.

- Tài khoản mẫu: `ktv.minhanh` / `Demo1234`.
- Luồng mật khẩu được cấp: `ktv.moi` / `Demo1234`. Phải hoàn tất màn đổi mật khẩu minh họa trước khi mở các chức năng khác.
- Đổi mật khẩu chỉ mô phỏng thao tác: không lưu hoặc thay đổi mật khẩu thật, không gọi Authentication Service.
- Dữ liệu giả được lưu dưới khóa `otcms-imaging-preview-v1` trong localStorage. Có nút khôi phục dữ liệu mẫu ở chân trang. File DICOM không được gửi đi hoặc lưu vào localStorage.

## Danh sách màn hình

| Màn hình                            | URL                                   |
| ----------------------------------- | ------------------------------------- |
| Staff Login                         | `/imaging/login`                      |
| Technician Dashboard                | `/imaging`                            |
| Service Worklist Imaging            | `/imaging/worklist`                   |
| Imaging Orders                      | `/imaging/orders`                     |
| Order Details                       | `/imaging/orders/CDHA-001`            |
| DICOM Upload Validation and Storage | `/imaging/orders/CDHA-001/upload`     |
| Validation Result                   | `/imaging/orders/CDHA-001/validation` |
| Service Order Status                | `/imaging/status`                     |
| Multi Room Visit Progress           | `/imaging/visits/LK-001`              |
| Rooms List                          | `/imaging/rooms`                      |
| Work Schedule                       | `/imaging/schedule`                   |
| Notifications                       | `/imaging/notifications`              |
| Notification Details                | `/imaging/notifications/TB-001`       |
| Account Profile                     | `/imaging/profile`                    |
| Update Account Profile              | `/imaging/profile/edit`               |
| Change Password                     | `/imaging/change-password`            |

Các URL có mã bản ghi hỗ trợ dữ liệu mẫu tương ứng; bản ghi ngoài phạm vi phòng hoặc không tồn tại có trạng thái không khả dụng. `/imaging/visits` là đường dẫn mở mặc định của cùng màn tiến trình.

Giao diện desktop được thu gọn cho khung laptop 1366 × 768: bỏ lời giới thiệu lặp lại, gộp thông tin đang phục vụ vào thẻ phòng, dùng tab cho chi tiết chỉ định và tiến trình lượt khám. Danh sách dài cuộn trong khung, hướng dẫn phụ mở theo nhu cầu; điều hướng quay lại nằm ở breadcrumb trên cùng. Mobile vẫn bố trí theo cột để giữ nội dung đọc được.

## Các tình huống có thể thao tác

- Worklist giới hạn phòng XR-01, gom nhiều chỉ định của cùng bệnh nhân thành một lượt hàng đợi. Xếp theo nhóm ưu tiên rồi thời điểm vào hàng đợi; không có thao tác đổi thứ tự thủ công. Chỉ bệnh nhân có chỉ định đã thanh toán được thực hiện.
- Gọi tiếp theo, đánh dấu vắng, trả lại hàng đợi trong ngày bởi người đã gọi. Có dữ liệu mẫu không được phép trả lại để thể hiện giới hạn này.
- Tra cứu chỉ định chưa thanh toán và đã hủy nhưng chặn upload/thực hiện. Chỉ định của phòng khác bị chặn khi mở trực tiếp.
- Ghi nhận chống chỉ định, từ chối, thiết bị chưa sẵn sàng hoặc cần quay lại; giữ nguyên chỉ định và thanh toán. Kỹ thuật viên không tự hủy chỉ định. Hàng đợi của lần phục vụ hiện tại được đóng; lần trở lại cần lễ tân check-in khi có backend.
- Chọn nhiều file `.dcm`, kéo thả, bỏ file, chia thành nhiều đợt chọn tối đa 500 MB/đợt. Có bộ thông tin mẫu để kiểm tra luồng mà không cần file DICOM thật.
- Đối chiếu mã bệnh nhân và chỉ định, Study Instance UID, số series, xác nhận và lý do thay thế. Màn kết quả thể hiện cả lỗi, sẵn sàng gửi và đã gửi.
- Gửi study cập nhật chỉ định thành Results Available. LK-001 còn kết quả xét nghiệm đang chờ nên chưa vào hàng đợi đọc kết quả; LK-002 đủ kết quả khi gửi study. Chỉ xét các chỉ định không hủy của đợt mới nhất.
- Thay thế study CDHA-005 yêu cầu lý do, giữ phiên bản trước, tăng phiên bản phiếu kết quả của lượt khám đã Completed và ghi nhận sự kiện thông báo minh họa. Không tạo PDF thật hay gửi thông báo tới tài khoản khác.
- Tiến trình hiển thị giai đoạn, phòng, số thứ tự và trạng thái dịch vụ; không hiển thị nội dung kết quả lâm sàng.
- Thông báo cá nhân hỗ trợ lọc, đọc chi tiết và đánh dấu đã đọc; phòng hỗ trợ tìm/lọc và chi tiết chỉ đọc; lịch hỗ trợ chọn tuần/ngày và kiểu tuần/danh sách; tài khoản chỉ cho đổi avatar.

## Giới hạn tích hợp

Kiểm tra DICOM ở frontend chỉ kiểm tra đuôi file, tiền tố DICM và các trường đối chiếu do người dùng nhập. Chưa đọc đầy đủ DICOM tags, không xác thực metadata từ study thật, không kiểm tra malware hoặc lưu Orthanc. Đăng nhập, phân quyền bản ghi, thanh toán, nhận ca, kết quả xét nghiệm, thông báo, timeout 7 ngày và phiên bản PDF hiện chỉ là dữ liệu/trạng thái mô phỏng. Các ràng buộc này phải được thực thi lại tại backend khi tích hợp.

UC49 View DICOM Images và UC50 Record Imaging Findings thuộc Orthopedic Doctor trong SRS này. Không thêm chức năng ghi nhận định hoặc xem hồ sơ bệnh án đầy đủ cho kỹ thuật viên.
