/**
 * Đọc `?page=` (đánh số từ 1 trên URL) và đổi sang chỉ số trang 0-based của API.
 * Thiếu, không phải số nguyên dương hoặc là mảng thì về trang đầu.
 */
export function readPageIndex(value: string | string[] | undefined): number {
  const parsed = typeof value === "string" ? Number.parseInt(value, 10) : Number.NaN;
  return Number.isInteger(parsed) && parsed >= 1 ? parsed - 1 : 0;
}
