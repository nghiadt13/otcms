import { apiBaseUrl } from "./apiBaseUrl";
import {
  getAccessToken,
  getCsrfToken,
  refreshSession,
} from "@/lib/auth/authSession";
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

async function authorizedHeaders(init: RequestInit) {
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
  // Spring may clear the XSRF cookie after an authenticated mutation. Fetch a fresh
  // cookie and matching header for each write instead of reusing an in-memory token.
  if (isMutation(init)) headers.set("X-CSRF-TOKEN", await getCsrfToken(true));
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

/** Gọi endpoint công khai không cần session; giữ cùng base URL và lỗi API chuẩn. */
export async function publicApiRequest<T>(
  path: `/api/v1/${string}`,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has("Accept")) headers.set("Accept", "application/json");
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers,
    cache: "no-store",
  });
  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

/** Gọi API JSON. `init.signal` huỷ request; 204 trả `undefined`; lỗi ném `ApiError`. */
export async function apiRequest<T>(
  path: `/api/v1/${string}`,
  init: RequestInit = {},
): Promise<T> {
  let response = await send(path, init);
  // CSRF lệch cookie ở request ghi dữ liệu: backend trả 403, hoặc 401 vì bộ lọc CSRF chạy trước
  // bộ lọc JWT nên request còn ẩn danh. Lấy token mới và thử lại đúng một lần.
  if ((response.status === 403 || response.status === 401) && isMutation(init))
    response = await send(path, init);
  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

/** Gọi API ghi dữ liệu bằng XMLHttpRequest để báo tiến trình gửi body theo byte. */
export async function apiRequestWithUploadProgress<T>(
  path: `/api/v1/${string}`,
  init: RequestInit = {},
  onProgress?: (loaded: number, total: number) => void,
): Promise<T> {
  let response = await sendWithUploadProgress(path, init, onProgress);
  if ((response.status === 403 || response.status === 401) && isMutation(init))
    response = await sendWithUploadProgress(path, init, onProgress);
  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

async function sendWithUploadProgress(
  path: `/api/v1/${string}`,
  init: RequestInit,
  onProgress?: (loaded: number, total: number) => void,
): Promise<Response> {
  const headers = await authorizedHeaders(init);
  const request = () => xhrRequest(`${apiBaseUrl}${path}`, init, headers, onProgress);
  const response = await request();
  if (response.status !== 401) return response;

  const session = await refreshSession();
  if (!session) return response;
  headers.set("Authorization", `Bearer ${session.accessToken}`);
  return request();
}

function xhrRequest(
  url: string,
  init: RequestInit,
  headers: Headers,
  onProgress?: (loaded: number, total: number) => void,
): Promise<Response> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const signal = init.signal;
    const abort = () => xhr.abort();
    const cleanup = () => signal?.removeEventListener("abort", abort);

    xhr.open(init.method ?? "GET", url);
    xhr.withCredentials = isMutation(init);
    headers.forEach((value, name) => xhr.setRequestHeader(name, value));
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress?.(event.loaded, event.total);
    };
    xhr.onerror = () => {
      cleanup();
      reject(new TypeError("Không thể kết nối tới máy chủ."));
    };
    xhr.onabort = () => {
      cleanup();
      reject(new DOMException("Request aborted", "AbortError"));
    };
    xhr.onload = () => {
      cleanup();
      const responseHeaders = new Headers();
      for (const line of xhr.getAllResponseHeaders().trim().split(/[\r\n]+/)) {
        const separator = line.indexOf(":");
        if (separator > 0)
          responseHeaders.append(line.slice(0, separator).trim(), line.slice(separator + 1).trim());
      }
      resolve(new Response(xhr.responseText || null, {
        status: xhr.status,
        statusText: xhr.statusText,
        headers: responseHeaders,
      }));
    };
    if (signal) {
      if (signal.aborted) {
        reject(new DOMException("Request aborted", "AbortError"));
        return;
      }
      signal.addEventListener("abort", abort, { once: true });
    }
    xhr.send((init.body as XMLHttpRequestBodyInit | null | undefined) ?? null);
  });
}

async function send(
  path: `/api/v1/${string}`,
  init: RequestInit,
): Promise<Response> {
  const headers = await authorizedHeaders(init);
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers,
    // Cookie XSRF-TOKEN phải đi kèm request ghi dữ liệu sang backend (khác origin).
    credentials: isMutation(init) ? "include" : init.credentials,
    cache: "no-store",
  });
  if (response.status !== 401) return response;

  const session = await refreshSession();
  if (!session) return response;
  headers.set("Authorization", `Bearer ${session.accessToken}`);
  return fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers,
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
