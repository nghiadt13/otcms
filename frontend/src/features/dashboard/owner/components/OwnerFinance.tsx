"use client";

import { Skeleton } from "@/components/ui/skeleton";
import { useDashboardPeriodFilter } from "@/features/dashboard/hooks/useDashboardPeriodFilter";
import { useOwnerFinance } from "../hooks/useOwnerFinance";
import { FinanceKpiStrip } from "./FinanceKpiStrip";
import { formatPeriodRange, OwnerPeriodPage } from "./OwnerPeriodPage";
import { RefundByPersonPanel } from "./RefundByPersonPanel";
import { RefundListPanel } from "./RefundListPanel";

function LoadingSkeleton() {
  return (
    <div aria-hidden className="space-y-6">
      <Skeleton className="h-32 rounded-xl" />
      <Skeleton className="h-56 rounded-xl" />
      <Skeleton className="h-96 rounded-xl" />
    </div>
  );
}

/** Tài chính cho Chủ phòng khám: tiền đã thu, đã hoàn, chờ hoàn, và ai đã hoàn khoản nào. Chỉ đọc. */
export function OwnerFinance() {
  const filter = useDashboardPeriodFilter("30d");
  const { data, loading, error, retry } = useOwnerFinance(filter.applied);

  return (
    <OwnerPeriodPage
      title="Tài chính"
      description="Tiền đã thu và hoàn lại khách, kèm người xác nhận từng khoản hoàn"
      filter={filter}
      loading={loading}
      error={error}
      retry={retry}
      periodNote={data ? `Kỳ ${formatPeriodRange(data.period)}` : null}
      skeleton={<LoadingSkeleton />}
    >
      {data ? (
        <div className="space-y-6">
          <FinanceKpiStrip summary={data.summary} />
          <RefundByPersonPanel people={data.byPerson} />
          <RefundListPanel refunds={data.refunds} people={data.byPerson} />
        </div>
      ) : null}
    </OwnerPeriodPage>
  );
}
