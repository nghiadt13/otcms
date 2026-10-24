// Đường dẫn khu Lễ tân (/staff). Nguồn duy nhất cho liên kết giữa các màn: feature không
// import navigation của nhau. Màn có id thêm hàm tạo URL ở đây khi phase cần.

export const STAFF_ROUTES = {
  home: "/staff",
  checkIn: "/staff/check-in",
  receptionQueue: "/staff/reception-queue",
  appointments: "/staff/appointments",
  newAppointment: "/staff/appointments/new",
} as const;
