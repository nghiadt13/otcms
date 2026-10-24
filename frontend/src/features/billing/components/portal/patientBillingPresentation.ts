import type { StatusTone } from "@/components/admin/StatusBadge";
import { isApiError } from "@/lib/api/apiError";
import { toQueryString } from "@/lib/api/pagination";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { SERVICE_ORDER_STATUS_LABELS, SERVICE_ORDER_STATUS_TONE } from "@/lib/domain/clinicVisit";
import { formatDate, formatTime } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import type {
  PaymentItem,
  PaymentItemStatus,
  PaymentSourceKind,
  PayableSourceSummary,
} from "../../types/patientBilling";

// Chữ, nhãn và đường dẫn dùng chung của các màn thanh toán khu bệnh nhân. Không có luật nghiệp
// vụ ở đây: chỉ đổi dữ liệu BE trả về thành chữ hiển thị và URL.

/** Câu bắt buộc trước khi chuyển sang VNPay: khoản đã trả không hoàn tiền (plan phase 06 §2). */
export const NO_REFUND_NOTICE =
  "Khoản đã thanh toán không được hoàn lại, kể cả khi hủy lịch, không đến hoặc bác sĩ hủy chỉ định.";

export const COUNTER_PAYMENT_HINT = "Hoặc thanh toán tại quầy lễ tân bằng tiền mặt hoặc mã QR.";

/** Câu cố định khi trừ tiền mà chưa có kết quả (UC55, luồng ngoại lệ E3). */
export const RECONCILE_NOTICE =
  "Nếu tài khoản đã bị trừ tiền nhưng trạng thái chưa đổi, vui lòng mang mã giao dịch đến quầy để lễ tân đối soát.";

/** Khoản thanh toán dùng chung nhãn với chỉ định dịch vụ: chờ, đã trả, đã hủy. */
export const PAYABLE_ITEM_STATUS_LABELS: Record<PaymentItemStatus, string> = {
  PENDING_PAYMENT: SERVICE_ORDER_STATUS_LABELS.PENDING_PAYMENT,
  PAID: SERVICE_ORDER_STATUS_LABELS.PAID,
  CANCELLED: SERVICE_ORDER_STATUS_LABELS.CANCELLED,
};

export const PAYABLE_ITEM_STATUS_TONE: Record<PaymentItemStatus, StatusTone> = {
  PENDING_PAYMENT: SERVICE_ORDER_STATUS_TONE.PENDING_PAYMENT,
  PAID: SERVICE_ORDER_STATUS_TONE.PAID,
  CANCELLED: SERVICE_ORDER_STATUS_TONE.CANCELLED,
};

export function isPaymentSourceKind(value: string | undefined): value is PaymentSourceKind {
  return value === "appointment" || value === "visit";
}

export function sourceKindLabel(kind: PaymentSourceKind): string {
  return kind === "appointment" ? "Lịch hẹn" : "Lượt khám";
}

/** "Lượt khám 23/10/2026 · Nguyễn Văn An (Bản thân)". Lịch hẹn có thêm giờ. */
export function payableSourceTitle(
  source: Pick<PayableSourceSummary, "kind" | "date" | "patient">,
): string {
  const when =
    source.kind === "appointment"
      ? `${formatDate(source.date)} ${formatTime(source.date)}`
      : formatDate(source.date);
  const relation = CARE_RELATIONSHIP_LABELS[source.patient.relationship];
  return `${sourceKindLabel(source.kind)} ${when} · ${source.patient.fullName} (${relation})`;
}

/** Trang chi tiết nguồn (`/portal/payments/{kind}/{id}`). */
export function sourceDetailHref(kind: PaymentSourceKind, id: string): string {
  return kind === "appointment" ? PATIENT_ROUTES.payAppointment(id) : PATIENT_ROUTES.payVisit(id);
}

