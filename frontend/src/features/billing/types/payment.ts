import type { PageQuery, PageResponse } from "@/lib/api/pagination";

/** CASH = Lễ tân thu tiền mặt; VNPAY = thanh toán qua VNPay. */
export type PaymentMethod = "CASH" | "VNPAY";
/** Hành động server cho phép. Màn chỉ đọc nên chỉ có "view". */
export type PaymentAction = "view";

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: "Tiền mặt",
  VNPAY: "VNPay",
};

/** Một phần tử của `items[]` trong GET /api/v1/admin/payments. */
export interface PaymentListItem {
  id: string;
  /** Số phiếu thu, BE quyết định định dạng. */
  receiptNumber: string;
  /** ISO có offset: "2026-10-20T09:15:00+07:00". */
  paidAt: string;
  /** Bệnh nhân được khám (không phải người trả). */
  patientName: string;
  /** "Phí khám + 2 chỉ định". */
  itemSummary: string;
  /** VND nguyên. */
  amount: number;
  method: PaymentMethod;
  /** null khi VNPAY. */
  cashierName: string | null;
  allowedActions: PaymentAction[];
}

/** Một khoản trong phiếu thu. Σ price = amount (không giảm giá). */
export interface PaymentLine {
  name: string;
  price: number;
}

/** GET /api/v1/admin/payments/{id}. */
export interface PaymentDetail extends Omit<PaymentListItem, "itemSummary"> {
  lines: PaymentLine[];
  /** Chỉ với VNPAY; không có dữ liệu thẻ. */
  vnpayTransactionCode: string | null;
}

/** Tổng trên toàn kết quả lọc, không theo trang. totalAmount = cashAmount + vnpayAmount. */
export interface PaymentSummary {
  totalAmount: number;
  cashAmount: number;
  vnpayAmount: number;
}

export type PaymentPageResponse = PageResponse<
  PaymentListItem,
  { summary: PaymentSummary }
>;

/** Bộ lọc màn. `q` đã trim; method "" = Tất cả (toQueryString bỏ đi). */
export interface PaymentFilters {
  from: string;
  to: string;
  q: string;
  method: PaymentMethod | "";
}

export type PaymentListQuery = PaymentFilters & PageQuery;
