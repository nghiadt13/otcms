/**
 * Luật khoảng ngày dùng chung cho lịch sử thanh toán và khoản chưa thanh toán.
 * Chuỗi `yyyy-MM-dd` so sánh trực tiếp được. Thiếu một đầu coi là hợp lệ (chưa lọc theo đầu đó).
 * Sai khoảng ngày → màn hiện MSG-54 dưới ô ngày và không gọi API.
 */
export function isBillingDateRangeValid(from?: string, to?: string): boolean {
  if (!from || !to) return true;
  return from <= to;
}
