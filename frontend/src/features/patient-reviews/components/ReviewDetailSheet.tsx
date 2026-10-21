import { DetailList } from "@/components/admin/DetailList";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDate, formatDateTime } from "@/lib/format/formatDate";
import { useReviewModerationDetail } from "../hooks/useReviewModerationDetail";
import type { ReviewListItem } from "../types/reviewModeration";
import { STATUS_LABEL, STATUS_TONE } from "../utils/reviewModerationLabels";
import { ReviewDecisionFooter } from "./ReviewDecisionFooter";
import { ReviewRatingStars } from "./ReviewRatingStars";
import { ReviewReplySection } from "./ReviewReplySection";

type ReviewDetailSheetProps = {
  /** Dòng đang mở; null = đóng. Hiện ngay dữ liệu dòng, chi tiết (GET) tải sau. */
  selected: ReviewListItem | null;
  onClose: () => void;
  onChanged: () => void;
};

export function ReviewDetailSheet({
  selected,
  onClose,
  onChanged,
}: ReviewDetailSheetProps) {
  const { detail, loading, failed, busy, retry, decide, saveReply } =
    useReviewModerationDetail(selected?.id ?? null, {
      onChanged,
      onGone: onClose,
    });
  const view = detail ?? selected;
  const positiveShown =
    detail?.allowedActions.some((a) => a === "approve" || a === "unhide") ?? false;

  return (
    <Sheet open={selected !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-lg">
        {view ? (
          <>
            <SheetHeader>
              <SheetTitle>{view.patientName}</SheetTitle>
              <SheetDescription className="tabular-nums">
                Gửi lúc {formatDateTime(view.submittedAt)}
              </SheetDescription>
            </SheetHeader>
            <div className="grid flex-1 content-start gap-6 px-4">
              <DetailList
                items={[
                  { label: "Điểm", value: <ReviewRatingStars rating={view.rating} /> },
                  { label: "Ngày khám", value: formatDate(view.visit.visitDate) },
                  { label: "Bác sĩ", value: view.visit.doctorName },
                  { label: "Dịch vụ", value: view.visit.serviceName },
                  {
                    label: "Trạng thái",
                    value: (
                      <StatusBadge tone={STATUS_TONE[view.status]}>
                        {STATUS_LABEL[view.status]}
                      </StatusBadge>
                    ),
                  },
                ]}
              />
              <section className="grid gap-1.5">
                <h3 className="text-sm font-medium">Nội dung đánh giá</h3>
                {view.content ? (
                  <p className="text-sm whitespace-pre-line break-words">{view.content}</p>
                ) : (
                  <p className="text-sm text-muted-foreground">Không có nội dung.</p>
                )}
              </section>
              {detail ? (
                <ReviewReplySection
                  review={detail}
                  busy={busy}
                  primary={!positiveShown}
                  onSave={saveReply}
                />
              ) : failed ? (
                <div className="grid justify-items-start gap-2">
                  <p role="alert" className="text-sm text-destructive">
                    Không mở được đánh giá này.
                  </p>
                  <Button variant="outline" size="sm" onClick={retry}>
                    Thử lại
                  </Button>
                </div>
              ) : loading ? (
                <div className="grid gap-2" aria-hidden>
                  <Skeleton className="h-4 w-40" />
                  <Skeleton className="h-16 w-full" />
                </div>
              ) : null}
            </div>
            {detail ? (
              <ReviewDecisionFooter
                review={detail}
                busy={busy}
                onDecide={decide}
                onClose={onClose}
              />
            ) : (
              <div className="p-4" />
            )}
          </>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}
