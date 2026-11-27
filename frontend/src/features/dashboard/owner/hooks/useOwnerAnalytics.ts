"use client";

import type { DashboardPeriod } from "@/features/dashboard/types/dashboardOverview";
import { getOwnerAnalytics } from "../api/ownerAnalyticsApi";
import { useOwnerPeriodData } from "./useOwnerPeriodData";

/** Số liệu kinh doanh của Owner theo kỳ. */
export function useOwnerAnalytics(period: DashboardPeriod) {
  return useOwnerPeriodData(period, getOwnerAnalytics);
}
