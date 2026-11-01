import { ApiError, type MsgCode } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import type { PageQuery } from "@/lib/api/pagination";
import type {
  ActivityLogDetail,
  ActivityLogFilters,
  ActivityLogPage,
} from "../types/activityLog";
const root = "/api/v1/admin/activity-logs" as const;
export function listActivityLogs(
  query: ActivityLogFilters & PageQuery,
  signal?: AbortSignal,
): Promise<ActivityLogPage> {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== null && value !== undefined && value !== "")
      params.set(key, String(value));
  }
  return apiRequest<ActivityLogPage>(`${root}?${params}`, { signal }).catch(
    rethrow,
  );
}
export function getActivityLog(
  id: string,
  signal?: AbortSignal,
): Promise<ActivityLogDetail> {
  return apiRequest<ActivityLogDetail>(`${root}/${encodeURIComponent(id)}`, {
    signal,
  }).catch(rethrow);
}

function rethrow(error: unknown): never {
  if (
    error instanceof ApiError &&
    !error.code &&
    /^MSG-\d+$/.test(error.errorCode ?? "")
  ) {
    throw new ApiError(error.status, {
      code: error.errorCode as MsgCode,
      errorCode: error.errorCode,
      fieldErrors: error.fieldErrors,
    });
  }
  throw error;
}
