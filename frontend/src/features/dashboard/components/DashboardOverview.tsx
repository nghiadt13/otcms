"use client";

import { useMemo } from "react";
import { PageHeader } from "@/components/PageHeader";
import { formatDate } from "@/lib/format/formatDate";
import { useDashboardOverview } from "../hooks/useDashboardOverview";
import { useDashboardPeriodFilter } from "../hooks/useDashboardPeriodFilter";
import { buildDashboardBlocks } from "../lib/dashboardBlockConfig";
import { buildSummaryCells } from "../lib/dashboardSummaryCells";
import type { DashboardPeriod } from "../types/dashboardOverview";
import { DashboardBlockSection } from "./DashboardBlockSection";
import { DashboardLoadingSkeleton } from "./DashboardLoadingSkeleton";
import { DashboardPeriodToolbar } from "./DashboardPeriodToolbar";
import { DashboardStateNotice } from "./DashboardStateNotice";
import { DashboardSummaryStrip } from "./DashboardSummaryStrip";

const formatPeriod = (period: DashboardPeriod) =>
  `${formatDate(period.from)} – ${formatDate(period.to)}`;

/** Bảng điều khiển Admin: chỉ đọc, tóm tắt vận hành theo kỳ. */
export function DashboardOverview() {
  const filter = useDashboardPeriodFilter();
  const { data, loading, failure, retry } = useDashboardOverview(
    filter.applied,
  );

  const cells = useMemo(() => (data ? buildSummaryCells(data) : []), [data]);
  const blocks = useMemo(
    () => (data ? buildDashboardBlocks(data) : []),
    [data],
  );

  // Kỳ sai từ BE (400) hiện dưới ô ngày cùng chỗ với lỗi kiểm ở FE và giữ số cũ.
  const periodError =
    filter.error ??
    (failure?.kind === "invalidPeriod" ? failure.message : undefined);
  const blockingFailure =
    failure && (failure.kind !== "invalidPeriod" || !data) ? failure : null;

  return (
    <>
      <PageHeader
        title="Bảng điều khiển"
        description="Tóm tắt vận hành theo kỳ"
      />
      <DashboardPeriodToolbar
        preset={filter.preset}
        draft={filter.draft}
        error={periodError}
        loading={loading}
        disabled={failure?.kind === "forbidden"}
        onPresetSelect={filter.selectPreset}
        onDraftChange={filter.setDraft}
        onApply={filter.apply}
      />
      <p className="mb-6 text-sm text-muted-foreground" aria-live="polite">
        {loading
          ? "Đang tải số liệu..."
          : data && !blockingFailure
            ? `Kỳ ${formatPeriod(data.period)}, so với ${formatPeriod(data.previousPeriod)}`
            : null}
      </p>
      {loading ? (
        <DashboardLoadingSkeleton />
      ) : blockingFailure ? (
        <DashboardStateNotice failure={blockingFailure} onRetry={retry} />
      ) : data ? (
        <>
          <DashboardSummaryStrip cells={cells} />
          <div className="divide-y">
            {blocks.map((block) => (
              <DashboardBlockSection
                key={block.key}
                block={block}
                period={data.period}
              />
            ))}
          </div>
        </>
      ) : null}
    </>
  );
}
