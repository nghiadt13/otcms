# Đề xuất hợp đồng API — kỹ thuật viên xét nghiệm

Trạng thái: **đề xuất để FE/BE thống nhất**, chưa được backend triển khai hay phê duyệt. Giao diện `/laboratory` hiện dùng dữ liệu giả, không gọi các API bên dưới. Tên đường dẫn, payload, mã HTTP và phân trang dưới đây là đề xuất; không coi là hợp đồng đang vận hành. Phạm vi bám UC46, UC48, UC52, BR-13, BR-28, BR-30, BR-43, BR-46, BR-47 và các quyết định nghiệp vụ trong AGENTS.md của workspace.

## Nguyên tắc chung

- Base `/api/v1`; bearer access token hiện có, cookie refresh và CSRF cho các request ghi như `apiRequest`. Backend xác thực và kiểm tra vai trò `LAB_TECHNICIAN`, ca, phòng và từng chỉ định; không dựa vào sidebar để phân quyền.
- Chỉ trả thông tin tối thiểu cần thực hiện xét nghiệm: mã và tên người bệnh, tuổi, giới tính, chỉ định, bác sĩ yêu cầu và lý do lâm sàng. Không đưa thông tin mẫu thật vào tài liệu hoặc log.
- Hàng chờ theo thứ tự vào hàng chờ/check-in; không có nhóm ưu tiên theo quyết định mới của dự án, dù BR-29 trong bản R3 local cũ còn mô tả ưu tiên. Xử lý người vắng/quay lại theo quyết định nghiệp vụ và các API hàng chờ đã thống nhất với nhóm sở hữu `visit`; không tự tạo hàng chờ thứ hai.
- Dùng các API dùng chung hiện có cho tài khoản, đổi mật khẩu, thông báo, phòng và lịch khi khả dụng. Trước khi thêm endpoint mới, BE đối chiếu hợp đồng hiện có và xác nhận quyền LAB_TECHNICIAN.
- Lỗi theo cấu trúc lỗi chung của dự án, mang mã R3: MSG-04 (lỗi xử lý), MSG-40 (không có quyền), MSG-41 (không tìm thấy), MSG-42 (trạng thái không hợp lệ), MSG-75 (giá trị không hợp lệ); thành công ghi kết quả MSG-45. HTTP trong bảng là đề xuất, còn envelope cụ thể phải thống nhất với backend hiện tại.

## Chức năng và endpoint đề xuất

| Nhu cầu | Method và đường dẫn đề xuất | Input | Output / hành vi |
| --- | --- | --- | --- |
| Ngữ cảnh phòng/ca hiện tại | `GET /laboratory/me/context` | Phiên nhân viên | Phòng, ca, quyền thao tác; xử lý rõ chưa được phân phòng/ngoài ca. |
| Hàng chờ và tìm kiếm | `GET /laboratory/worklist?query=&cursor=&limit=` | Tìm theo mã chỉ định hoặc tên, phân trang | Các lượt chờ trong phòng của kỹ thuật viên, theo thứ tự hàng chờ; mỗi lượt gồm số hàng, thông tin bệnh nhân tối thiểu và các chỉ định của phòng. Tìm ngoài hàng chờ vẫn giới hạn phòng được phép. |
| Gọi tiếp | `POST /laboratory/worklist/call-next` | Mã phòng/ca nếu cần | Gọi người tiếp theo một cách nguyên tử; trả lượt được gọi, cập nhật hiển thị cho bệnh nhân theo UC46. Không có bệnh nhân: trả trạng thái rỗng được xác định. |
| Đánh dấu vắng | `POST /laboratory/worklist/{queueEntryId}/absent` | Phiên và mã lượt | Chỉ nhân viên đã gọi mới được đánh dấu; trả trạng thái hàng chờ cập nhật. |
| Không thể thực hiện | `POST /laboratory/orders/{orderId}/unable-to-perform` | `{ "reason": "..." }` | Ghi lý do và chuyển thông tin tới bác sĩ theo UC46 AF3; không tự hủy chỉ định. Chi tiết trạng thái và thông báo cần thống nhất. |
| Xem chi tiết chỉ định | `GET /laboratory/orders/{orderId}` | Mã chỉ định | Patient header, mã chỉ định, dịch vụ, lý do lâm sàng, bác sĩ yêu cầu, trạng thái, trạng thái thanh toán và từng chỉ số, đơn vị, khoảng tham chiếu theo giới tính, kết quả đã có nếu được phép. |
| Theo dõi chỉ định | `GET /laboratory/orders?status=&query=&cursor=&limit=` | Lọc theo trạng thái, tìm kiếm | Danh sách chỉ định phù hợp phạm vi phòng và trạng thái Ordered / Results Available / Cancelled; có trạng thái thanh toán. |
| Duyệt kết quả | `POST /laboratory/orders/{orderId}/results/approve` | Xem ví dụ bên dưới | Kiểm tra đủ và hợp lệ từng chỉ số, quyền và trạng thái; lưu kết quả, cờ bất thường do backend tính, người duyệt và thời điểm; bác sĩ yêu cầu xem được ngay; thực hiện điều phối theo BR-30. |
| Sửa kết quả đã duyệt | `POST /laboratory/orders/{orderId}/results/amend` | Các giá trị thay đổi, lý do bắt buộc, phiên bản | Lưu giá trị cũ vào nhật ký; cập nhật cờ, thông báo bác sĩ phụ trách; nếu lượt đã hoàn thành thì tạo phiên bản phiếu mới và thông báo bệnh nhân theo UC48 AF2. |

