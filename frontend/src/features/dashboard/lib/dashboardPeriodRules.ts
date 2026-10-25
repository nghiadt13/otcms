import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import type { MsgCode } from "@/lib/api/apiError";
import type { DashboardPeriod } from "../types/dashboardOverview";

/** Số ngày tối đa của một kỳ (tạm, chờ R3 chốt). */
export const DASHBOARD_MAX_RANGE_DAYS = 366;

export type QuickPreset = "today" | "7d" | "30d" | "month";

export const QUICK_PRESETS: { value: QuickPreset; label: string }[] = [
  { value: "today", label: "Hôm nay" },
  { value: "7d", label: "7 ngày" },
  { value: "30d", label: "30 ngày" },
  { value: "month", label: "Tháng này" },
];

export const DEFAULT_PRESET: QuickPreset = "7d";

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Hôm nay theo giờ Việt Nam (yyyy-MM-dd). */
export const todayInVietnam = (): string => todayIsoDate();

/** Số ngày của kỳ, gồm cả hai đầu. */
export function periodLengthDays({ from, to }: DashboardPeriod): number {
  const ms = Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`);
  return Math.round(ms / 86_400_000) + 1;
}

/** Kỳ nhanh tính theo ngày hôm nay, gồm hôm nay. */
export function quickPeriodRange(
  preset: QuickPreset,
  today: string,
): DashboardPeriod {
  switch (preset) {
    case "today":
      return { from: today, to: today };
    case "7d":
      return { from: addDays(today, -6), to: today };
    case "30d":
      return { from: addDays(today, -29), to: today };
    case "month":
      return { from: `${today.slice(0, 8)}01`, to: today };
  }
}

/** Kỳ liền trước, cùng số ngày. */
export function previousPeriodOf(period: DashboardPeriod): DashboardPeriod {
  const days = periodLengthDays(period);
  const to = addDays(period.from, -1);
  return { from: addDays(to, -(days - 1)), to };
}

/**
 * Kiểm kỳ ở FE trước khi gọi API. Trả MSG-72 khi rỗng, sai định dạng, từ > đến,
 * vượt số ngày tối đa hoặc kết thúc sau hôm nay; hợp lệ trả null.
 */
export function validateDashboardPeriod(
  draft: { from: string; to: string },
  today: string = todayInVietnam(),
): MsgCode | null {
  const { from, to } = draft;
  if (!ISO_DATE.test(from) || !ISO_DATE.test(to)) return "MSG-72";
  if (from > to || to > today) return "MSG-72";
  if (periodLengthDays({ from, to }) > DASHBOARD_MAX_RANGE_DAYS)
    return "MSG-72";
  return null;
}
