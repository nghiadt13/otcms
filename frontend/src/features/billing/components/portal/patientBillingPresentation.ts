import { isApiError } from "@/lib/api/apiError";
import { toQueryString } from "@/lib/api/pagination";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

// Chữ, đường dẫn và lớp giao diện dùng chung của màn thanh toán khu bệnh nhân. Không có luật nghiệp
// vụ ở đây: chỉ đổi tham số URL và dữ liệu BE thành đường dẫn hiển thị.

/** Tham số URL dạng chuỗi; mảng hoặc rỗng thì coi như không có. */
export function readStringParam(
  value: string | string[] | undefined,
): string | undefined {
  return typeof value === "string" && value !== "" ? value : undefined;
}

/** Ngày `yyyy-MM-dd` từ URL; sai dạng thì bỏ qua (coi như không lọc theo ngày đó). */
export function readDateParam(
  value: string | string[] | undefined,
): string | undefined {
  const text = readStringParam(value);
  return text && /^\d{4}-\d{2}-\d{2}$/.test(text) ? text : undefined;
}

/** Đổi người khám trên URL: bỏ tham số rỗng. */
export function paymentsHref(profileId?: string): string {
  return `${PATIENT_ROUTES.payments}${toQueryString({ profileId })}`;
}

export function historyHref(filter: {
  from?: string;
  to?: string;
  profileId?: string;
}): string {
  return `${PATIENT_ROUTES.paymentHistory}${toQueryString(filter)}`;
}

/** Không tìm thấy hoặc không thuộc phạm vi tài khoản (403/404 của `GET /payments/{id}`). */
export function isMissingRecordError(error: unknown): boolean {
  return isApiError(error) && (error.status === 403 || error.status === 404);
}

/** Khung danh sách: vạch chia trong một khung, cả dòng là link (ui-guidelines mục 9). */
export const LIST_FRAME_CLASS =
  "divide-y overflow-hidden rounded-lg border bg-card";

/**
 * Một dòng của khung danh sách: vùng chạm cao hơn 44px, focus vẽ bên trong dòng để khung cắt
 * không che mất viền focus.
 */
export const LIST_LINK_CLASS =
  "flex min-h-14 items-center justify-between gap-4 px-4 py-3 text-base transition-colors duration-150 hover:bg-accent focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring/50 motion-reduce:transition-none";

/** Liên kết nhỏ trong dòng (phiếu thu, quay lại): vùng chạm tối thiểu 44px. */
export const INLINE_LINK_CLASS =
  "inline-flex min-h-11 items-center gap-1 rounded-md text-base font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50";
