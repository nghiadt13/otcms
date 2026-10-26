import { useId, useState } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { formatDateTime } from "@/lib/format/formatDate";
import type { ReplyResult } from "../hooks/useReviewModerationDetail";
import type { ReviewDetail } from "../types/reviewModeration";

type ReviewReplySectionProps = {
  review: ReviewDetail;
  /** Đang có thao tác khác chạy trong Sheet. */
  busy: boolean;
  /** true khi không có nút quyết định nào nổi bật hơn → nút phản hồi là `default`. */
  primary: boolean;
  onSave: (text: string) => Promise<ReplyResult>;
};

/** Khối phản hồi của phòng khám: phản hồi hiện có, Sửa, và ô soạn inline (không lồng modal). */
export function ReviewReplySection({
  review,
  busy,
  primary,
  onSave,
}: ReviewReplySectionProps) {
  const fieldId = useId();
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState("");
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [sending, setSending] = useState(false);

  const { reply, allowedActions } = review;
  const canReply = allowedActions.includes("reply");
  const canEdit = Boolean(reply) && allowedActions.includes("editReply");
  const composing = (!reply && canReply) || editing;
  const variant = primary ? "default" : "outline";

  function startEdit() {
    setText(reply?.text ?? "");
    setFieldError(undefined);
    setEditing(true);
  }

  function cancelEdit() {
    setEditing(false);
    setFieldError(undefined);
  }

  async function submit() {
    setSending(true);
    const result = await onSave(text);
    setSending(false);
    if (result.ok) {
      setEditing(false);
      setText("");
      setFieldError(undefined);
    } else {
      setFieldError(result.fieldError);
    }
  }

  return (
    <section aria-labelledby={`${fieldId}-title`} className="grid gap-3">
      <h3 id={`${fieldId}-title`} className="text-sm font-medium">
        Phản hồi của phòng khám
      </h3>

      {reply && !editing ? (
        <div className="grid gap-2 rounded-lg bg-muted p-3 text-sm">
          <p className="whitespace-pre-line break-words">{reply.text}</p>
          <p className="text-xs text-muted-foreground tabular-nums">
            {formatDateTime(reply.repliedAt)}
          </p>
          {review.status === "HIDDEN" ? (
            <p className="text-xs text-muted-foreground">
              Phản hồi không hiển thị công khai vì đánh giá đang ẩn.
            </p>
          ) : null}
          {canEdit ? (
            <Button
              variant={variant}
              size="sm"
              className="w-full sm:w-fit"
              disabled={busy}
              onClick={startEdit}
            >
              Sửa phản hồi
            </Button>
          ) : null}
        </div>
      ) : null}

      {composing ? (
        <div className="grid gap-2">
          <Label htmlFor={fieldId} className="sr-only">
            Nội dung phản hồi
          </Label>
          <Textarea
            id={fieldId}
            value={text}
            readOnly={sending}
            rows={4}
            aria-invalid={fieldError ? true : undefined}
            aria-describedby={`${fieldId}-hint${fieldError ? ` ${fieldId}-error` : ""}`}
            onChange={(event) => setText(event.target.value)}
          />
          <FieldError id={`${fieldId}-error`} message={fieldError} />
          <p id={`${fieldId}-hint`} className="text-xs text-muted-foreground">
            Phản hồi chỉ hiển thị công khai khi đánh giá đang được hiển thị.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            {editing ? (
              <Button variant="ghost" disabled={busy} onClick={cancelEdit}>
                Hủy
              </Button>
            ) : null}
            {text.trim() ? (
              <Button
                variant={variant}
                disabled={busy}
                onClick={() => void submit()}
              >
                {sending ? "Đang gửi..." : "Gửi phản hồi"}
              </Button>
            ) : null}
          </div>
        </div>
      ) : null}

      {!reply && !canReply ? (
        <p className="text-sm text-muted-foreground">Chưa có phản hồi.</p>
      ) : null}
    </section>
  );
}
