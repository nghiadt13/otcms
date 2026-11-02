import { addDays } from "@/lib/format/formatDate";
import type { ShiftPeriod } from "../types/shift";

// Mọi phép tính ngày làm trên chuỗi "yyyy-MM-dd" bằng UTC, không đọc múi giờ máy,
// nên không lệch ngày dù trình duyệt ở UTC hay UTC+7.

const WEEKDAY_SHORT = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export function isIsoDate(value: string | null | undefined): value is string {
  if (!value || !ISO_DATE.test(value)) return false;
  const [y, m, d] = value.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
}

/** 0 = Chủ nhật ... 6 = Thứ bảy. */
function weekdayIndex(iso: string): number {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d)).getUTCDay();
}

/** Thứ Hai đầu tuần chứa ngày `iso`. */
export function startOfWeek(iso: string): string {
  return addDays(iso, -((weekdayIndex(iso) + 6) % 7));
}

export interface ShiftRange {
  from: string;
  to: string;
  /** Các ngày trong kỳ, tăng dần. */
  days: string[];
}

export function getShiftRange(anchor: string, period: ShiftPeriod): ShiftRange {
  if (period === "day") return { from: anchor, to: anchor, days: [anchor] };
  const from = startOfWeek(anchor);
  const days = Array.from({ length: 7 }, (_, i) => addDays(from, i));
  return { from, to: days[6], days };
}

/** Lùi (-1) hoặc tiến (+1) một kỳ. */
export function stepAnchor(
  anchor: string,
  period: ShiftPeriod,
  direction: -1 | 1,
): string {
  return addDays(anchor, direction * (period === "week" ? 7 : 1));
}

export function weekdayShort(iso: string): string {
  return WEEKDAY_SHORT[weekdayIndex(iso)];
}

/** "19/10" */
export function dayMonth(iso: string): string {
  const [, m, d] = iso.split("-");
  return `${d}/${m}`;
}

/** "20/10/2026" */
export function dayMonthYear(iso: string): string {
  const [y, m, d] = iso.split("-");
  return `${d}/${m}/${y}`;
}

/** Nhãn kỳ: "19/10 – 25/10/2026", hoặc "T3, 20/10/2026" cho kỳ Ngày. */
export function rangeLabel(anchor: string, period: ShiftPeriod): string {
  if (period === "day")
    return `${weekdayShort(anchor)}, ${dayMonthYear(anchor)}`;
  const { from, to } = getShiftRange(anchor, "week");
  const fromLabel =
    from.slice(0, 4) === to.slice(0, 4) ? dayMonth(from) : dayMonthYear(from);
  return `${fromLabel} – ${dayMonthYear(to)}`;
}

export const isPastDate = (iso: string, today: string) => iso < today;

export const rangeContains = (range: ShiftRange, iso: string) =>
  iso >= range.from && iso <= range.to;
