import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import {
  formatDecimal1,
  formatInteger,
  formatSignedPercent1,
} from "@/lib/format/formatNumber";
import type { DashboardRowView } from "../types/dashboardOverview";

type Format = DashboardRowView["format"];

/** Số hiển thị theo loại; null là "—" (chưa có nguồn hoặc chưa có số). */
export function formatMetricValue(
  value: number | null,
  format: Format,
): string {
  if (value === null) return "—";
  if (format === "vnd") return formatCurrencyVnd(value);
  if (format === "rating") return formatDecimal1(value);
  return formatInteger(value);
}

type ComparisonInput = {
  value: number | null;
  previousValue: number | null;
  format: Format;
  snapshot?: boolean;
};

/** So với kỳ trước bằng chữ, ví dụ "tăng 4 so với kỳ trước 38 (+10,5%)". */
export function describeComparison({
  value,
  previousValue,
  format,
  snapshot,
}: ComparisonInput): string {
  if (snapshot) return "Số hiện tại";
  if (value === null || previousValue === null) return "Không so sánh được";

  const previousText = formatMetricValue(previousValue, format);
  const diff = value - previousValue;
  if (diff === 0) return `không đổi so với kỳ trước ${previousText}`;

  const amount = formatMetricValue(Math.abs(diff), format);
  const direction = diff > 0 ? "tăng" : "giảm";
  const base = `${direction} ${amount} so với kỳ trước ${previousText}`;
  // Điểm trung bình so bằng chênh lệch; kỳ trước = 0 thì không có %.
  if (format === "rating" || previousValue === 0) return base;
  return `${base} (${formatSignedPercent1((diff / previousValue) * 100)})`;
}
