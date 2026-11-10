"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState, type ReactNode } from "react";
import { PortalEmptyState } from "@/components/patient/PortalEmptyState";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { useResultList } from "../hooks/useResultList";
import { ResultFilters } from "./ResultFilters";
import { ResultSummaryCards } from "./ResultSummaryCards";
import { ResultVisitList } from "./ResultVisitList";
import {
  countResults,
  filterByTab,
  isInTimeRange,
  type ResultTab,
  type ResultTimeRange,
} from "./resultVisitPresentation";

/**
 * Màn "Kết quả khám": số liệu đầu trang, rồi danh sách lượt khám lọc theo người khám (`?profileId=`,
 * lọc ở backend), khoảng thời gian và tab trạng thái (lọc ở giao diện trên trang đã tải).
 */
export function ResultListScreen() {
  const router = useRouter();
  const pathname = usePathname();
  const profileId = useSearchParams().get("profileId") || undefined;
  const linked = useLinkedProfiles();
  const { data, loading, error, reload } = useResultList(profileId);
  const [timeRange, setTimeRange] = useState<ResultTimeRange>("ALL");
  const [tab, setTab] = useState<ResultTab>("ALL");

  const changeProfile = (next: string | undefined) =>
    router.replace(
      next ? `${pathname}?profileId=${encodeURIComponent(next)}` : pathname,
    );

  const inRange = (data?.items ?? []).filter((item) =>
    isInTimeRange(item.visitDate, timeRange),
  );
  const counts = countResults(inRange);
  const shown = filterByTab(inRange, tab);
  const tabLabel = (text: string, count: number) => `${text} (${count})`;

  const emptyList = (
    <PortalEmptyState>
      Chưa có kết quả khám nào. Kết quả hiện ở đây khi bác sĩ kết luận lượt
      khám.
    </PortalEmptyState>
  );

  let body: ReactNode;
  if (linked.loading || loading) {
    body = <ListSkeleton />;
  } else if (linked.error) {
    body = <LoadError message={linked.error} onRetry={linked.reload} />;
  } else if (linked.profiles.length === 0) {
    // Chưa điền thông tin cá nhân: chưa có gì để xem, hiện như danh sách trống (chỉ đặt lịch mới bắt buộc).
    body = emptyList;
  } else if (error) {
    body = <LoadError message={error} onRetry={reload} />;
  } else if (!data || data.items.length === 0) {
    body = emptyList;
  } else if (shown.length === 0) {
    body = (
      <PortalEmptyState>
        Không có lượt khám nào khớp bộ lọc. Hãy đổi khoảng thời gian hoặc chọn
        tab khác.
      </PortalEmptyState>
    );
  } else {
    body = <ResultVisitList items={shown} />;
  }

  const showContent =
    !linked.loading &&
    !linked.error &&
    linked.profiles.length > 0 &&
    !loading &&
    !error &&
    !!data &&
    data.items.length > 0;

  return (
    <div className="space-y-8">
      <PortalPageHeader
        title="Kết quả khám"
        description="Theo dõi kết quả thăm khám và hồ sơ y tế của bạn và người thân."
      />
      {showContent ? <ResultSummaryCards counts={counts} /> : null}
      <section
        aria-labelledby="result-list-heading"
        className={showContent ? "space-y-5 border-t pt-8" : "space-y-5"}
      >
        <div>
          <h2 className="text-xl font-semibold" id="result-list-heading">
            Danh sách hồ sơ khám
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Chọn một lượt khám để xem kết luận và thông tin chi tiết.
          </p>
        </div>
        {!linked.loading && !linked.error && linked.profiles.length > 0 ? (
          <ResultFilters
            onProfileChange={changeProfile}
            onTimeRangeChange={setTimeRange}
            profileId={profileId}
            profiles={linked.profiles}
            timeRange={timeRange}
          />
        ) : null}
        {showContent ? (
          <Tabs
            onValueChange={(value) => setTab(value as ResultTab)}
            value={tab}
          >
            <TabsList className="grid h-14! w-full grid-cols-3">
              <TabsTrigger className="text-base" value="ALL">
                {tabLabel("Tất cả", counts.total)}
              </TabsTrigger>
              <TabsTrigger className="text-base" value="COMPLETED">
                {tabLabel("Có kết quả", counts.completed)}
              </TabsTrigger>
              <TabsTrigger className="text-base" value="INCOMPLETE">
                {tabLabel("Đang xử lý", counts.incomplete)}
              </TabsTrigger>
            </TabsList>
            <TabsContent className="mt-4" value={tab}>
              {body}
            </TabsContent>
          </Tabs>
        ) : (
          body
        )}
      </section>
    </div>
  );
}

function ListSkeleton() {
  return (
    <div aria-hidden className="space-y-4">
      {[0, 1].map((row) => (
        <div className="space-y-4 rounded-xl border bg-card p-5" key={row}>
          <div className="flex gap-4">
            <Skeleton className="h-20 w-16" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-6 w-3/5" />
              <Skeleton className="h-4 w-2/5" />
              <Skeleton className="h-4 w-1/3" />
            </div>
          </div>
          <Skeleton className="h-5 w-full" />
        </div>
      ))}
    </div>
  );
}

function LoadError({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <div
      role="alert"
      className="space-y-4 rounded-lg border bg-card px-6 py-8 text-center"
    >
      <p className="text-base text-destructive">{message}</p>
      <Button
        variant="outline"
        className="h-12 px-5 text-base"
        onClick={onRetry}
      >
        Thử lại
      </Button>
    </div>
  );
}
