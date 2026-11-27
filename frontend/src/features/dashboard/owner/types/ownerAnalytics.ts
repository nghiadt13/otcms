import type { ServiceCategory } from "@/lib/domain/clinicCategories";
import type {
  DashboardPeriod,
  Metric,
} from "@/features/dashboard/types/dashboardOverview";

/**
 * Hợp đồng dữ liệu của dashboard Owner. Tiền là số nguyên VND, ngày là
 * "YYYY-MM-DD" theo giờ Asia/Ho_Chi_Minh. Khớp `GET /api/v1/owner/analytics`.
 */

/** Doanh thu một ngày, tách theo hình thức thanh toán. `previousTotal` là ngày cùng thứ tự của kỳ trước. */
export type DailyRevenuePoint = {
  date: string;
  cash: number;
  payos: number;
  previousTotal: number | null;
};

/** `doctorId: null` là phần doanh thu không truy được bác sĩ. */
export type DoctorPerformanceRow = {
  doctorId: string | null;
  name: string;
  visits: number;
  /** Số ngày bác sĩ có khám trong kỳ; ca TB mỗi ngày = visits / activeDays. */
  activeDays: number;
  revenue: number;
  previousRevenue: number | null;
};

export type ServiceRevenueRow = {
  serviceId: string;
  name: string;
  category: ServiceCategory;
  count: number;
  revenue: number;
  previousRevenue: number | null;
};

export type BookingFunnel = {
  booked: number;
  arrived: number;
  completed: number;
  cancelled: number;
  noShow: number;
};

export type OwnerAnalytics = {
  period: DashboardPeriod;
  previousPeriod: DashboardPeriod;
  revenue: {
    total: Metric;
    cash: Metric;
    payos: Metric;
    daily: DailyRevenuePoint[];
  };
  appointments: Metric;
  arrivals: Metric;
  funnel: BookingFunnel;
  doctors: DoctorPerformanceRow[];
  services: ServiceRevenueRow[];
};
