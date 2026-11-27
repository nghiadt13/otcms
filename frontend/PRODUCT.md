# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Chủ phòng khám (Clinic Owner) của một phòng khám chấn thương chỉnh hình nhỏ ở Việt Nam. Họ không làm việc hằng ngày ở quầy; họ mở trang để nắm tình hình kinh doanh và vận hành theo kỳ: có bao nhiêu khách đặt lịch và đến khám, tiền thu về bao nhiêu, bác sĩ và dịch vụ nào đem lại doanh thu. Họ đọc bằng tiếng Việt, trên máy tính và đôi khi trên điện thoại.

## Product Purpose

OTCMS là hệ thống quản lý phòng khám: đặt lịch, tiếp đón, hàng đợi, khám, chỉ định chụp hình và xét nghiệm, thanh toán (tiền mặt, PayOS), đơn thuốc điện tử. Dashboard Owner là màn đọc-chỉ-xem tổng hợp số liệu kinh doanh. Thành công: Owner hiểu tình hình kỳ đang xem trong vài giây và tin rằng con số khớp với tiền và lượt khám thật.

## Positioning

Số liệu lấy thẳng từ giao dịch thật của chính phòng khám (thanh toán đã xác nhận, lượt khám đã check-in), không phải số ước lượng.

## Operating Context

- Báo cáo theo kỳ, so sánh với kỳ liền trước cùng độ dài.
- Giờ lâm sàng theo múi giờ Asia/Ho_Chi_Minh; tiền tệ VND.
- Dashboard Admin hiện có ở `/admin` (UC77); Owner dùng chung chức năng theo R3 và được mở rộng thêm các chỉ số kinh tế.

## Capabilities and Constraints

- Chỉ ghi nhận tiền thu vào; không có chi phí nên không có lãi lỗ.
- Doanh thu chỉ tính thanh toán đã xác nhận (PAYOS và tiền mặt), trừ phần đã hoàn.
- Xét nghiệm có dữ liệu thật (lab_status, kết quả đã duyệt); số hiện đang bị gán cứng 0 ở báo cáo và sẽ được nối lại.
- Doanh thu theo bác sĩ cần quy tắc gán: phí khám theo bác sĩ của lịch hẹn; phí chụp hình/xét nghiệm theo bác sĩ chỉ định; phần không truy được gom vào "Không xác định".
- Ngoài R3: doanh thu theo bác sĩ, doanh thu theo dịch vụ, phễu đặt lịch → đến khám → hoàn thành, ca khám trung bình mỗi bác sĩ mỗi ngày. Cần bổ sung vào UC77; việc sửa R3 do chủ đồ án quyết.
- Thuật ngữ tiếng Việt: lịch hẹn, lượt khám, doanh thu, chờ hoàn tiền, kỳ trước.

## Brand Commitments

Bảng màu Teal + Coral và font Be Vietnam Pro của OTCMS (`src/styles/brand-palette.css`, `src/lib/fonts.ts`). Coral không dùng cho lỗi hoặc xoá; lỗi dùng `destructive`.

## Evidence on Hand

Chưa có số liệu doanh thu thật để minh hoạ. Giai đoạn thiết kế dùng dữ liệu mẫu gắn nhãn rõ là mẫu; không có tên khách hàng thật hay con số thật.

## Product Principles

1. Con số phải truy được về giao dịch: thu thêm một khoản thì đúng chỉ số đó tăng đúng bằng khoản đó.
2. Đọc nhanh hơn trang trí: Owner cần thấy tổng, xu hướng và ai đóng góp, không cần hiệu ứng.
3. Không bịa: thiếu nguồn thì ghi "chưa có dữ liệu", không điền số 0 như thể đã đo.
4. Cùng một ngôn ngữ giao diện với phần còn lại của OTCMS.

## Accessibility & Inclusion

Tương phản WCAG AA; không chỉ dùng màu để phân biệt tăng/giảm (kèm mũi tên và chữ); biểu đồ có bảng số liệu thay thế; hỗ trợ `prefers-reduced-motion`; dùng được trên màn hình điện thoại.
