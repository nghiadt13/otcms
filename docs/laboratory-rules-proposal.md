# Quy tắc xét nghiệm máu gửi đơn vị ngoài

**Trạng thái:** Nội dung BR-LAB-01–14, MSG-LAB-01–14 và ba phương án xử lý ở cuối tài liệu đã được người yêu cầu duyệt; các mã vẫn là mã tạm, chưa được nhóm gán mã chính thức hoặc đồng bộ vào tài liệu UC. Tài liệu mô tả yêu cầu, không khẳng định chức năng đã được triển khai.

**Phạm vi:** Bác sĩ chỉ định CBC/CRP; bệnh nhân thanh toán; lễ tân chuyển đến phòng xét nghiệm; kỹ thuật viên xét nghiệm xác minh bệnh nhân, lấy mẫu và gửi đến đơn vị đối tác; kỹ thuật viên nhập và đối chiếu kết quả từ phiếu gốc trước khi gửi bác sĩ. Giai đoạn hiện tại một kỹ thuật viên được tự nhập và tự đối chiếu; đây không phải đối chiếu độc lập. Người phụ trách chuyên môn cần xác nhận quy trình trước khi áp dụng cho bệnh nhân thật.

## Quy tắc nghiệp vụ

**Bổ sung đã được người dùng xác nhận:** Khi nhập kết quả, kỹ thuật viên phải chọn trạng thái **Bình thường** hoặc **Bất thường** cho từng chỉ số. Trạng thái được lưu cùng chỉ số trong bản nháp và gửi cho bác sĩ khi phát hành kết quả; không tự suy ra trạng thái từ giá trị và khoảng tham chiếu. Không cho phát hành nếu còn chỉ số chưa chọn trạng thái. Cờ kết quả nguy cấp của cả phiếu là một thông tin riêng.

**Hiển thị cho bệnh nhân:** Khi kết quả xét nghiệm đã được kỹ thuật viên phát hành, bệnh nhân có quyền với lượt khám đó được xem từng chỉ số, trạng thái đã chọn và tải phiếu gốc kèm theo. Việc bác sĩ đóng lượt khám không tự phát hành kết quả xét nghiệm; trang bệnh nhân chỉ hiện dữ liệu đã phát hành.

| Mã tạm | Quy tắc |
| --- | --- |
| BR-LAB-01 | Bác sĩ phụ trách lượt khám được tạo chỉ định xét nghiệm CBC hoặc CRP cho bệnh nhân của lượt khám đó. |
| BR-LAB-02 | Lễ tân chỉ được gửi chỉ định đến phòng xét nghiệm khi chỉ định còn hiệu lực và đã thanh toán. |
| BR-LAB-03 | Phòng được chọn phải đang hoạt động và hỗ trợ dịch vụ xét nghiệm được chỉ định. |
| BR-LAB-04 | Chỉ kỹ thuật viên xét nghiệm được phân công vào phòng và ca tương ứng mới được tiếp nhận và xử lý chỉ định của phòng đó. |
| BR-LAB-05 | Trước khi ghi nhận lấy mẫu, kỹ thuật viên phải xác minh danh tính bệnh nhân theo quy trình phòng khám. Hệ thống ghi nhận người xác minh và thời gian. |
| BR-LAB-06 | Khi ghi nhận gửi mẫu, kỹ thuật viên phải nhập mã mẫu, đơn vị xét nghiệm đối tác, thời gian lấy mẫu và thời gian gửi mẫu. Thời gian gửi không được trước thời gian lấy mẫu. |
| BR-LAB-07 | Nếu mẫu không đạt hoặc thông tin bệnh nhân/mẫu không khớp, kỹ thuật viên có thể ghi nhận từ chối mẫu và lý do; chỉ định không được gửi kết quả cho bác sĩ cho đến khi vấn đề được xử lý theo quy trình đã duyệt. |
| BR-LAB-08 | Kết quả được nhập từ phiếu do đơn vị đối tác phát hành. Mỗi chỉ số phải thể hiện giá trị, đơn vị và khoảng tham chiếu theo phiếu; trường hợp phiếu không có đơn vị hoặc khoảng tham chiếu cần có cách ghi nhận phù hợp. |
| BR-LAB-09 | Trước khi gửi bác sĩ, kỹ thuật viên phải đính kèm phiếu gốc, đối chiếu bệnh nhân, mã mẫu, loại xét nghiệm và các chỉ số với phiếu, rồi xác nhận. Giai đoạn hiện tại cho phép cùng một kỹ thuật viên nhập và tự đối chiếu; hệ thống lưu riêng người và thời gian của từng bước. |
| BR-LAB-10 | Bác sĩ phụ trách chỉ xem kết quả đã được xác nhận và gửi. Quyền xem phiếu gốc và lịch sử kết quả phải được kiểm tra ở backend theo vai trò và lượt khám. |
| BR-LAB-11 | Kết quả đã gửi không bị sửa đè. Khi phát hiện sai, người có quyền đánh dấu cần xem lại; bác sĩ nhìn thấy cảnh báo và được thông báo. |
| BR-LAB-12 | Kỹ thuật viên tạo phiên bản điều chỉnh, ghi lý do và nguồn điều chỉnh; đính kèm phiếu mới nếu đối tác phát hành lại. Phiên bản điều chỉnh phải được đối chiếu và xác nhận trước khi gửi bác sĩ. |
| BR-LAB-13 | Hệ thống giữ phiên bản cũ, phiên bản mới, phiếu liên quan, người thực hiện, thời gian và lý do điều chỉnh; ghi nhận thông báo và việc bác sĩ đã xem. |
| BR-LAB-14 | Với kết quả nguy cấp hoặc sai sót cần xử lý khẩn, nhân viên liên hệ bác sĩ theo quy trình chuyên môn của phòng khám và ghi nhận việc liên hệ; không chỉ dựa vào thông báo trên hệ thống. |

