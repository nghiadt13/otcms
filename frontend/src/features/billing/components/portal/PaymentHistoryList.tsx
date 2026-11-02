"use client";

import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Skeleton } from "@/components/ui/skeleton";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  type PaymentHistoryFilter,
  useMyPaymentHistory,
} from "../../hooks/useMyPaymentHistory";
import { PaymentErrorBlock } from "./PaymentErrorBlock";
import { PaymentRow } from "./PaymentRow";
import { LIST_FRAME_CLASS } from "./patientBillingPresentation";

/**
 * Danh sách thanh toán đã hoàn tất theo bộ lọc. Màn cha đặt `key` theo bộ lọc, nên đổi lọc là
 * tải lại từ trang đầu.
 */
export function PaymentHistoryList({
  filters,
}: {
  filters: PaymentHistoryFilter;
}) {
  const { data, loading, error, page, setPage, reload } =
    useMyPaymentHistory(filters);
  const items = data?.items ?? [];
  const totalPages = data?.totalPages ?? 1;

  if (loading) {
    return (
      <div aria-busy="true" className={`${LIST_FRAME_CLASS} p-0`}>
        {[0, 1, 2, 3].map((row) => (
          <div className="px-4 py-4" key={row}>
            <Skeleton className="h-5 w-2/3" />
            <Skeleton className="mt-2 h-4 w-1/3" />
          </div>
        ))}
      </div>
    );
  }
  if (error)
    return (
      <PaymentErrorBlock message={messageFromError(error)} onRetry={reload} />
    );
  if (items.length === 0) {
    return (
      <p className="rounded-lg border bg-card px-4 py-6 text-base text-muted-foreground">
        Chưa có thanh toán nào trong khoảng thời gian này. Chọn lại khoảng ngày
        hoặc để trống để xem tất cả.
      </p>
    );
  }
  return (
    <div className="space-y-4">
      <ul className={LIST_FRAME_CLASS}>
        {items.map((payment) => (
          <PaymentRow key={payment.id} payment={payment} />
        ))}
      </ul>
      {totalPages > 1 ? (
        <nav
          aria-label="Phân trang lịch sử"
          className="flex items-center justify-between gap-3"
        >
          <button
            className={publicButtonClass("secondary")}
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
            type="button"
          >
            Trang trước
          </button>
          <span className="text-base tabular-nums">
            Trang {page + 1} / {totalPages}
          </span>
          <button
            className={publicButtonClass("secondary")}
            disabled={page >= totalPages - 1}
            onClick={() => setPage(page + 1)}
            type="button"
          >
            Trang sau
          </button>
        </nav>
      ) : null}
    </div>
  );
}
