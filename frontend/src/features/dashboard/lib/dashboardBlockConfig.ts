import { CATEGORY_LABELS } from "@/lib/domain/clinicCategories";
import { ADMIN_ROUTES } from "@/lib/routes/adminRoutes";
import type {
  AppointmentStatusCode,
  DashboardOverview,
  DashboardRowView,
  Metric,
  PaymentMethod,
  VisitStatusCode,
} from "../types/dashboardOverview";

export type DashboardBlockKey =
  | "appointments"
  | "visits"
  | "imagingAndLab"
  | "services"
  | "payments"
  | "pendingAndCancelled"
  | "reviews";

export type DashboardBlockView = {
  key: DashboardBlockKey;
  title: string;
  description: string;
  noSource: boolean;
  rows: DashboardRowView[];
  /** Câu dạy cách dùng khi danh sách con rỗng (kỳ không hoạt động). */
  emptyNotice?: string;
  /** Route module đích (chưa gắn from/to). */
  href: string;
};

export const NO_SOURCE_TEXT = "Chưa có dữ liệu để tổng hợp cho khối này.";
const RETRY_PERIOD_HINT = "Chọn kỳ khác để xem lại.";

const APPOINTMENT_STATUS: Record<AppointmentStatusCode, string> = {
  BOOKED: "Đã đặt",
  CHECKED_IN: "Đã check-in",
  CANCELLED: "Đã hủy",
  NO_SHOW: "Vắng mặt",
};
const VISIT_STATUS: Record<VisitStatusCode, string> = {
  WAITING: "Đang chờ",
  IN_PROGRESS: "Đang khám",
  COMPLETED: "Hoàn tất",
  CANCELLED: "Đã hủy",
};
const PAYMENT_METHOD: Record<PaymentMethod, string> = {
  CASH: "Tiền mặt",
  VNPAY: "VNPay",
};

const count = (
  label: string,
  metric: Metric | null,
  extra: Partial<DashboardRowView> = {},
): DashboardRowView => ({
  label,
  value: metric?.value ?? null,
  previousValue: metric?.previousValue ?? null,
  format: "count",
  ...extra,
});

/** Giữ thứ tự cố định của enum, thiếu mã thì coi là 0. */
function fixedRows<C extends string>(
  labels: Record<C, string>,
  rows: { code: C; value: number; previousValue: number | null }[],
  extra: Partial<DashboardRowView>,
): DashboardRowView[] {
  return (Object.keys(labels) as C[]).map((code) =>
    count(
      labels[code],
      rows.find((row) => row.code === code) ?? {
        value: 0,
        previousValue: 0,
      },
      extra,
    ),
  );
}

/** Dòng hiển thị `—` cho khối NO_SOURCE, giữ khung bảng cố định. */
const placeholder = (
  labels: string[],
  extra: Partial<DashboardRowView> = {},
): DashboardRowView[] => labels.map((label) => count(label, null, extra));

