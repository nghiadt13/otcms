import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { formatInteger } from "@/lib/format/formatNumber";
import type { ReviewCounts, ReviewFilters } from "../types/reviewModeration";
import {
  countForTab,
  fromUiValue,
  STATUS_TABS,
  toUiValue,
} from "../utils/reviewModerationLabels";

type ReviewStatusTabsProps = {
  status: ReviewFilters["status"];
  counts: ReviewCounts | null;
  onChange: (status: ReviewFilters["status"]) => void;
};

/** Tab theo trạng thái kèm số đếm toàn phòng khám; cuộn ngang ở màn hẹp. */
export function ReviewStatusTabs({
  status,
  counts,
  onChange,
}: ReviewStatusTabsProps) {
  return (
    <Tabs
      value={toUiValue(status)}
      onValueChange={(value) =>
        onChange(fromUiValue(String(value)) as ReviewFilters["status"])
      }
      className="mb-4"
    >
      <TabsList
        aria-label="Trạng thái đánh giá"
        className="max-w-full justify-start overflow-x-auto"
      >
        {STATUS_TABS.map((tab) => (
          <TabsTrigger
            key={tab.value}
            value={tab.value}
            className="flex-none px-3"
          >
            {tab.label}
            {counts ? (
              <span className="tabular-nums text-muted-foreground">
                {formatInteger(countForTab(tab.value, counts))}
              </span>
            ) : (
              <Skeleton className="h-4 w-5" />
            )}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );
}
