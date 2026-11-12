# Phát hiện khi kiểm phase 01: race khi webhook và người dùng cùng xử lý giữ lịch

## Bằng chứng

- `AppointmentHoldSettlementPostgresTest.webhookRacingAUserReleaseEndsInExactlyOneOutcome()[1]` lỗi ở cả lượt full đầu và lượt tập trung tiếp theo.
- Ngoại lệ: `ObjectOptimisticLockingFailureException`, phát sinh ở `InvoiceSettlementSupport.lockInvoiceFresh` khi gọi `InvoiceRepository.findByIdForUpdate`, trước bước refresh.
- Các file billing và test/fixture này không thay đổi so với commit nền `610bea1c`; đã kiểm bằng `git diff --quiet HEAD -- .../billing .../AppointmentHoldSettlementPostgresTest.java .../HoldBillingTestSupport.java` trước khi tạo commit phase 00/01.

## Cơ chế

1. `AppointmentInvoiceVoider.voidUnpaid` bắt đầu transaction và đọc invoice bằng `findFirstByAppointmentId`.
2. Một webhook đồng thời có thể commit phiên bản invoice mới.
3. Câu query pessimistic lock thấy phiên bản DB khác phiên bản entity đang được quản lý. Hibernate ném lỗi trước khi đoạn refresh phía sau được thực thi.

Đây là **cơ chế race có sẵn trong code, mới được quan sát khi kiểm phase 01**. Chưa chạy lại nguyên commit cũ để khẳng định test từng thất bại trong baseline lịch sử; không đưa nó vào danh sách 5 lỗi baseline một cách mặc định.

## Vì sao tách khỏi phần thay đổi phase 01

- Test gọi service trực tiếp trên worker thread, không qua servlet filter/MDC.
- Fixture thay appointment payment port bằng JDBC, tự khóa hàng appointment; không gọi coordination guard vừa thay.
- Đường lỗi không gọi AuditLogWriter hay view nhật ký mới.
- V109 không sửa bảng invoice, payment attempt hay cơ chế version của invoice.
- Hai lượt review chỉ đọc không tìm thấy thành phần phase 01 trên đường thực thi lỗi.

## Bàn giao phần billing

- Giữ nguyên test race và các assert kết quả. Không thêm retry ở test, không bỏ repetition và không Disabled để che lỗi.
- Khi sửa billing, cần bảo đảm đọc lại state và lấy khóa trên cùng snapshot hợp lệ, kể cả entity đã tồn tại trong persistence context. Không chỉ refresh sau câu query có thể ném lỗi.
- Kiểm cả webhook đối đầu release và webhook đối đầu sweeper; không được void hóa đơn đã có tiền, không xác nhận lịch hai lần, không làm mất trạng thái reconciliation.
- Phát hiện này phải được xử lý hoặc quyết định rõ trước nghiệm thu tích hợp phase 10. Phase 01 không tự thay code billing ngoài phạm vi.
- Log thô: `.snapshots/phase01-full.log`, `.snapshots/phase01-focused-final.log` (không commit). Kết quả hồi quy cuối nằm trong `phase-01-verification.md`.
