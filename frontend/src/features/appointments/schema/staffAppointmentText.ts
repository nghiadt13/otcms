// Chữ và cách hiển thị riêng của khu Lễ tân. Chữ dùng chung với admin (Dialog, toast) nằm ở
// appointmentMessages.ts; file này chỉ giữ phần khác về nội dung.

/**
 * Dòng giải thích khi không có bác sĩ thay (AF1) trong Dialog của Lễ tân. Không nhắc đến admin:
 * Lễ tân không hủy hay đổi giờ (BR-22), việc đó để bệnh nhân quyết định sau khi được báo.
 */
export const STAFF_NO_CANDIDATE_LINES: readonly string[] = [
  "Không có bác sĩ nào có ca vào giờ này.",
  "Lễ tân không hủy hoặc đổi giờ lịch hẹn. Báo cho bệnh nhân để họ tự quyết định.",
];

/** "0912345678" → "0912 345 678". Số không đủ 10 chữ số giữ nguyên; không có số thì ghi rõ. */
export function formatPhoneForDisplay(phone: string | null): string {
  if (!phone) return "Chưa có SĐT";
  return /^\d{10}$/.test(phone)
    ? phone.replace(/^(\d{4})(\d{3})(\d{3})$/, "$1 $2 $3")
    : phone;
}
