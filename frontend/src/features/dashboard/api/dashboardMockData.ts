import type {
  DashboardOverview,
  DashboardPeriod,
} from "../types/dashboardOverview";

// Dữ liệu giả cho GET /api/v1/admin/dashboard. Số khớp nhau:
// 25+12+3+2 = 42, 5+6+25+2 = 38, 5.200.000 + 7.250.000 = 12.450.000.
const PERIOD_PLACEHOLDER: DashboardPeriod = { from: "", to: "" };

export const DASHBOARD_FIXTURE: DashboardOverview = {
  period: PERIOD_PLACEHOLDER,
  previousPeriod: PERIOD_PLACEHOLDER,
  appointments: {
    status: "READY",
    total: { value: 42, previousValue: 38 },
    byStatus: [
      { code: "BOOKED", value: 25, previousValue: 22 },
      { code: "CHECKED_IN", value: 12, previousValue: 10 },
      { code: "CANCELLED", value: 3, previousValue: 4 },
      { code: "NO_SHOW", value: 2, previousValue: 2 },
    ],
  },
  visits: {
    status: "READY",
    total: { value: 38, previousValue: 41 },
    byStatus: [
      { code: "WAITING", value: 5, previousValue: 6 },
      { code: "IN_PROGRESS", value: 6, previousValue: 7 },
      { code: "COMPLETED", value: 25, previousValue: 27 },
      { code: "CANCELLED", value: 2, previousValue: 1 },
    ],
    waitingByRoom: [
      {
        roomId: "5a1e0c3e-2b7d-4c1f-9a8e-0d6b1f2c3a01",
        roomName: "Phòng khám 1",
        value: 3,
        previousValue: null,
      },
      {
        roomId: "5a1e0c3e-2b7d-4c1f-9a8e-0d6b1f2c3a02",
        roomName: "Phòng khám 2",
        value: 2,
        previousValue: null,
      },
      {
        roomId: "5a1e0c3e-2b7d-4c1f-9a8e-0d6b1f2c3a03",
        roomName: "Phòng chẩn đoán hình ảnh",
        value: 0,
        previousValue: null,
      },
    ],
  },
  imagingAndLab: {
    status: "READY",
    imagingPerformed: { value: 14, previousValue: 11 },
    labPerformed: { value: 27, previousValue: 30 },
  },
  services: {
    status: "READY",
    top: [
      {
        serviceId: "7d9b4e2a-6c1f-4a3e-8b5d-2f0c9e1a7b01",
        name: "Khám chỉnh hình",
        category: "EXAMINATION",
        value: 12,
        previousValue: 9,
      },
      {
        serviceId: "7d9b4e2a-6c1f-4a3e-8b5d-2f0c9e1a7b02",
        name: "Chụp X-quang cột sống",
        category: "IMAGING",
        value: 9,
        previousValue: 8,
      },
      {
        serviceId: "7d9b4e2a-6c1f-4a3e-8b5d-2f0c9e1a7b03",
        name: "Xét nghiệm công thức máu",
        category: "LABORATORY",
        value: 8,
        previousValue: 12,
      },
      {
        serviceId: "7d9b4e2a-6c1f-4a3e-8b5d-2f0c9e1a7b04",
        name: "Chụp MRI khớp gối",
        category: "IMAGING",
        value: 5,
        previousValue: null,
      },
      {
        serviceId: "7d9b4e2a-6c1f-4a3e-8b5d-2f0c9e1a7b05",
        name: "Tái khám sau phẫu thuật",
        category: "EXAMINATION",
        value: 4,
        previousValue: 4,
      },
    ],
  },
  payments: {
    status: "READY",
    collected: { value: 12450000, previousValue: 9800000 },
    byMethod: [
      { code: "CASH", value: 5200000, previousValue: 4100000 },
      { code: "VNPAY", value: 7250000, previousValue: 5700000 },
    ],
  },
  pendingAndCancelled: {
    status: "READY",
    pendingPayment: { value: 5, previousValue: 3 },
    cancelled: { value: 2, previousValue: 1 },
  },
  reviews: {
    status: "READY",
    averageRating: { value: 4.6, previousValue: 4.4 },
    pendingApproval: { value: 3, previousValue: null },
  },
};

/** Kỳ nhiều hoạt động: số lớn, nhiều chữ số, có khối giảm mạnh so với kỳ trước. */
export const DASHBOARD_LARGE_FIXTURE: DashboardOverview = {
  ...DASHBOARD_FIXTURE,
  appointments: {
    status: "READY",
    total: { value: 12480, previousValue: 14120 },
    byStatus: [
      { code: "BOOKED", value: 4300, previousValue: 5200 },
      { code: "CHECKED_IN", value: 7410, previousValue: 8000 },
      { code: "CANCELLED", value: 520, previousValue: 600 },
      { code: "NO_SHOW", value: 250, previousValue: 320 },
    ],
  },
  payments: {
    status: "READY",
    collected: { value: 3_450_000_000, previousValue: 3_900_000_000 },
    byMethod: [
      { code: "CASH", value: 1_150_000_000, previousValue: 1_500_000_000 },
      { code: "VNPAY", value: 2_300_000_000, previousValue: 2_400_000_000 },
    ],
  },
  reviews: {
    status: "READY",
    averageRating: { value: 3.9, previousValue: 4.3 },
    pendingApproval: { value: 128, previousValue: null },
  },
};

/** Kỳ không hoạt động (AF1): mọi số 0, danh sách rỗng, chưa có đánh giá. */
export function buildEmptyOverview(
  period: DashboardPeriod,
  previousPeriod: DashboardPeriod,
): DashboardOverview {
  const zero = { value: 0, previousValue: 0 };
  return {
    period,
    previousPeriod,
    appointments: {
      status: "READY",
      total: { ...zero },
      byStatus: (
        ["BOOKED", "CHECKED_IN", "CANCELLED", "NO_SHOW"] as const
      ).map((code) => ({ code, ...zero })),
    },
    visits: {
      status: "READY",
      total: { ...zero },
      byStatus: [],
      waitingByRoom: [],
    },
    imagingAndLab: {
      status: "READY",
      imagingPerformed: { ...zero },
      labPerformed: { ...zero },
    },
    services: { status: "READY", top: [] },
    payments: {
      status: "READY",
      collected: { ...zero },
      byMethod: (["CASH", "VNPAY"] as const).map((code) => ({
        code,
        ...zero,
      })),
    },
    pendingAndCancelled: {
      status: "READY",
      pendingPayment: { ...zero },
      cancelled: { ...zero },
    },
    reviews: {
      status: "READY",
      averageRating: { value: null, previousValue: null },
      pendingApproval: { value: 0, previousValue: null },
    },
  };
}

/** Khối đánh giá chưa có nguồn dữ liệu. */
export function withoutReviews(overview: DashboardOverview): DashboardOverview {
  return { ...overview, reviews: { status: "NO_SOURCE" } };
}
