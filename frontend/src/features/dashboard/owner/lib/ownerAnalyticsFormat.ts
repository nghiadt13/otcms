import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import {
  formatDecimal1,
  formatSignedPercent1,
} from "@/lib/format/formatNumber";

/** 12.400.000 → "12,4 tr ₫"; dưới 1 triệu giữ số đầy đủ. Dùng cho nhãn trục, không dùng cho số chính. */
export function formatCompactVnd(amount: number): string {
  const abs = Math.abs(amount);
  if (abs >= 1_000_000_000)
    return `${formatDecimal1(amount / 1_000_000_000)} tỷ ₫`;
  if (abs >= 1_000_000) return `${formatDecimal1(amount / 1_000_000)} tr ₫`;
  return formatCurrencyVnd(amount);
}

export type Delta =
  | { kind: "none" }
  | { kind: "flat" }
  | { kind: "up" | "down"; percent: number | null; diff: number };

/**
 * So với kỳ trước. `none` khi không có số kỳ trước; kỳ trước bằng 0 mà kỳ này
 * có số thì chỉ báo hướng (percent null) vì chia cho 0 không có nghĩa.
 */
export function deltaOf(value: number, previous: number | null): Delta {
  if (previous === null) return { kind: "none" };
  const diff = value - previous;
  if (diff === 0) return { kind: "flat" };
  const percent = previous === 0 ? null : (diff / previous) * 100;
  return { kind: diff > 0 ? "up" : "down", percent, diff };
}

export const deltaPercentText = (
  delta: Extract<Delta, { percent: unknown }>,
) => (delta.percent === null ? "mới" : formatSignedPercent1(delta.percent));

/** Tỷ trọng phần trăm của `part` trong `total`; total = 0 thì 0. */
export const sharePercent = (part: number, total: number): number =>
  total > 0 ? (part / total) * 100 : 0;

/** Ca khám trung bình mỗi ngày bác sĩ có khám; chưa có ngày nào thì null. */
export const averagePerDay = (visits: number, activeDays: number) =>
  activeDays > 0 ? visits / activeDays : null;

/** Mốc trục tung "đẹp" cho giá trị lớn nhất: 1, 2, 2,5, 5 × 10^n, luôn ≥ max. */
export function niceAxisMax(max: number): number {
  if (max <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(max));
  for (const step of [1, 2, 2.5, 5, 10]) {
    if (step * magnitude >= max) return step * magnitude;
  }
  return 10 * magnitude;
}
