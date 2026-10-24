import { useEffect, type ReactNode } from "react";
import { DetailList } from "@/components/admin/DetailList";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { isApiError } from "@/lib/api/apiError";
import { VISIT_STATUS_LABELS, VISIT_STATUS_TONE } from "@/lib/domain/clinicVisit";
import { notifyError } from "@/lib/messages/notify";
import { useReceptionVisitSummary } from "../hooks/useReceptionVisitSummary";
import { SEX_LABELS } from "../schema/checkInPresentation";
import { summaryCheckInLine, summaryQueueLine } from "../schema/receptionQueuePresentation";
import type { ReceptionVisitSummary } from "../types/receptionQueue";

interface ReceptionVisitSummarySheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Dòng đang xem. Giữ lại khi đóng để nội dung không biến mất trong lúc Sheet đóng. */
  queueEntryId: string | null;
  /** Gọi khi bản ghi đã rời hàng (404/410): Sheet đóng và bảng tải lại. */
  onGone: () => void;
}

/** Tóm tắt một lượt khám khi chọn dòng. Chỉ đọc: không có thao tác hàng đợi nào ở đây. */
export function ReceptionVisitSummarySheet({
  open,
  onOpenChange,
  queueEntryId,
  onGone,
}: ReceptionVisitSummarySheetProps) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-lg">
        {queueEntryId ? (
          <SummaryBody key={queueEntryId} queueEntryId={queueEntryId} onGone={onGone} />
        ) : null}
      </SheetContent>
    </Sheet>
  );
}

function isGone(error: unknown): boolean {
  return isApiError(error) && (error.status === 404 || error.status === 410);
}

function isForbidden(error: unknown): boolean {
  return isApiError(error) && error.status === 403;
}

function SummaryBody({ queueEntryId, onGone }: { queueEntryId: string; onGone: () => void }) {
  const { data, error, loading, reload } = useReceptionVisitSummary(queueEntryId);
  const gone = isGone(error);

  // Bản ghi đã rời hàng sau khi bảng được tải: báo một lần, đóng Sheet và tải lại bảng.
  useEffect(() => {
    if (!gone) return;
    notifyError(error);
    onGone();
  }, [gone, error, onGone]);

  return (
    <>
      <SheetHeader>
        <SheetTitle>{data ? `Số ${data.queueNumber} · ${data.roomName}` : "Lượt khám"}</SheetTitle>
      </SheetHeader>
      {loading ? <SummarySkeleton /> : null}
      {!data && error !== undefined && !gone ? (
        <SummaryError forbidden={isForbidden(error)} onRetry={reload} />
      ) : null}
      {data ? <DetailList items={summaryItems(data)} /> : null}
    </>
  );
}

function summaryItems(summary: ReceptionVisitSummary): { label: string; value: ReactNode }[] {
  return [
    {
      label: "Bệnh nhân",
      value: `${summary.patient.fullName} · ${summary.patient.patientCode} · ${SEX_LABELS[summary.patient.sex]} · ${summary.patient.age} tuổi`,
    },
    { label: "Tiếp nhận lúc", value: summaryCheckInLine(summary) },
    { label: "Bác sĩ phụ trách", value: summary.responsibleDoctorName },
    { label: "Nhóm ưu tiên", value: summary.priorityLabel ?? "Không ưu tiên" },
    { label: "Hàng đợi", value: summaryQueueLine(summary) },
    {
      label: "Lượt khám",
      value: (
        <StatusBadge tone={VISIT_STATUS_TONE[summary.visitStatus]}>
          {VISIT_STATUS_LABELS[summary.visitStatus]}
        </StatusBadge>
      ),
    },
  ];
}

function SummarySkeleton() {
  return (
    <div className="space-y-3" aria-busy="true">
      {Array.from({ length: 6 }, (_, index) => (
        <Skeleton key={index} className="h-5 w-full" />
      ))}
    </div>
  );
}

function SummaryError({ forbidden, onRetry }: { forbidden: boolean; onRetry: () => void }) {
  return (
    <div className="space-y-3">
      <p role="alert" className="text-sm text-destructive">
        {forbidden
          ? "Bạn không có quyền xem lượt khám này."
          : "Không tải được thông tin lượt khám. Kiểm tra kết nối rồi bấm Thử lại."}
      </p>
      {forbidden ? null : (
        <Button variant="outline" size="sm" onClick={onRetry}>
          Thử lại
        </Button>
      )}
    </div>
  );
}
