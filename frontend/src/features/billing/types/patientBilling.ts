import type { CareRelationship } from "@/lib/domain/careRelationship";
import type { PaymentMethod, PaymentStatus } from "@/lib/domain/clinicVisit";
import type { PageQuery } from "@/lib/api/pagination";

// Kiểu dữ liệu thanh toán của khu bệnh nhân (plan 261024-0512 phase 06). Khớp hình JSON của
// BE. FE không tính quyền, không tự quyết số tiền: tổng và hành động do BE trả về.

/** Lịch hẹn (phí khám trước khi đến) hoặc lượt khám (chỉ định trong lượt khám). */
export type PaymentSourceKind = "appointment" | "visit";

/**
 * Khoản tiền đã chuẩn hóa về 3 trạng thái: phí khám và chỉ định hiển thị cùng một cách.
 * PENDING_PAYMENT: chờ trả; PAID: đã trả (có phiếu thu); CANCELLED: đã hủy (có lý do).
 */
export type PaymentItemStatus = "PENDING_PAYMENT" | "PAID" | "CANCELLED";

/** Một nguồn còn khoản chờ thanh toán. `date`: lượt khám yyyy-MM-dd; lịch hẹn ISO có giờ. */
export interface PayableSourceSummary {
  kind: PaymentSourceKind;
  id: string;
  date: string;
  patient: { id: string; fullName: string; relationship: CareRelationship };
  pendingCount: number;
  /** VND nguyên, tổng các khoản PENDING_PAYMENT. */
  pendingAmount: number;
}

export interface PaymentItem {
  id: string;
  label: string;
  /** VND nguyên, giá chốt lúc chỉ định hoặc đặt lịch. */
  amount: number;
  status: PaymentItemStatus;
  cancellationReason: string | null;
  receipt: { paymentId: string; receiptNo: string } | null;
}

export interface PayableSourceDetail extends PayableSourceSummary {
  items: PaymentItem[];
}

/** POST /api/v1/payments/online. Chỉ một nguồn và trọn từng khoản (không trả một phần). */
export interface CreateOnlinePaymentRequest {
  sourceKind: PaymentSourceKind;
  sourceId: string;
  itemIds: string[];
}

export interface CreateOnlinePaymentResponse {
  paymentId: string;
  /** Tổng do BE tính khi tạo yêu cầu; FE chỉ so với tạm tính để xem trước. */
  total: number;
  /** Đường dẫn nội bộ hoặc https tới cổng VNPay đã cấu hình. */
  redirectUrl: string;
}

/** Thanh toán trực tuyến hoặc tiền mặt đã ghi nhận của tài khoản; trả về từ GET /payments/{id}. */
export interface PatientPayment {
  id: string;
  status: PaymentStatus;
  method: PaymentMethod;
  total: number;
  /** ISO có offset; null khi chưa hoàn tất. */
  paidAt: string | null;
  vnpayRef: string | null;
  receiptNo: string | null;
  /** Người được khám (không phải người trả). */
  patientName: string;
  items: { label: string; amount: number }[];
  /** Chỉ khi SUCCESS và khoản vừa trả có chỉ định phòng. */
  queueAssignments: { roomName: string; queueNumber: number }[];
  sourceKind: PaymentSourceKind;
  sourceId: string;
}

/** Bộ lọc GET /api/v1/payments (`status=SUCCESS` do FE gắn cố định). Chuỗi rỗng = không lọc. */
export interface PatientPaymentListQuery extends PageQuery {
  /** yyyy-MM-dd, tính cả ngày. */
  from?: string;
  /** yyyy-MM-dd, tính cả ngày. */
  to?: string;
  patientId?: string;
}

/** GET /api/v1/payments/payable-sources. */
export interface PayableSourcesQuery {
  patientId?: string;
}
