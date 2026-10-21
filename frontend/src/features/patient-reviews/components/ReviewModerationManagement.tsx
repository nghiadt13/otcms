"use client";

import { useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { TablePagination } from "@/components/admin/TablePagination";
import { useReviewModerationList } from "../hooks/useReviewModerationList";
import type { ReviewListItem } from "../types/reviewModeration";
import { emptyTextFor, hasExtraFilters } from "../utils/reviewModerationLabels";
import { ReviewDetailSheet } from "./ReviewDetailSheet";
import { ReviewFilterBar } from "./ReviewFilterBar";
import { ReviewStatusTabs } from "./ReviewStatusTabs";
import { ReviewTable } from "./ReviewTable";

/** Màn kiểm duyệt đánh giá: tab → bộ lọc → bảng; chi tiết và phản hồi mở ở Sheet phải. */
export function ReviewModerationManagement() {
  const list = useReviewModerationList();
  const [selected, setSelected] = useState<ReviewListItem | null>(null);
  const { data, filters, loading, error } = list;

  const items = data?.items ?? [];
  const filtered = hasExtraFilters(filters);

  function closeSheet() {
    const id = selected?.id;
    setSelected(null);
    // Trả focus về dòng vừa mở (nếu dòng còn trong bảng).
    if (id)
      requestAnimationFrame(() =>
        document
          .querySelector<HTMLElement>(`[data-review-trigger="${id}"]`)
          ?.focus(),
      );
  }

  return (
    <>
      <PageHeader
        title="Đánh giá bệnh nhân"
        description="Duyệt đánh giá trước khi đưa lên website. Chỉ Admin trả lời nhân danh phòng khám."
      />
      <ReviewStatusTabs
        status={filters.status}
        counts={list.counts}
        onChange={(status) => list.setFilters({ status })}
      />
      <ReviewFilterBar
        filters={filters}
        dateDraft={list.dateDraft}
        dateError={list.dateError}
        onDateChange={list.changeDateRange}
        onFilterChange={list.setFilters}
        onClear={list.clearFilters}
      />
      <ReviewTable
        items={items}
        loading={loading}
        error={error}
        empty={!loading && !error && items.length === 0}
        emptyText={emptyTextFor(filters.status, filtered)}
        onRetry={list.reload}
        onOpen={setSelected}
      />
      {data ? (
        <TablePagination
          page={data.page}
          totalPages={data.totalPages}
          totalItems={data.totalItems}
          onPageChange={list.setPage}
        />
      ) : null}
      <ReviewDetailSheet
        selected={selected}
        onClose={closeSheet}
        onChanged={list.reload}
      />
    </>
  );
}
