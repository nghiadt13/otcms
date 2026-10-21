import { clone, mockDelay, mockFail } from "@/lib/api/mockServer";
import {
  DASHBOARD_MAX_RANGE_DAYS,
  periodLengthDays,
  previousPeriodOf,
} from "../lib/dashboardPeriodRules";
import type {
  DashboardOverview,
  DashboardPeriod,
} from "../types/dashboardOverview";
import {
  DASHBOARD_FIXTURE,
  DASHBOARD_LARGE_FIXTURE,
  buildEmptyOverview,
  withoutReviews,
} from "./dashboardMockData";

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * GET /api/v1/admin/dashboard?from=&to=
 * Hiện là mock. Khi nối BE chỉ đổi thân hàm thành
 * `apiRequest<DashboardOverview>(`/api/v1/admin/dashboard${toQueryString(period)}`, { signal })`
 * và xóa các ca `from` đặc biệt bên dưới.
 */
export async function getDashboardOverview(
  period: DashboardPeriod,
  signal?: AbortSignal,
): Promise<DashboardOverview> {
  await mockDelay(400, signal);

  // Giống BE: sai định dạng, from > to hoặc vượt số ngày tối đa là 400 MSG-72.
  if (
    !ISO_DATE.test(period.from) ||
    !ISO_DATE.test(period.to) ||
    period.from > period.to ||
    periodLengthDays(period) > DASHBOARD_MAX_RANGE_DAYS
  )
    mockFail(400, "MSG-72");

  const previousPeriod = previousPeriodOf(period);
  switch (period.from) {
    case "1999-01-01":
      return mockFail(403, "MSG-50");
    case "1998-01-01":
      return mockFail(500, "MSG-04");
    case "1997-01-01":
      return withPeriods(withoutReviews(DASHBOARD_FIXTURE), period);
    case "1996-01-01":
      return withPeriods(DASHBOARD_LARGE_FIXTURE, period);
    case "2000-01-01":
      return buildEmptyOverview(period, previousPeriod);
    default:
      return withPeriods(DASHBOARD_FIXTURE, period);
  }
}

function withPeriods(
  source: DashboardOverview,
  period: DashboardPeriod,
): DashboardOverview {
  return { ...clone(source), period, previousPeriod: previousPeriodOf(period) };
}
