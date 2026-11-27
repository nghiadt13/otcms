import { apiRequest } from "@/lib/api/client";
import type { DashboardPeriod } from "@/features/dashboard/types/dashboardOverview";
import type { OwnerFinance } from "../types/ownerFinance";

/** Tiền đã thu, đã hoàn, đang chờ hoàn và người xác nhận từng khoản hoàn trong kỳ (tối đa 90 ngày). */
export function getOwnerFinance(
  period: DashboardPeriod,
  signal?: AbortSignal,
): Promise<OwnerFinance> {
  return apiRequest<OwnerFinance>(
    `/api/v1/owner/finance?${new URLSearchParams(period)}`,
    { signal },
  );
}
