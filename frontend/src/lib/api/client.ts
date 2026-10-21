import { getSupabaseBrowserClient } from "@/lib/supabase/browser";
import { ApiError, type ApiFieldError, type MsgCode } from "./apiError";
import { getStaffAccessToken } from "./staffSession";

const apiBaseUrl =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

type ProblemDetail = {
  status?: number;
  detail?: string;
  code?: MsgCode;
  params?: Record<string, string | number>;
  fieldErrors?: ApiFieldError[];
};

async function authorizedHeaders(init: RequestInit) {
  const staffToken = getStaffAccessToken();
  const {
    data: { session },
  } = staffToken
    ? { data: { session: null } }
    : await getSupabaseBrowserClient().auth.getSession();
  const accessToken = staffToken ?? session?.access_token;
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
  return headers;
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail = {};
  try {
    problem = (await response.json()) as ProblemDetail;
  } catch {
    // Không có thân JSON: giữ mã HTTP.
  }
  return new ApiError(response.status, problem);
}

/** Gọi API JSON. `init.signal` huỷ request; 204 trả `undefined`; lỗi ném `ApiError`. */
export async function apiRequest<T>(
  path: `/api/v1/${string}`,
  init: RequestInit = {},
): Promise<T> {
  const headers = await authorizedHeaders(init);
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers,
    cache: "no-store",
  });
  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
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
