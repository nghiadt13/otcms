// Mọi hàm hiển thị theo múi Asia/Ho_Chi_Minh, kiểu dd/MM/yyyy và HH:mm.
const TIME_ZONE = "Asia/Ho_Chi_Minh";

function parts(value: Date, options: Intl.DateTimeFormatOptions) {
  const result: Record<string, string> = {};
  for (const part of new Intl.DateTimeFormat("en-GB", {
    timeZone: TIME_ZONE,
    hour12: false,
    ...options,
  }).formatToParts(value))
    result[part.type] = part.value;
  return result;
}

function toDate(value: string | Date): Date {
  // "2026-10-20" (chỉ ngày) coi là ngày theo giờ VN, không để lệch múi.
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)
    ? new Date(`${value}T00:00:00+07:00`)
    : new Date(value);
}

const dateParts = {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
} as const;

export function formatDate(value: string | Date): string {
  const p = parts(toDate(value), dateParts);
  return `${p.day}/${p.month}/${p.year}`;
}

export function formatTime(value: string | Date): string {
  const p = parts(toDate(value), { hour: "2-digit", minute: "2-digit" });
  return `${p.hour === "24" ? "00" : p.hour}:${p.minute}`;
}

export function formatDateTime(
  value: string | Date,
  options: { seconds?: boolean } = {},
): string {
  const p = parts(toDate(value), {
    ...dateParts,
    hour: "2-digit",
    minute: "2-digit",
    ...(options.seconds ? { second: "2-digit" } : {}),
  });
  const hour = p.hour === "24" ? "00" : p.hour;
  const time = `${hour}:${p.minute}${options.seconds ? `:${p.second}` : ""}`;
  return `${p.day}/${p.month}/${p.year} ${time}`;
}

/** Ngày hôm nay (yyyy-MM-dd) theo giờ Việt Nam. */
export function todayIsoDate(now: Date = new Date()): string {
  const p = parts(now, dateParts);
  return `${p.year}-${p.month}-${p.day}`;
}

/** Cộng/trừ ngày trên chuỗi yyyy-MM-dd, không phụ thuộc múi giờ máy. */
export function addDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const result = new Date(Date.UTC(y, m - 1, d + days));
  return result.toISOString().slice(0, 10);
}
