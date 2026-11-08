-- "Ghi nhớ đăng nhập": phiên không ghi nhớ dùng cookie hết khi đóng trình duyệt.
-- Cờ nằm ở phiên để lần xoay refresh token giữ đúng kiểu cookie. Phiên cũ coi như đã ghi nhớ.
ALTER TABLE sessions ADD COLUMN remember_me boolean NOT NULL DEFAULT true;
