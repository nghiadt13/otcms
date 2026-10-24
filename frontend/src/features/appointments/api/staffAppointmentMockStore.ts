// Trạng thái riêng của khu Lễ tân trong kho giả: lịch đã báo bệnh nhân (AF1). Chuyển bác sĩ không còn
// ở đây: ghi thẳng vào kho lịch chung (`changeStoredAppointmentDoctor`) để khu bệnh nhân thấy bác sĩ mới.

const notifiedIds = new Set<string>();

/** Chỉ dùng trong test và demo: xóa các lịch đã báo bệnh nhân của khu Lễ tân. Gọi cùng `resetBookingMock`. */
export function resetStaffAppointmentMock(): void {
  notifiedIds.clear();
}

export function markNotified(appointmentId: string): void {
  notifiedIds.add(appointmentId);
}

export function isNotified(appointmentId: string): boolean {
  return notifiedIds.has(appointmentId);
}
