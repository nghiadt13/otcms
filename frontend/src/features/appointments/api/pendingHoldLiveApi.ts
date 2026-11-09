import { apiBaseUrl } from "@/lib/api/apiBaseUrl";
import { apiRequest } from "@/lib/api/client";
import {
  getAccessToken,
  getCsrfToken,
  peekCsrfToken,
  peekSession,
} from "@/lib/auth/authSession";

// Lớp API THẬT của hủy giữ chỗ thanh toán (lịch tự đặt có phí đang AWAITING_PAYMENT). Rời trang =
// hủy thanh toán: BE hủy mọi hold còn sống của tài khoản đang đăng nhập, idempotent. Nếu PayOS báo
// đã trả thì BE xác nhận lịch thay vì hủy (`alreadyPaid`).

/** POST /api/v1/appointments/pending-hold/cancel → 200. */
export interface PendingHoldCancelResult {
  /** Số hold đã hủy (0 = không có hold nào, không phải lỗi). */
  cancelledCount: number;
  /** Hold đã được trả trước khi hủy nên BE xác nhận lịch thay vì hủy. */
  alreadyPaid: boolean;
}

const CANCEL_PATH = "/api/v1/appointments/pending-hold/cancel" as const;

export async function cancelPendingHold(
  signal?: AbortSignal,
): Promise<PendingHoldCancelResult> {
  const result = await apiRequest<Partial<PendingHoldCancelResult> | undefined>(
    CANCEL_PATH,
    { method: "POST", signal },
  );
  return {
    cancelledCount:
      typeof result?.cancelledCount === "number" ? result.cancelledCount : 0,
    alreadyPaid: result?.alreadyPaid === true,
  };
}

/**
 * GET /api/v1/appointments/{id}, chỉ lấy trạng thái BE (CONFIRMED, AWAITING_PAYMENT, PAYMENT_EXPIRED,
 * CANCELLED…). Bước thanh toán dò định kỳ để biết giữ chỗ có bị tab khác hủy không; nhẹ hơn
 * `getMyAppointment` vì không tải kèm danh sách hồ sơ. Lịch không còn: ném ApiError 404.
 */
export async function getAppointmentStatus(
  appointmentId: string,
  signal?: AbortSignal,
): Promise<string> {
  const dto = await apiRequest<{ status: string }>(
    `/api/v1/appointments/${encodeURIComponent(appointmentId)}`,
    { signal },
  );
  return dto.status;
}

// Hủy lúc `pagehide` (đóng tab, điều hướng ra ngoài): không chờ được gì bất đồng bộ nên phải có sẵn
// token. `sendBeacon` không gắn được header Authorization/CSRF nên dùng `fetch` + `keepalive`. Chỉ là
// nỗ lực tối đa (token có thể lệch hoặc trình duyệt chặn preflight); lưới an toàn thật là hủy khi tải
// app lần sau và hạn 15 phút của BE.
//
// Token chụp lúc vào bước 5 sẽ cũ: `createAppointmentInvoice`/`createPayOsAttempt` gọi
// `getCsrfToken(true)` làm đổi cookie CSRF, và access token được refresh. Vì vậy `prime` chỉ đảm bảo
// bộ nhớ của authSession có token, còn lúc gửi thì đọc đồng bộ giá trị mới nhất (peek); bản chụp chỉ là
// dự phòng khi peek rỗng.
let keepaliveSnapshot: { accessToken: string; csrfToken: string } | null = null;
/** Tăng mỗi lần `clearCancelKeepalive` để bỏ kết quả của lần prime đang bay (hold đã kết thúc). */
let keepaliveGeneration = 0;

/** Làm ấm token cho `sendCancelKeepalive`. Gọi lặp lại để giữ access token còn hạn. */
export async function primeCancelKeepalive(): Promise<void> {
  const generation = keepaliveGeneration;
  try {
    const accessToken = await getAccessToken();
    const snapshot = accessToken
      ? { accessToken, csrfToken: await getCsrfToken() }
      : null;
    if (generation === keepaliveGeneration) keepaliveSnapshot = snapshot;
  } catch {
    if (generation === keepaliveGeneration) keepaliveSnapshot = null;
  }
}

export function clearCancelKeepalive(): void {
  keepaliveGeneration += 1;
  keepaliveSnapshot = null;
}

/** Gửi yêu cầu hủy mà không đợi kết quả. Trả `false` nếu chưa có token để gửi. */
export function sendCancelKeepalive(): boolean {
  if (!keepaliveSnapshot) return false;
  const accessToken =
    peekSession()?.accessToken ?? keepaliveSnapshot.accessToken;
  const csrfToken = peekCsrfToken() ?? keepaliveSnapshot.csrfToken;
  fetch(`${apiBaseUrl}${CANCEL_PATH}`, {
    method: "POST",
    keepalive: true,
    credentials: "include",
    cache: "no-store",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "X-CSRF-TOKEN": csrfToken,
    },
  }).catch(() => undefined);
  return true;
}
