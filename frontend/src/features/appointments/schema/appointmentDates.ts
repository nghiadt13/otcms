const WEEKDAYS = [
  "Chủ nhật",
  "Thứ hai",
  "Thứ ba",
  "Thứ tư",
  "Thứ năm",
  "Thứ sáu",
  "Thứ bảy",
];

/** "2026-10-21" → "Thứ tư 21/10" (tiêu đề cột lưới giờ trống). */
export function weekdayLabel(isoDate: string): string {
  const [, month, day] = isoDate.split("-");
  const weekday = new Date(`${isoDate}T00:00:00Z`).getUTCDay();
  return `${WEEKDAYS[weekday]} ${day}/${month}`;
}
