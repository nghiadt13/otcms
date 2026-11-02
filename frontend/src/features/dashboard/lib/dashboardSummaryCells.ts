import type {
  DashboardOverview,
  DashboardRowView,
} from "../types/dashboardOverview";

export type DashboardSummaryCell = Pick<
  DashboardRowView,
  "label" | "value" | "previousValue" | "format" | "note"
> & { key: string; noSource: boolean };

/** 5 ô của dải tóm tắt. Khối NO_SOURCE cho `value: null` (hiện "—"). */
export function buildSummaryCells(
  overview: DashboardOverview,
): DashboardSummaryCell[] {
  const { appointments, visits, payments, pendingAndCancelled, reviews } =
    overview;
  const noValue = { value: null, previousValue: null } as const;

  return [
    {
      key: "appointments",
      label: "Lịch hẹn",
      format: "count",
      noSource: appointments.status === "NO_SOURCE",
      ...(appointments.status === "READY" ? appointments.total : noValue),
    },
    {
      key: "visits",
      label: "Lượt khám",
      format: "count",
      noSource: visits.status === "NO_SOURCE",
      ...(visits.status === "READY" ? visits.total : noValue),
    },
    {
      key: "payments",
      label: "Đã thu",
      format: "vnd",
      noSource: payments.status === "NO_SOURCE",
      ...(payments.status === "READY" ? payments.collected : noValue),
    },
    {
      key: "pending",
      label: "Chờ thanh toán",
      format: "count",
      noSource: pendingAndCancelled.status === "NO_SOURCE",
      ...(pendingAndCancelled.status === "READY"
        ? pendingAndCancelled.pendingPayment
        : noValue),
    },
    {
      key: "rating",
      label: "Điểm trung bình",
      format: "rating",
      noSource: reviews.status === "NO_SOURCE",
      note:
        reviews.status === "READY" && reviews.averageRating.value === null
          ? "Chưa có đánh giá trong kỳ này"
          : undefined,
      ...(reviews.status === "READY" ? reviews.averageRating : noValue),
    },
  ];
}
