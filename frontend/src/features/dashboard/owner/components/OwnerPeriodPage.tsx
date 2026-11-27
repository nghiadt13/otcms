"use client";

import type { ReactNode } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { DashboardPeriodToolbar } from "@/features/dashboard/components/DashboardPeriodToolbar";
import type { useDashboardPeriodFilter } from "@/features/dashboard/hooks/useDashboardPeriodFilter";
import { formatDate } from "@/lib/format/formatDate";

export const formatPeriodRange = (p: { from: string; to: string }) =>
  `${formatDate(p.from)} – ${formatDate(p.to)}`;

/**
 * Khung chung của các trang Owner theo kỳ: tiêu đề, bộ chọn kỳ, dòng trạng thái tải,
 * lỗi kèm "Thử lại", rồi nội dung. `periodNote` là câu mô tả kỳ đang xem khi đã có số liệu.
 */
export function OwnerPeriodPage({
  title,
  description,
  filter,
  loading,
  error,
  retry,
  periodNote,
  skeleton,
  children,
}: {
  title: string;
  description: string;
  filter: ReturnType<typeof useDashboardPeriodFilter>;
  loading: boolean;
  error: string | null;
  retry: () => void;
  /** Câu mô tả kỳ; null khi chưa có số liệu. */
  periodNote: string | null;
  skeleton: ReactNode;
  children: ReactNode;
}) {
  return (
    <>
      <PageHeader title={title} description={description} />
      <DashboardPeriodToolbar
        preset={filter.preset}
        draft={filter.draft}
        error={filter.error ?? undefined}
        loading={loading}
        disabled={false}
        onPresetSelect={filter.selectPreset}
        onDraftChange={filter.setDraft}
        onApply={filter.apply}
      />
      <p className="mb-6 text-sm text-muted-foreground" aria-live="polite">
        {loading ? "Đang tải số liệu..." : error ? null : periodNote}
      </p>
      {loading ? (
        skeleton
      ) : error ? (
        <div
          role="alert"
          className="rounded-xl border bg-card px-4 py-10 text-center"
        >
          <p className="text-sm text-destructive">{error}</p>
          <Button variant="outline" size="sm" className="mt-3" onClick={retry}>
            Thử lại
          </Button>
        </div>
      ) : (
        children
      )}
    </>
  );
}
