"use client";

// Nạp kho đặt lịch giả khi khu bệnh nhân mở. Kho phí khám dùng chung (đọc bởi thanh toán) được dựng
// lúc nạp module này, nên người dùng vào thẳng màn thanh toán vẫn thấy lịch đã đặt. Không hiển thị gì.
// Chỉ cho lớp API giả: xóa khi nối BE.
import "../api/bookingMockStore";

export function BookingMockStoreBootstrap(): null {
  return null;
}
