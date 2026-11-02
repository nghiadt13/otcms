import { formatDate, formatTime } from "@/lib/format/formatDate";

// Thời gian của luồng đặt lịch. Mọi giờ là giờ Việt Nam (+07:00), không phụ thuộc múi giờ máy:
// ngày "YYYY-MM-DD" được tính theo lịch, không qua Date theo giờ địa phương.

const VN_TIME_ZONE = "Asia/Ho_Chi_Minh";
const SHORT_WEEKDAYS = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
const LONG_WEEKDAYS = [
  "Chủ nhật",
  "Thứ hai",
  "Thứ ba",
  "Thứ tư",
  "Thứ năm",
  "Thứ sáu",
  "Thứ bảy",
];

const vnParts = new Intl.DateTimeFormat("en-GB", {
  timeZone: VN_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});

/** Thời điểm → ISO giờ VN có giây, ví dụ "2026-10-23T08:05:00+07:00". */
export function vnIsoOf(instant: Date): string {
  const parts: Record<string, string> = {};
  for (const part of vnParts.formatToParts(instant))
    parts[part.type] = part.value;
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}:${parts.second}+07:00`;
}

/** ("2026-10-23", "08:30") → "2026-10-23T08:30:00+07:00". */
export function vnIsoAt(date: string, hhmm: string): string {
  return `${date}T${hhmm}:00+07:00`;
}

/** Ngày YYYY-MM-DD của một thời điểm ISO giờ VN. */
export const dateOfStart = (startAt: string): string => startAt.slice(0, 10);

/** Giờ "HH:mm" (giờ VN) của một thời điểm ISO giờ VN. */
export const hhmmOfStart = (startAt: string): string => startAt.slice(11, 16);

/** 0 = Chủ nhật. */
export function weekdayIndex(date: string): number {
  return new Date(`${date}T00:00:00Z`).getUTCDay();
}

/** Nhãn ngắn của ô ngày trên dải: "T5", "CN". */
export const shortWeekdayLabel = (date: string): string =>
  SHORT_WEEKDAYS[weekdayIndex(date)];

/** "Thứ năm", "Chủ nhật". */
export const longWeekdayLabel = (date: string): string =>
  LONG_WEEKDAYS[weekdayIndex(date)];

/** Ô ngày trên dải: "23/10" (thứ đứng riêng ở dòng trên). */
export const shortDateLabel = (date: string): string =>
  `${date.slice(8, 10)}/${date.slice(5, 7)}`;

/** Dòng tóm tắt lịch: "Thứ năm 23/10/2026 lúc 08:30". */
export function longStartLabel(startAt: string): string {
  const date = dateOfStart(startAt);
  return `${longWeekdayLabel(date)} ${formatDate(date)} lúc ${formatTime(startAt)}`;
}

/** Giờ đã qua so với thời điểm máy chủ (BE trả `serverNow`). */
export function isBeforeServerNow(startAt: string, serverNow: string): boolean {
  return Date.parse(startAt) < Date.parse(serverNow);
}
