import { CalendarDays, Clock, UserRound, Footprints } from "lucide-react";
import type { ReactNode } from "react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import {
  VISIT_STATUS_LABELS,
  VISIT_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { formatTime } from "@/lib/format/formatDate";
import type { VisitProgress, VisitSummary } from "../types/visitProgress";
import {
  visitDateLabel,
  visitPatientLabel,
  visitSourceLabel,
} from "./visitPresentation";

// Phần đầu trang chi tiết: lượt này của ai, ngày nào, tiếp nhận lúc mấy giờ, vào khám bằng cách nào.
// Tiến độ từ BE không có tên người khám và ngày, nên các dòng đó chỉ hiện khi tìm thấy tóm tắt lượt
// trong danh sách; thiếu thì bỏ dòng, không điền chữ thay.

export function VisitSummaryInfo({
  progress,
  summary,
}: {
  progress: VisitProgress;
  summary: VisitSummary | null;
}) {
  return (
    <div className="space-y-4">
      {summary ? (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-xl font-semibold break-words">
            {visitPatientLabel(summary.patient)}
          </p>
          <StatusBadge
            className="h-7 px-3 text-sm"
            tone={VISIT_STATUS_TONE[summary.status]}
          >
            {VISIT_STATUS_LABELS[summary.status]}
          </StatusBadge>
        </div>
      ) : null}
      <dl className="grid gap-x-6 gap-y-3 text-base sm:grid-cols-3">
        {summary ? (
          <Fact
            icon={<CalendarDays aria-hidden className="size-5" />}
            label="Ngày khám"
          >
            {visitDateLabel(summary.visitDate)}
          </Fact>
        ) : null}
        {progress.arrivedAt ? (
          <Fact
            icon={<Clock aria-hidden className="size-5" />}
            label="Tiếp nhận lúc"
          >
            {formatTime(progress.arrivedAt)}
          </Fact>
        ) : null}
        <Fact
          icon={
            progress.source === "WALK_IN" ? (
              <Footprints aria-hidden className="size-5" />
            ) : (
              <UserRound aria-hidden className="size-5" />
            )
          }
          label="Hình thức"
        >
          {visitSourceLabel(progress.source)}
        </Fact>
      </dl>
    </div>
  );
}

function Fact({
  icon,
  label,
  children,
}: {
  icon: ReactNode;
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="flex items-start gap-3">
      <span className="mt-0.5 text-muted-foreground">{icon}</span>
      <div className="min-w-0">
        <dt className="text-sm text-muted-foreground">{label}</dt>
        <dd className="font-medium tabular-nums">{children}</dd>
      </div>
    </div>
  );
}
