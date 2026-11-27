import { apiRequest } from "@/lib/api/client";
import type { DashboardPeriod } from "@/features/dashboard/types/dashboardOverview";
import type { OwnerAnalytics } from "../types/ownerAnalytics";

/** Số liệu kinh doanh của Owner theo kỳ (tối đa 90 ngày); BE tự tính kỳ liền trước cùng độ dài. */
export function getOwnerAnalytics(
  period: DashboardPeriod,
  signal?: AbortSignal,
): Promise<OwnerAnalytics> {
  return apiRequest<OwnerAnalytics>(
    `/api/v1/owner/analytics?${new URLSearchParams(period)}`,
    { signal },
  );
}
