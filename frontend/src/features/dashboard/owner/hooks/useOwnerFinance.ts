"use client";

import type { DashboardPeriod } from "@/features/dashboard/types/dashboardOverview";
import { getOwnerFinance } from "../api/ownerFinanceApi";
import { useOwnerPeriodData } from "./useOwnerPeriodData";

/** Tài chính của Owner theo kỳ: đã thu, đã hoàn, chờ hoàn và người hoàn. */
export function useOwnerFinance(period: DashboardPeriod) {
  return useOwnerPeriodData(period, getOwnerFinance);
}