/** Trang xác nhận với nguồn, id và danh sách khoản đã chọn (`?source=&id=&items=`). */
export function paymentConfirmHref(kind: PaymentSourceKind, id: string, itemIds: string[]): string {
  const params = new URLSearchParams({ source: kind, id, items: itemIds.join(",") });
  return `${PATIENT_ROUTES.paymentConfirm}?${params.toString()}`;
}

export function paymentResultHref(paymentId: string): string {
  return `${PATIENT_ROUTES.paymentResult}?${new URLSearchParams({ paymentId }).toString()}`;
}

/** Tách `items=a,b` thành danh sách không trùng, bỏ phần rỗng. */
export function parseItemIds(value: string | undefined): string[] {
  return [...new Set((value ?? "").split(",").map((part) => part.trim()).filter(Boolean))];
}

/** Tham số URL dạng chuỗi; mảng hoặc rỗng thì coi như không có. */
export function readStringParam(value: string | string[] | undefined): string | undefined {
  return typeof value === "string" && value !== "" ? value : undefined;
}

/** Ngày `yyyy-MM-dd` từ URL; sai dạng thì bỏ qua (coi như không lọc theo ngày đó). */
export function readDateParam(value: string | string[] | undefined): string | undefined {
  const text = readStringParam(value);
  return text && /^\d{4}-\d{2}-\d{2}$/.test(text) ? text : undefined;
}

/**
 * Các khoản được chọn để trả, theo đúng thứ tự URL. Chỉ hợp lệ khi có ít nhất một khoản và mọi
 * khoản đều thuộc nguồn này, đang chờ thanh toán. Thiếu một khoản thì `null`: không cho trả.
 */
export function pickPayableItems(items: PaymentItem[], itemIds: string[]): PaymentItem[] | null {
  if (itemIds.length === 0) return null;
  const picked: PaymentItem[] = [];
  for (const itemId of itemIds) {
    const item = items.find((candidate) => candidate.id === itemId);
    if (!item || item.status !== "PENDING_PAYMENT") return null;
    picked.push(item);
  }
  return picked;
}

/** Tổng tạm tính: chỉ cộng khoản đang chờ thanh toán đã chọn. */
export function sumSelectedPending(items: PaymentItem[], selectedIds: ReadonlySet<string>): number {
  return items
    .filter((item) => item.status === "PENDING_PAYMENT" && selectedIds.has(item.id))
    .reduce((total, item) => total + item.amount, 0);
}

/** Đổi người khám trên URL: bỏ tham số rỗng, giữ nguyên khoảng ngày đang áp dụng. */
export function paymentsHref(profileId?: string): string {
  return `${PATIENT_ROUTES.payments}${toQueryString({ profileId })}`;
}

export function historyHref(filter: { from?: string; to?: string; profileId?: string }): string {
  return `${PATIENT_ROUTES.paymentHistory}${toQueryString(filter)}`;
}

/** Không tìm thấy hoặc không thuộc phạm vi tài khoản (403/404 của `GET /payments/{id}`). */
export function isMissingRecordError(error: unknown): boolean {
  return isApiError(error) && (error.status === 403 || error.status === 404);
}

/** Khung danh sách: vạch chia trong một khung, cả dòng là link (ui-guidelines mục 9). */
export const LIST_FRAME_CLASS = "divide-y overflow-hidden rounded-lg border bg-card";

/**
 * Một dòng của khung danh sách: vùng chạm cao hơn 44px, focus vẽ bên trong dòng để khung cắt
 * không che mất viền focus.
 */
export const LIST_LINK_CLASS =
  "flex min-h-14 items-center justify-between gap-4 px-4 py-3 text-base transition-colors duration-150 hover:bg-accent focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring/50 motion-reduce:transition-none";

/** Liên kết nhỏ trong dòng (phiếu thu, quay lại): vùng chạm tối thiểu 44px. */
export const INLINE_LINK_CLASS =
  "inline-flex min-h-11 items-center gap-1 rounded-md text-base font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50";