Các API dashboard có thể dùng số liệu tổng hợp từ backend hoặc endpoint tổng hợp riêng, chỉ theo phạm vi phòng/ca. Không tính số từ một trang kết quả phân trang. Các thao tác trên cần quyền và kiểm tra trạng thái ở server; trạng thái chờ từ client không đủ căn cứ để ghi.

## Dữ liệu kết quả đề xuất

Chi tiết trả về mỗi chỉ số gồm `indicatorId` ổn định, `name`, `unit`, khoảng tham chiếu áp dụng (`lower`, `upper`, hai đầu có bao gồm hay không), giới tính/điều kiện áp dụng, và quy tắc miền giá trị nhập hợp lệ nếu có. **Miền giá trị hợp lệ và khoảng tham chiếu bình thường là hai khái niệm khác nhau**: ngoài khoảng tham chiếu được đánh dấu bất thường, không mặc định là dữ liệu không hợp lệ. Cần thống nhất định dạng số thập phân, đơn vị, giá trị biên và xử lý giới tính chưa có khoảng phù hợp; không suy đoán khoảng tham chiếu từ dữ liệu mẫu.

Ví dụ request duyệt (minh họa, các ID không có thật):

```json
{
  "version": 3,
  "results": [
    { "indicatorId": "indicator-1", "value": "4.8" },
    { "indicatorId": "indicator-2", "value": "7.2" }
  ]
}
```

Đề xuất trả order ID, phiên bản mới, trạng thái, thời điểm/người duyệt và từng kết quả kèm cờ `NORMAL` / `ABNORMAL` (hoặc trạng thái khác được thống nhất). Backend phải tự xác định cờ theo khoảng tham chiếu có hiệu lực, không tin cờ do frontend gửi. Giá trị thiếu hoặc ngoài miền hợp lệ trả MSG-75 gắn `indicatorId` để FE đặt lỗi đúng ô; lỗi xung đột phiên bản hoặc trạng thái trả MSG-42 và thông tin cần thiết để tải lại.

Ví dụ request sửa sau duyệt:

```json
{
  "version": 4,
  "reason": "Điều chỉnh sau khi kiểm tra lại",
  "results": [{ "indicatorId": "indicator-1", "value": "4.6" }]
}
```

## Việc BE và FE cần chốt trước khi tích hợp

1. Quyền theo phòng/ca và lúc nào cho phép xem chỉ định đã duyệt; quy tắc bệnh nhân quay lại sau khi vắng và API hàng chờ dùng chung.
2. Miền giá trị hợp lệ, kiểu chỉ số (số hay văn bản), số thập phân, các khoảng tham chiếu và trường hợp không có khoảng phù hợp.
3. Định dạng lỗi, mã HTTP, phân trang, idempotency khi gọi/duyệt lại, khóa phiên bản khi nhiều người cùng thao tác và cơ chế cập nhật trạng thái tức thời.
4. Chi tiết sự kiện gửi bác sĩ, tạo phiên bản phiếu kết quả và thông báo bệnh nhân; bảo đảm mọi thay đổi kết quả và nhật ký có tính nhất quán.
5. Có cần lưu nháp trên server hay không: UC48 cho phép sửa trước duyệt nhưng không quy định một endpoint lưu nháp; giao diện mẫu chỉ giữ giá trị trong bộ nhớ trang.
6. Những API dùng chung nào đã live và cho phép vai trò xét nghiệm; danh sách phòng/lịch/thông báo/tài khoản không nên sao chép endpoint.

## Kiểm thử nghiệm thu khi làm live

- Tài khoản xét nghiệm khác phòng/khác ca không đọc hoặc duyệt được chỉ định; gọi tiếp đồng thời không gọi trùng; người không gọi không được đánh dấu vắng.
- Hiển thị đúng số đo, đơn vị và khoảng cho bệnh nhân; giá trị ngoài khoảng nhưng hợp lệ được đánh dấu, còn giá trị thiếu/không hợp lệ nhận MSG-75 ngay tại ô.
- Duyệt đầy đủ lưu một lần, bác sĩ xem ngay; thử gửi lại, xung đột phiên bản và lỗi mạng. Trường hợp không thể thực hiện giữ đúng trạng thái và ghi lý do.
- Sửa kết quả đã duyệt phải có lý do, lưu lịch sử, báo bác sĩ và tạo phiên bản phiếu/thông báo bệnh nhân khi lượt đã hoàn thành; kiểm tra thất bại một phần và rollback.
- Kiểm tra tìm kiếm, trạng thái rỗng, hết phiên, lỗi quyền và thao tác bàn phím; kiểm tra không có dữ liệu bệnh nhân trong log.
