const WEEKDAYS = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"] as const;

/** "2026-10-24" → "T7". Tính từ lịch, không phụ thuộc múi giờ của máy chạy. */
export function weekdayShortVi(isoDate: string): string {
  return WEEKDAYS[new Date(`${isoDate}T00:00:00Z`).getUTCDay()];
}

/** "2026-10-24" → "24/10". */
export function dayMonthVi(isoDate: string): string {
  return `${isoDate.slice(8, 10)}/${isoDate.slice(5, 7)}`;
}