## Thông báo ứng dụng

| Mã tạm | Tình huống | Nội dung |
| --- | --- | --- |
| MSG-LAB-01 | Chưa thanh toán | Chỉ định chưa được thanh toán. Không thể gửi đến phòng xét nghiệm. |
| MSG-LAB-02 | Phòng không phù hợp hoặc không hoạt động | Phòng xét nghiệm không khả dụng hoặc không hỗ trợ dịch vụ này. |
| MSG-LAB-03 | Không có quyền hoặc không đúng phòng/ca | Bạn không có quyền thực hiện thao tác trên chỉ định này. |
| MSG-LAB-04 | Chưa xác minh bệnh nhân | Vui lòng xác minh bệnh nhân trước khi ghi nhận lấy mẫu. |
| MSG-LAB-05 | Thiếu hoặc sai thông tin gửi mẫu | Vui lòng kiểm tra mã mẫu, đơn vị xét nghiệm và thời gian lấy/gửi mẫu. |
| MSG-LAB-06 | Từ chối mẫu chưa có lý do | Vui lòng nhập lý do từ chối mẫu. |
| MSG-LAB-07 | Thiếu thông tin kết quả | Vui lòng kiểm tra các chỉ số và thông tin trên phiếu kết quả. |
| MSG-LAB-08 | Chưa đính kèm phiếu gốc | Vui lòng đính kèm phiếu kết quả của đơn vị xét nghiệm. |
| MSG-LAB-09 | Chưa xác nhận đối chiếu | Vui lòng đối chiếu và xác nhận kết quả trước khi gửi bác sĩ. |
| MSG-LAB-10 | Gửi kết quả thành công | Đã gửi kết quả xét nghiệm cho bác sĩ. |
| MSG-LAB-11 | Kết quả đang xem lại | Kết quả này đang được xem lại. Vui lòng kiểm tra thông báo điều chỉnh. |
| MSG-LAB-12 | Thiếu lý do điều chỉnh | Vui lòng nhập lý do và nguồn điều chỉnh kết quả. |
| MSG-LAB-13 | Gửi phiên bản điều chỉnh | Kết quả xét nghiệm đã được điều chỉnh. Vui lòng xem phiên bản mới. |
| MSG-LAB-14 | Trạng thái đã thay đổi | Chỉ định đã được cập nhật. Vui lòng tải lại và kiểm tra trước khi tiếp tục. |

## Phương án xử lý đã được người yêu cầu duyệt

### Mẫu bị từ chối

Ghi nhận mẫu, lý do, nguồn thông báo, người và thời gian từ chối; thông báo bác sĩ phụ trách. Giữ chỉ định chờ xử lý và không phát hành kết quả từ mẫu bị từ chối. Bác sĩ và phòng xét nghiệm quyết định lấy lại hay kết thúc theo quy trình chuyên môn; nếu lấy lại, tạo lần lấy mẫu mới với mã mới và giữ lịch sử mẫu cũ. Nếu lỗi thuộc quy trình của phòng khám, phòng khám chịu trách nhiệm chi phí; nếu lỗi thuộc đơn vị đối tác, đối tác chịu trách nhiệm chi phí. Việc xác định trách nhiệm khi nguyên nhân chưa rõ hoặc các bên chưa thống nhất cần nhóm và đối tác xác nhận; không tự quy lỗi hoặc áp dụng hoàn tiền. Phòng khám và đối tác thống nhất cách bảo quản, trả lại hoặc tiêu hủy mẫu theo quy trình chuyên môn.

Khi chỉ định CBC và CRP cùng lúc, mỗi chỉ định có mã mẫu riêng; mỗi lần lấy lại dùng mã mới.

Tham khảo: [WHO — xử lý mẫu và từ chối mẫu](https://extranet.who.int/lqsi/sites/default/files/attachedfiles/LQMS%205-4%20Sample%20Processing.pdf).

### Phiếu thiếu đơn vị hoặc khoảng tham chiếu

Không tự suy ra hoặc tự điền giá trị từ danh mục. Ghi rõ trường nào không có trên phiếu, giữ phiếu gốc và liên hệ đối tác để xác minh hoặc cấp phiếu bổ sung. Cho phép lưu bản nháp; tạm dừng phát hành khi thông tin cần thiết để hiểu kết quả còn thiếu. Trường hợp chỉ số định tính hoặc không áp dụng đơn vị/khoảng tham chiếu, người có thẩm quyền xác nhận theo loại xét nghiệm và ghi nhận lý do cùng nguồn xác nhận. Nhóm chuyên môn cần xác định điều kiện cụ thể cho phép phát hành.

### Thông báo kết quả nguy cấp

Nhận biết theo cảnh báo của đối tác hoặc ngưỡng đã được người có thẩm quyền phê duyệt; không tự đặt ngưỡng CBC/CRP. Kỹ thuật viên liên hệ trực tiếp bác sĩ phụ trách theo quy trình phòng khám, đồng thời ghi nhận thời gian, người liên hệ, người nhận và tình trạng liên hệ. Nếu chưa liên hệ được, tiếp tục theo quy trình liên hệ lại/chuyển cấp do phòng khám phê duyệt; thông báo trên hệ thống hỗ trợ theo dõi nhưng không thay thế liên hệ trực tiếp. Lưu bằng chứng bác sĩ xác nhận tiếp nhận và phát hành phiếu chính thức theo quy trình chuyên môn.

Tham khảo: [WHO — thông báo khẩn và kết quả nguy cấp](https://extranet.who.int/lqsi/node/144).
