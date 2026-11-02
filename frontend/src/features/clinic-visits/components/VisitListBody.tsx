"use client";

import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalEmptyState } from "@/components/patient/PortalEmptyState";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { useVisitList } from "../hooks/useVisitList";
import type { VisitWhen } from "../types/visitProgress";
import { VisitLoadError } from "./VisitLoadError";
import { VisitRow } from "./VisitRow";

// Các trạng thái của danh sách lượt khám: đang tải (khung chờ đúng hình dòng), lỗi, trống (câu dạy
// cách dùng), có dòng (list có vạch chia) và "Xem thêm" khi còn trang.

const EMPTY_TEXT: Record<VisitWhen, string> = {
  active:
    "Hôm nay bạn chưa có lượt khám nào. Khi lễ tân tiếp nhận, số thứ tự sẽ hiện ở đây.",
  recent: "Chưa có lượt khám nào.",
};

export function VisitListSkeleton() {
  return (
    <div
      aria-busy="true"
      className="divide-y rounded-lg border bg-card"
      role="status"
    >
      <span className="sr-only">Đang tải lượt khám</span>
      {[0, 1, 2].map((row) => (
        <div className="space-y-2 px-4 py-4" key={row}>
          <Skeleton className="h-5 w-3/4" />
          <Skeleton className="h-5 w-1/2" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      ))}
    </div>
  );
}

export function VisitListBody({
  when,
  patientId,
}: {
  when: VisitWhen;
  patientId?: string;
}) {
  const list = useVisitList(when, patientId);

  if (list.loading) return <VisitListSkeleton />;
  if (list.error)
    return (
      <VisitLoadError
        message={`Không tải được lượt khám. ${list.error}`}
        onRetry={list.reload}
      />
    );
  if (list.items.length === 0)
    return <PortalEmptyState>{EMPTY_TEXT[when]}</PortalEmptyState>;

  return (
    <div className="space-y-4">
      <ul className="divide-y rounded-lg border bg-card">
        {list.items.map((visit) => (
          <VisitRow key={visit.id} visit={visit} />
        ))}
      </ul>
      {list.hasMore ? (
        <button
          className={cn(publicButtonClass("secondary"), "w-full sm:w-auto")}
          disabled={list.loadingMore}
          onClick={list.loadMore}
          type="button"
        >
          {list.loadingMore ? "Đang tải..." : "Xem thêm"}
        </button>
      ) : null}
    </div>
  );
}
