"use client";

import { useCallback, useEffect, useState } from "react";
import { usePagedList } from "@/hooks/usePagedList";
import type { PageQuery } from "@/lib/api/pagination";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { listModerationReviews } from "../api/reviewModerationApi";
import type {
  ReviewCounts,
  ReviewFilters,
  ReviewListItem,
} from "../types/reviewModeration";

const INITIAL_FILTERS: ReviewFilters = {
  status: "PENDING",
  rating: "",
  replied: "",
  from: "",
  to: "",
};

type DateRange = { from: string; to: string };

/** State màn danh sách: tab, bộ lọc, số đếm tab (cùng response), khoảng ngày nháp. */
export function useReviewModerationList() {
  const [counts, setCounts] = useState<ReviewCounts | null>(null);
  const [dateDraft, setDateDraft] = useState<DateRange>({ from: "", to: "" });
  const [dateError, setDateError] = useState<string | undefined>();

  const fetcher = useCallback(
    async (query: ReviewFilters & PageQuery, signal: AbortSignal) => {
      const result = await listModerationReviews(query, signal);
      if (!signal.aborted) setCounts(result.counts);
      return result;
    },
    [],
  );
  const list = usePagedList<
    ReviewListItem,
    ReviewFilters,
    { counts: ReviewCounts }
  >(fetcher, INITIAL_FILTERS);

  const { data, page, setPage, setFilters } = list;

  // Sau khi duyệt/ẩn làm trang hiện tại hết dòng, lùi về trang cuối còn dữ liệu.
  useEffect(() => {
    if (data && page > 0 && page > data.totalPages - 1) {
      setPage(data.totalPages - 1);
    }
  }, [data, page, setPage]);

  /** Chỉ đưa khoảng ngày vào bộ lọc khi from ≤ to; ngược lại hiện lỗi ngay dưới ô. */
  const changeDateRange = useCallback(
    (range: DateRange) => {
      setDateDraft(range);
      if (range.from && range.to && range.from > range.to) {
        setDateError(formatAppMessage("MSG-54"));
        return;
      }
      setDateError(undefined);
      setFilters(range);
    },
    [setFilters],
  );

  const clearFilters = useCallback(() => {
    setDateDraft({ from: "", to: "" });
    setDateError(undefined);
    setFilters({ rating: "", replied: "", from: "", to: "" });
  }, [setFilters]);

  return {
    ...list,
    counts,
    dateDraft,
    dateError,
    changeDateRange,
    clearFilters,
  };
}
