import { apiRequest } from "@/lib/api/client";
import { ApiError, type MsgCode } from "@/lib/api/apiError";
import type {
  DashboardOverview,
  DashboardPeriod,
} from "../types/dashboardOverview";
export async function getDashboardOverview(
  period: DashboardPeriod,
  signal?: AbortSignal,
): Promise<DashboardOverview> {
  try {
    return await apiRequest(
      `/api/v1/admin/dashboard?${new URLSearchParams(period)}`,
      { signal },
    );
  } catch (error) {
    if (error instanceof ApiError && error.errorCode?.startsWith("MSG-"))
      throw new ApiError(error.status, { code: error.errorCode as MsgCode });
    throw error;
  }
}
