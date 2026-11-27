"use client";

import { Skeleton } from "@/components/ui/skeleton";
import { useDashboardPeriodFilter } from "@/features/dashboard/hooks/useDashboardPeriodFilter";
import { useOwnerAnalytics } from "../hooks/useOwnerAnalytics";
import { BookingFunnelPanel } from "./BookingFunnelPanel";
import { DoctorRevenueTable } from "./DoctorRevenueTable";
import { OwnerKpiStrip } from "./OwnerKpiStrip";
import { formatPeriodRange, OwnerPeriodPage } from "./OwnerPeriodPage";
import { RevenueTrendPanel } from "./RevenueTrendPanel";
import { ServiceRevenueTable } from "./ServiceRevenueTable";

function LoadingSkeleton() {
  return (
    <div aria-hidden className="space-y-6">
      <Skeleton className="h-44 rounded-xl" />
      <div className="grid gap-6 lg:grid-cols-3">
        <Skeleton className="h-96 rounded-xl lg:col-span-2" />
        <Skeleton className="h-96 rounded-xl" />
      </div>
      <Skeleton className="h-72 rounded-xl" />
      <Skeleton className="h-72 rounded-xl" />
    </div>
  );
}

/** Tổng quan kinh doanh cho Chủ phòng khám: doanh thu, vận hành, bác sĩ và dịch vụ theo kỳ. Chỉ đọc. */
export function OwnerDashboard() {
  const filter = useDashboardPeriodFilter("30d");
  const { data, loading, error, retry } = useOwnerAnalytics(filter.applied);

  return (
    <OwnerPeriodPage
      title="Tổng quan kinh doanh"
      description="Doanh thu và vận hành theo kỳ, so với kỳ liền trước cùng độ dài"
      filter={filter}
      loading={loading}
      error={error}
      retry={retry}
      periodNote={
        data
          ? `Kỳ ${formatPeriodRange(data.period)}, so với ${formatPeriodRange(data.previousPeriod)}`
          : null
      }
      skeleton={<LoadingSkeleton />}
    >
      {data ? (
        <div className="space-y-6">
          <OwnerKpiStrip data={data} />
          <div className="grid gap-6 lg:grid-cols-3">
            <RevenueTrendPanel
              daily={data.revenue.daily}
              className="lg:col-span-2"
            />
            <BookingFunnelPanel funnel={data.funnel} />
          </div>
          <DoctorRevenueTable doctors={data.doctors} />
          <ServiceRevenueTable services={data.services} />
        </div>
      ) : null}
    </OwnerPeriodPage>
  );
}
