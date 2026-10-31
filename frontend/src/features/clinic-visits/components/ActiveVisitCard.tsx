"use client";

import Link from "next/link";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Skeleton } from "@/components/ui/skeleton";
import {
  VISIT_STATUS_LABELS,
  VISIT_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { useVisitProgress } from "../hooks/useVisitProgress";
import type { VisitSummary } from "../types/visitProgress";
import { QueuePositionList } from "./QueuePositionList";
import { visitPatientLabel } from "./visitPresentation";

// Một lượt đang diễn ra trên trang chủ: người khám, trạng thái, số thứ tự ở từng phòng và lối tới
// chi tiết. `primary` chỉ bật cho lượt đầu tiên để trang chủ có đúng một nút chính. Tự tải tiến độ
// riêng (cùng chu kỳ 30 s với trang chi tiết).

export function ActiveVisitCard({
  visit,
  primary = false,
}: {
  visit: VisitSummary;
  primary?: boolean;
}) {
  const { progress, loading, error, reload } = useVisitProgress(visit.id);

  return (
    <section
      aria-label={`Lượt khám của ${visit.patient.fullName}`}
      className="space-y-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-base font-medium break-words">
          {visitPatientLabel(visit.patient)}
        </p>
        <StatusBadge tone={VISIT_STATUS_TONE[visit.status]}>
          {VISIT_STATUS_LABELS[visit.status]}
        </StatusBadge>
      </div>

      {loading ? (
        <div aria-busy="true" className="space-y-2" role="status">
          <span className="sr-only">Đang tải tiến độ</span>
          <Skeleton className="h-24 w-full" />
        </div>
      ) : error || !progress ? (
        <div className="space-y-3">
          <p className="text-base text-destructive" role="alert">
            Không tải được tiến độ. {messageFromError(error)}
          </p>
          <button
            className={publicButtonClass("secondary")}
            onClick={reload}
            type="button"
          >
            Thử lại
          </button>
        </div>
      ) : progress.queues.length > 0 ? (
        <QueuePositionList queues={progress.queues} />
      ) : (
        <p className="text-base text-muted-foreground">
          Hiện bạn không chờ ở phòng nào.
        </p>
      )}

      <Link
        aria-label={`Xem tiến độ của ${visit.patient.fullName}`}
        className={cn(
          publicButtonClass(primary ? "primary" : "secondary"),
          "w-full sm:w-auto",
        )}
        href={PATIENT_ROUTES.visitDetail(visit.id)}
      >
        Xem tiến độ
      </Link>
    </section>
  );
}
