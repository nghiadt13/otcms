import { apiBaseUrl } from "./apiBaseUrl";
import { getAccessToken, getCsrfToken } from "@/lib/auth/authSession";
import { ApiError, type ApiFieldError, type MsgCode } from "./apiError";

type ProblemDetail = {
  status?: number;
  detail?: string;
  code?: MsgCode;
  params?: Record<string, string | number>;
  fieldErrors?: ApiFieldError[];
  /** Hình lỗi của backend: `{ error: { code, message } }`. */
  error?: { code?: string };
};

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

const isMutation = (init: RequestInit) =>
  !SAFE_METHODS.has((init.method ?? "GET").toUpperCase());

async function authorizedHeaders(init: RequestInit, refreshCsrf = false) {
  const accessToken = await getAccessToken();
  if (!accessToken) throw new Error("Bạn cần đăng nhập để tiếp tục.");

  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${accessToken}`);
  // FormData: để trình duyệt tự đặt Content-Type kèm boundary.
  if (
    init.body &&
    !(init.body instanceof FormData) &&
    !headers.has("Content-Type")
  )
    headers.set("Content-Type", "application/json");
  // Request ghi dữ liệu cần CSRF (xem getCsrfToken); GET không cần.
  if (isMutation(init))
    headers.set("X-CSRF-TOKEN", await getCsrfToken(refreshCsrf));
  return headers;
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail = {};
  try {
    problem = (await response.json()) as ProblemDetail;
  } catch {
    // Không có thân JSON: giữ mã HTTP.
  }
  return new ApiError(response.status, {
    ...problem,
    errorCode: problem.error?.code,
  });
}

/** Gọi API JSON. `init.signal` huỷ request; 204 trả `undefined`; lỗi ném `ApiError`. */
export async function apiRequest<T>(
  path: `/api/v1/${string}`,
  init: RequestInit = {},
): Promise<T> {
  let response = await send(path, init, false);
  // 403 ở request ghi dữ liệu thường là CSRF lệch cookie: lấy token mới và thử lại đúng một lần.
  if (response.status === 403 && isMutation(init))
    response = await send(path, init, true);
  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

async function send(
  path: `/api/v1/${string}`,
  init: RequestInit,
  refreshCsrf: boolean,
): Promise<Response> {
  const headers = await authorizedHeaders(init, refreshCsrf);
  return fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers,
    // Cookie XSRF-TOKEN phải đi kèm request ghi dữ liệu sang backend (khác origin).
    credentials: isMutation(init) ? "include" : init.credentials,
    cache: "no-store",
  });
}

/** Tải tệp nhị phân (xuất báo cáo). Đọc tên tệp từ `Content-Disposition`. */
export async function fetchBlob(
  path: `/api/v1/${string}`,
  init: RequestInit = {},
): Promise<{ blob: Blob; fileName: string }> {
  const headers = await authorizedHeaders(init);
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers,
    cache: "no-store",
  });
  if (!response.ok) throw await toApiError(response);
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename="?([^";]+)"?/i.exec(disposition);
  return {
    blob: await response.blob(),
    fileName: match ? decodeURIComponent(match[1]) : "tai-ve",
  };
}
