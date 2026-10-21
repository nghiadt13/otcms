import type { ServiceCategory } from "@/lib/domain/clinicCategories";

/** Kỳ, ngày "YYYY-MM-DD" (múi Asia/Ho_Chi_Minh), hai đầu gồm cả. */
export type DashboardPeriod = { from: string; to: string };

/** previousValue = null: không có số kỳ trước để so, hoặc số tức thời (hàng đợi, chờ duyệt). */
export type Metric = { value: number; previousValue: number | null };

/** value = null khi kỳ không có đánh giá nào. */
export type RatingMetric = {
  value: number | null;
  previousValue: number | null;
};

/** READY có đủ trường (trường rỗng là 0 hoặc []); NO_SOURCE không kèm trường nào. */
export type DashboardBlock<T> =
  | ({ status: "READY" } & T)
  | { status: "NO_SOURCE" };

export type StatusRow<C extends string> = Metric & { code: C };
export type AppointmentStatusCode =
  | "BOOKED"
  | "CHECKED_IN"
  | "CANCELLED"
  | "NO_SHOW";
export type VisitStatusCode =
  | "WAITING"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED"; // tạm, chờ BE
export type PaymentMethod = "CASH" | "VNPAY";
export type RoomWaitingRow = {
  roomId: string;
  roomName: string;
  value: number;
  previousValue: null;
};
export type TopServiceRow = Metric & {
  serviceId: string;
  name: string;
  category: ServiceCategory;
};

export type DashboardOverview = {
  period: DashboardPeriod;
  previousPeriod: DashboardPeriod;
  appointments: DashboardBlock<{
    total: Metric;
    byStatus: StatusRow<AppointmentStatusCode>[];
  }>;
  visits: DashboardBlock<{
    total: Metric;
    byStatus: StatusRow<VisitStatusCode>[];
    waitingByRoom: RoomWaitingRow[];
  }>;
  imagingAndLab: DashboardBlock<{
    imagingPerformed: Metric;
    labPerformed: Metric;
  }>;
  services: DashboardBlock<{ top: TopServiceRow[] }>;
  payments: DashboardBlock<{
    collected: Metric;
    byMethod: StatusRow<PaymentMethod>[];
  }>;
  pendingAndCancelled: DashboardBlock<{
    pendingPayment: Metric;
    cancelled: Metric;
  }>;
  reviews: DashboardBlock<{
    averageRating: RatingMetric;
    pendingApproval: Metric;
  }>;
};

/** Kiểu nội bộ của UI (không phải contract): một dòng trong bảng của khối. */
export type DashboardRowView = {
  label: string;
  /** Nhóm con trong cùng bảng, ví dụ "Theo trạng thái". */
  group?: string;
  /** Dòng phụ dưới nhãn, ví dụ loại dịch vụ. */
  hint?: string;
  /** null: khối NO_SOURCE hoặc không có số (điểm TB chưa có đánh giá). */
  value: number | null;
  previousValue: number | null;
  format: "count" | "vnd" | "rating";
  /** true: hiện "Số hiện tại", không so sánh. */
  snapshot?: boolean;
  /** Thay câu so sánh mặc định (ví dụ chưa có đánh giá). */
  note?: string;
};
