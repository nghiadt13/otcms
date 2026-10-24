"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button, buttonVariants } from "@/components/ui/button";
import { isApiError } from "@/lib/api/apiError";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";
import { useReceptionQueue } from "../hooks/useReceptionQueue";
import {
  INITIAL_QUEUE_FILTER,
  matchesReceptionQueueSearch,
  type ReceptionQueueFilterState,
  STATUS_FILTER_STATUSES,
} from "../schema/receptionQueueFilters";
import { refreshedAtText } from "../schema/receptionQueuePresentation";
import { ReceptionQueueFilters } from "./ReceptionQueueFilters";
import { ReceptionQueueTable, ReceptionQueueTotals } from "./ReceptionQueueTable";
import { ReceptionVisitSummarySheet } from "./ReceptionVisitSummarySheet";

const FORBIDDEN_TEXT = "Bạn không có quyền xem hàng đợi tiếp nhận.";
const LOAD_FAILED_TEXT = "Không tải được hàng đợi. Kiểm tra kết nối rồi bấm Thử lại.";
const REFRESH_FAILED_TEXT = "Chưa cập nhật được, thử lại sau";

/** Chữ lỗi khi chưa có dữ liệu: 403 nói rõ là không có quyền, còn lại là lỗi tải chung. */
function loadErrorText(error: unknown): string {
  return isApiError(error) && error.status === 403 ? FORBIDDEN_TEXT : LOAD_FAILED_TEXT;
}

// Màn Hàng đợi tiếp nhận (UC30): chỉ xem. Không có nút gọi số, vắng mặt, đổi phòng hay thứ tự;
// những thao tác đó thuộc UC31–UC33 và chưa có ở đây.
export function ReceptionQueueBoard() {
  const [filter, setFilter] = useState<ReceptionQueueFilterState>(INITIAL_QUEUE_FILTER);
  const queue = useReceptionQueue({
    roomId: filter.roomId || undefined,
    status: STATUS_FILTER_STATUSES[filter.status],
  });
  const [sheetOpen, setSheetOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { data, error, reload } = queue;
  const forbidden = isApiError(error) && error.status === 403;
  const rows = (data?.items ?? []).filter((row) => matchesReceptionQueueSearch(row, filter.search));

  const openRow = useCallback((queueEntryId: string) => {
    setSelectedId(queueEntryId);
    setSheetOpen(true);
  }, []);

  const closeAndReload = useCallback(() => {
    setSheetOpen(false);
    reload();
  }, [reload]);

  const tableError = data === undefined && error !== undefined ? loadErrorText(error) : null;

  // Trống khi không lọc: chưa ai đang chờ (dạy cách dùng). Trống khi có lọc: chỉ nói không khớp.
  const isFiltered =
    filter.roomId !== "" || filter.status !== INITIAL_QUEUE_FILTER.status || filter.search.trim() !== "";
  const emptyText = isFiltered ? (
    <>
      Không có bệnh nhân khớp bộ lọc.{" "}
      <Button variant="link" onClick={() => setFilter(INITIAL_QUEUE_FILTER)}>
        Xóa bộ lọc
      </Button>
    </>
  ) : (
    <>
      Chưa có bệnh nhân nào trong hàng đợi. Bệnh nhân sẽ hiện ở đây sau khi được tiếp nhận.{" "}
      <Link href={STAFF_ROUTES.checkIn} className={buttonVariants({ variant: "link" })}>
        Tiếp nhận bệnh nhân
      </Link>
    </>
  );

  const refreshStatus = data
    ? {
        text: queue.refreshFailed ? REFRESH_FAILED_TEXT : refreshedAtText(data.generatedAt),
        failed: queue.refreshFailed,
      }
    : null;

  return (
    <>
      <PageHeader
        title="Hàng đợi tiếp nhận"
        description="Bệnh nhân đã tiếp nhận hôm nay và vị trí của họ trong hàng đợi các phòng khám."
        actions={
          <Link href={STAFF_ROUTES.checkIn} className={buttonVariants()}>
            Tiếp nhận bệnh nhân
          </Link>
        }
      />
      <div className="space-y-4">
        <ReceptionQueueFilters
          filter={filter}
          rooms={data?.rooms ?? []}
          onChange={setFilter}
          onReload={reload}
          reloading={queue.reloading}
          status={refreshStatus}
        />
        <ReceptionQueueTable
          rows={rows}
          loading={queue.loading}
          error={tableError}
          onRetry={forbidden ? undefined : reload}
          emptyText={emptyText}
          onOpen={openRow}
        />
        {data ? <ReceptionQueueTotals counts={data.counts} /> : null}
      </div>
      <ReceptionVisitSummarySheet
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        queueEntryId={selectedId}
        onGone={closeAndReload}
      />
    </>
  );
}
