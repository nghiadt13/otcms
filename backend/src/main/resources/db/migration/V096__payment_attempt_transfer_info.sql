-- Thông tin chuyển khoản do cổng thanh toán trả khi tạo link, để app tự hiển thị chi tiết đơn hàng
-- (ngân hàng, chủ tài khoản, số tài khoản, nội dung) thay vì chuyển người dùng sang trang của cổng.
ALTER TABLE payment_attempts
    ADD COLUMN transfer_bank_bin       varchar(16),
    ADD COLUMN transfer_account_number varchar(64),
    ADD COLUMN transfer_account_name   varchar(200),
    ADD COLUMN transfer_description    varchar(200);
