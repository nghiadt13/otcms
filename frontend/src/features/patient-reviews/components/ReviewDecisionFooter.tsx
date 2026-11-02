import { useState } from "react";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { Button } from "@/components/ui/button";
import { SheetFooter } from "@/components/ui/sheet";
import type { ReviewDecision } from "../hooks/useReviewModerationDetail";
import type { ReviewDetail } from "../types/reviewModeration";
import { decisionLabel } from "../utils/reviewModerationLabels";

type ReviewDecisionFooterProps = {
  review: ReviewDetail;
  busy: boolean;
  onDecide: (decision: ReviewDecision) => Promise<boolean>;
  onClose: () => void;
};

/** Nút quyết định chỉ vẽ theo `allowedActions` của server; ẩn/từ chối luôn qua hộp xác nhận. */
export function ReviewDecisionFooter({
  review,
  busy,
  onDecide,
  onClose,
}: ReviewDecisionFooterProps) {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pending, setPending] = useState<ReviewDecision | null>(null);
  const { allowedActions, status } = review;

  async function run(decision: ReviewDecision) {
    setPending(decision);
    await onDecide(decision);
    setPending(null);
  }

  // Một nút nổi bật duy nhất: duyệt hoặc hiện lại; hai việc này không đi cùng nhau.
  const positive: ReviewDecision | null = allowedActions.includes("approve")
    ? "approve"
    : allowedActions.includes("unhide")
      ? "unhide"
      : null;
  const hide = decisionLabel("hide", status);
  const isReject = status === "PENDING";

  return (
    <SheetFooter className="border-t sm:flex-row sm:items-center sm:justify-between">
      {allowedActions.includes("hide") ? (
        <Button
          variant="destructive"
          disabled={busy}
          onClick={() => setConfirmOpen(true)}
        >
          {hide.label}
        </Button>
      ) : (
        <span />
      )}
      <div className="flex flex-col gap-2 sm:flex-row">
        {positive ? (
          <Button disabled={busy} onClick={() => void run(positive)}>
            {pending === positive
              ? decisionLabel(positive, status).pending
              : decisionLabel(positive, status).label}
          </Button>
        ) : null}
        <Button variant="outline" disabled={busy} onClick={onClose}>
          Đóng
        </Button>
      </div>
      <ConfirmActionDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={isReject ? "Từ chối đánh giá này?" : "Ẩn đánh giá này?"}
        description={
          isReject
            ? "Đánh giá sẽ không được hiển thị trên website. Lịch sử kiểm duyệt được giữ."
            : "Đánh giá sẽ không còn hiển thị trên website. Lịch sử kiểm duyệt được giữ."
        }
        confirmLabel={hide.label}
        pendingLabel={hide.pending}
        destructive
        onConfirm={async () => {
          await onDecide("hide");
        }}
      />
    </SheetFooter>
  );
}
