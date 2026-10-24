import { formatDateTime } from "@/lib/format/formatDate";

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/**
 * Thời gian của một thông báo: "2 giờ trước · 23/10/2026 18:20". Quá 7 ngày hoặc ở tương lai
 * thì chỉ ghi ngày giờ tuyệt đối. `now` tách ra để kiểm thử xác định.
 */
export function notificationTimeLabel(
  isoTime: string,
  now: Date = new Date(),
): string {
  const absolute = formatDateTime(isoTime);
  const elapsed = now.getTime() - Date.parse(isoTime);
  if (!Number.isFinite(elapsed) || elapsed < 0 || elapsed >= 7 * DAY) {
    return absolute;
  }
  return `${relativeText(elapsed)} · ${absolute}`;
}

function relativeText(elapsed: number): string {
  if (elapsed < MINUTE) return "Vừa xong";
  if (elapsed < HOUR) return `${Math.floor(elapsed / MINUTE)} phút trước`;
  if (elapsed < DAY) return `${Math.floor(elapsed / HOUR)} giờ trước`;
  return `${Math.floor(elapsed / DAY)} ngày trước`;
}