/** Dựng 7 khối hiển thị từ phản hồi API; không chứa logic gọi mạng. */
export function buildDashboardBlocks(
  overview: DashboardOverview,
): DashboardBlockView[] {
  const { appointments, visits, imagingAndLab, services, payments } = overview;
  const { pendingAndCancelled, reviews } = overview;
  const money = (label: string, metric: Metric): DashboardRowView => ({
    ...count(label, metric),
    format: "vnd",
  });

  return [
    {
      key: "appointments",
      title: "Lịch hẹn",
      description: "Số lịch hẹn tạo trong kỳ, theo trạng thái.",
      href: ADMIN_ROUTES.appointments,
      noSource: appointments.status === "NO_SOURCE",
      rows:
        appointments.status === "READY"
          ? [
              count("Tổng", appointments.total),
              ...fixedRows(APPOINTMENT_STATUS, appointments.byStatus, {}),
            ]
          : placeholder(["Tổng", ...Object.values(APPOINTMENT_STATUS)]),
    },
    {
      key: "visits",
      title: "Lượt khám và hàng đợi",
      description: "Lượt khám theo trạng thái và số người đang chờ ở từng phòng.",
      href: ADMIN_ROUTES.appointments,
      noSource: visits.status === "NO_SOURCE",
      rows:
        visits.status === "READY"
          ? [
              count("Tổng lượt khám", visits.total),
              ...visits.byStatus.map((row) =>
                count(VISIT_STATUS[row.code], row, { group: "Theo trạng thái" }),
              ),
              ...visits.waitingByRoom.map((row) =>
                count(row.roomName, row, {
                  group: "Đang chờ theo phòng",
                  snapshot: true,
                }),
              ),
            ]
          : placeholder(["Tổng lượt khám"]),
      emptyNotice:
        visits.status === "READY" &&
        visits.byStatus.length === 0 &&
        visits.waitingByRoom.length === 0
          ? `Chưa có lượt khám hay người chờ nào trong kỳ này. ${RETRY_PERIOD_HINT}`
          : undefined,
    },
    {
      key: "imagingAndLab",
      title: "Cận lâm sàng",
      description: "Chỉ định đã thực hiện trong kỳ.",
      href: ADMIN_ROUTES.reports,
      noSource: imagingAndLab.status === "NO_SOURCE",
      rows:
        imagingAndLab.status === "READY"
          ? [
              count("Chẩn đoán hình ảnh", imagingAndLab.imagingPerformed),
              count("Xét nghiệm", imagingAndLab.labPerformed),
            ]
          : placeholder(["Chẩn đoán hình ảnh", "Xét nghiệm"]),
    },
    {
      key: "services",
      title: "Dịch vụ dùng nhiều nhất",
      description: "Năm dịch vụ có nhiều lượt dùng nhất trong kỳ.",
      href: ADMIN_ROUTES.reports,
      noSource: services.status === "NO_SOURCE",
      rows:
        services.status === "READY"
          ? services.top.map((row) =>
              count(row.name, row, { hint: CATEGORY_LABELS[row.category] }),
            )
          : [],
      emptyNotice:
        services.status === "READY" && services.top.length === 0
          ? `Chưa có dịch vụ nào được dùng trong kỳ này. ${RETRY_PERIOD_HINT}`
          : undefined,
    },
    {
      key: "payments",
      title: "Thanh toán đã thu",
      description: "Tiền đã thu trong kỳ, tách theo hình thức.",
      href: ADMIN_ROUTES.payments,
      noSource: payments.status === "NO_SOURCE",
      rows:
        payments.status === "READY"
          ? [
              money("Tổng đã thu", payments.collected),
              ...fixedRows(PAYMENT_METHOD, payments.byMethod, {}).map(
                (row) => ({ ...row, format: "vnd" as const }),
              ),
            ]
          : placeholder(["Tổng đã thu", ...Object.values(PAYMENT_METHOD)], {
              format: "vnd",
            }),
    },
    {
      key: "pendingAndCancelled",
      title: "Chỉ định chờ và đã hủy",
      description: "Chỉ định tạo trong kỳ đang chờ thanh toán hoặc đã hủy.",
      href: ADMIN_ROUTES.unpaidItems,
      noSource: pendingAndCancelled.status === "NO_SOURCE",
      rows:
        pendingAndCancelled.status === "READY"
          ? [
              count("Chờ thanh toán", pendingAndCancelled.pendingPayment),
              count("Đã hủy", pendingAndCancelled.cancelled),
            ]
          : placeholder(["Chờ thanh toán", "Đã hủy"]),
    },
    {
      key: "reviews",
      title: "Phản hồi bệnh nhân",
      description: "Điểm đánh giá trung bình và đánh giá đang chờ duyệt.",
      href: ADMIN_ROUTES.reviews,
      noSource: reviews.status === "NO_SOURCE",
      rows:
        reviews.status === "READY"
          ? [
              {
                label: "Điểm trung bình",
                value: reviews.averageRating.value,
                previousValue: reviews.averageRating.previousValue,
                format: "rating",
                note:
                  reviews.averageRating.value === null
                    ? "Chưa có đánh giá trong kỳ này"
                    : undefined,
              },
              count("Chờ duyệt", reviews.pendingApproval, { snapshot: true }),
            ]
          : [
              { ...count("Điểm trung bình", null), format: "rating" },
              count("Chờ duyệt", null),
            ],
    },
  ];
}
