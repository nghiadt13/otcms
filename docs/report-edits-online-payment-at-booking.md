# Ghi chú sửa báo cáo R3: thanh toán online ngay lúc đặt lịch

Ngày: 04/11/2026. Căn cứ: thống nhất với thành viên BE, cho bệnh nhân trả phí khám online ngay trong luồng đặt lịch để khỏi phải đến phòng khám mới trả.
Tra theo bản local `SRS/report3-md/` (Drive là bản chuẩn, đối chiếu lại khi sửa).

## 1. Luồng code hiện tại (để viết cho khớp)

Wizard đặt lịch khu bệnh nhân có **5 bước**: Tài khoản → Người khám → Dịch vụ và lịch → Xác nhận → **Thanh toán**.

1. Bước Xác nhận, bấm "Đặt lịch khám": `POST /api/v1/appointments`. BE tạo lịch trạng thái `CONFIRMED`, giá khám chốt lúc đặt.
2. Sang bước 5: `POST /api/v1/appointments/{id}/invoices` tạo hóa đơn, rồi `POST /api/v1/invoices/{id}/payment-attempts` (header `Idempotency-Key`) tạo mã PayOS. FE hiện QR + link thanh toán, đếm ngược hết hạn, tự hỏi trạng thái mỗi 5 giây.
3. Kết thúc bước 5 theo một trong hai cách:
   - PayOS xác nhận thành công, bệnh nhân bấm "Hoàn tất".
   - Bệnh nhân bấm "Thanh toán sau tại quầy" (vẫn được, lịch giữ nguyên, phí còn Pending Payment).
4. Toast MSG-26 "Đã đặt lịch khám thành công" chỉ hiện ở cuối bước 5, rồi sang màn kết quả.
5. Sang bước 5 thì các bước trước bị khóa (tránh đặt trùng).

Lịch **không bị tự hủy** nếu chưa trả tiền; chưa có BR giữ chỗ. (Xem mục 4.)

## 2. Chỗ báo cáo cần sửa

| # | Vị trí | Hiện tại | Cần sửa |
| --- | --- | --- | --- |
| 1 | Cổng thanh toán, toàn bộ báo cáo (37 chỗ nhắc VNPay: UC54, UC55, NF-06, BR-51, tên UC-55, Secondary Actor) | VNPay | BE đang tích hợp **PayOS** (`billing/integration/payos`). Đổi tên cổng cho khớp, hoặc xác nhận lại với nhóm cổng nào là thật |
| 2 | UC21 Book Appointment (§2.5.2) | Kết thúc ở bước đặt thành công, MSG-26 | Thêm bước sau khi đặt: hệ thống chuyển sang thanh toán online phí khám (gọi UC55 hoặc nhúng luồng), AF "Trả sau tại quầy". Dời hiển thị MSG-26 về cuối luồng |
| 3 | UC55 Pay Online (§2.12.3), Normal Flow bước 1 | Bệnh nhân mở Payments (UC53) rồi chọn mục chờ thanh toán | Thêm điểm vào thứ hai: từ UC21 ngay sau khi đặt lịch, một lịch hẹn, tự chọn phí khám |
| 4 | UC55, Precondition | Phí khám của lịch đã đặt đang Pending Payment | Giữ nguyên (lịch đã tạo trước khi trả); ghi thêm "ngay sau khi đặt lịch" nếu muốn |
| 5 | UC29 Check In (§2.7.1) | Yêu cầu phí khám đã trả mới check-in | Giữ nguyên. Ghi chú: phần lớn lịch đã trả online nên lễ tân chỉ xác nhận, ai chọn trả sau thì thu tại quầy theo UC54 |
| 6 | UC54 Record Payment at Clinic | Thu tại quầy là cách chính | Hạ thành phương án thay thế khi bệnh nhân không trả online lúc đặt |
| 7 | NF-01 (tính số tiền) | "booking an examination fee (UC21)" kích hoạt tính tiền | Giữ, nhưng khớp với việc UC21 gọi sang thanh toán |
| 8 | NF-06 Receive Payment Result | Trigger: UC54, UC55 | Thêm UC21 (nếu UC21 gọi UC55). Đổi tên cổng nếu đổi PayOS |
| 9 | MSG-26 (appendix) | "Đã đặt lịch khám thành công." hiện ngay khi đặt | Giữ câu chữ, đổi **thời điểm hiển thị** (sau thanh toán xong hoặc sau khi chọn trả sau). Có thể thêm MSG mới "Đã giữ lịch, vui lòng thanh toán phí khám" nếu muốn báo lúc tạo lịch |
| 10 | Màn hình (§Screen), danh sách screen/UC21 | 4 bước đặt lịch | Cập nhật ảnh và mô tả sang 5 bước, thêm màn Thanh toán (QR PayOS, đếm ngược, trạng thái) |
| 11 | BR-53 (không hoàn tiền) | Áp dụng cho thanh toán | Giữ. Nên hiện cảnh báo "không hoàn tiền" ở bước 5 trước khi quét mã (UC55 bước 2 đã có, FE bước 5 chưa có) |

## 3. Đề xuất BR / MSG mới (nếu muốn thêm)

- **BR (đề xuất):** "Khi đặt lịch, Bệnh nhân được đề nghị thanh toán online phí khám ngay sau khi đặt; có thể trả sau tại quầy. Phí chưa trả không làm lịch bị hủy tự động." (Khớp code hiện tại.)
- Chỉ nếu muốn giữ chỗ chờ thanh toán: thêm BR "Lịch chưa thanh toán được giữ N phút, quá hạn tự hủy và trả slot". **Chưa có trong code**, phải sửa BE (trạng thái chờ thanh toán, job tự hủy, webhook xác nhận).
- BR-51 giữ "không xác nhận trong khoảng 15 phút thì coi là thất bại" cho giao dịch. BE đặt `link-ttl-seconds` mặc định 900 (15 phút), khớp.

## 4. Câu hỏi mở

1. Phí khám = 0 hoặc dịch vụ miễn phí: BE từ chối tạo hóa đơn (`SOURCE_NOT_BILLABLE` khi giá <= 0). Cần quy định luồng và hiển thị bước 5 cho trường hợp này.
2. Lịch chưa trả tiền chiếm slot: có cần giữ chỗ có hạn không (xem mục 3)?
3. Cổng chính thức là VNPay hay PayOS?
4. Lễ tân đặt lịch hộ (UC21, khu staff): có bước thanh toán online không, hay chỉ thu tại quầy theo UC54? Code hiện chỉ thêm bước 5 cho khu bệnh nhân.
5. Trạng thái BE: PayOS mặc định tắt (`APP_PAYMENT_PAYOS_ENABLED=false`), bật cùng 3 key thì bước 5 mới chạy; hiện đang báo 503.
6. `issuedAt` của hóa đơn hiện ra "1/1/1970" ở bước 5, nghi BE trả thiếu trường; cần kiểm.
