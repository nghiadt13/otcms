"use client";

import { Star } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState, type FormEvent } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { isApiError, toFieldErrorMap, type ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { submitReview } from "../api/myReviewApi";
import {
  REVIEW_TEXT_MAX_LENGTH,
  REVIEW_TEXT_MIN_LENGTH,
  reviewTextLength,
  validateReviewText,
} from "../schema/myReviewFieldRules";
import type { ReviewableVisit } from "../types/myReview";
import { RATING_OPTIONS } from "../utils/myReviewLabels";

type FieldErrors = { rating?: string; text?: string };

/**
 * Form viết đánh giá một lượt khám. Điểm là nhóm radio (bàn phím được), mỗi mức có nhãn chữ.
 * Gửi xong: MSG-55 rồi về danh sách. 403/409 (MSG-56): về danh sách. 400 (MSG-57): báo dưới ô.
 */
export function ReviewForm({ visit }: { visit: ReviewableVisit }) {
  const router = useRouter();
  const formId = useId();
  const [rating, setRating] = useState<number | null>(null);
  const [text, setText] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [pending, setPending] = useState(false);

  const canSubmit =
    rating !== null && validateReviewText(text) === null && !pending;
  const questionId = `${formId}-question`;
  const ratingErrorId = `${formId}-rating-error`;
  const textId = `${formId}-text`;
  const hintId = `${formId}-hint`;
  const textErrorId = `${formId}-text-error`;
  const characterCount = reviewTextLength(text);
  const overLimit = characterCount > REVIEW_TEXT_MAX_LENGTH;
  const selectedRating = RATING_OPTIONS.find((option) => option.value === rating);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (rating === null || !canSubmit) return;
    setPending(true);
    setErrors({});
    try {
      await submitReview({ visitId: visit.visitId, rating, text: text.trim() });
      notifySuccess("MSG-55");
      router.replace(PATIENT_ROUTES.reviews);
    } catch (err) {
      if (isApiError(err) && err.status === 400) {
        setErrors(fieldErrorsOf(err));
        setPending(false);
      } else if (
        isApiError(err) &&
        (err.status === 403 || err.status === 409)
      ) {
        notifyError(err);
        router.replace(PATIENT_ROUTES.reviews);
      } else {
        notifyError(err);
        setPending(false);
      }
    }
  }

  return (
    <form
      className="grid min-w-0 gap-4 rounded-xl border bg-card p-3 sm:gap-6 sm:p-6"
      noValidate
      onSubmit={(event) => void handleSubmit(event)}
    >
      <div className="grid min-w-0 gap-2 sm:gap-3">
        <p className="text-base font-semibold" id={questionId}>
          Bạn hài lòng ở mức nào?
        </p>
        <div
          aria-describedby={errors.rating ? ratingErrorId : undefined}
          aria-invalid={errors.rating ? true : undefined}
          aria-labelledby={questionId}
          aria-required="true"
          className="grid w-full max-w-sm grid-cols-5 gap-1.5 sm:gap-2"
          role="radiogroup"
        >
          {RATING_OPTIONS.map((option) => {
            const id = `${formId}-rating-${option.value}`;
            const filled = rating !== null && option.value <= rating;
            return (
              <div className="min-w-0" key={option.value}>
                <input
                  aria-label={`${option.value} sao · ${option.label}`}
                  checked={rating === option.value}
                  className="peer sr-only"
                  disabled={pending}
                  id={id}
                  name={`${formId}-rating`}
                  onChange={() => setRating(option.value)}
                  type="radio"
                  value={option.value}
                />
                <Label
                  className="flex min-h-12 cursor-pointer flex-col items-center justify-center gap-1 rounded-lg border bg-background px-1 py-1.5 text-sm font-semibold hover:border-primary peer-checked:border-primary peer-checked:bg-secondary peer-focus-visible:ring-2 peer-focus-visible:ring-ring peer-focus-visible:ring-offset-2 peer-disabled:cursor-default peer-disabled:opacity-60 sm:min-h-14 sm:py-2"
                  htmlFor={id}
                >
                  <Star
                    aria-hidden
                    className={cn(
                      "size-5",
                      filled ? "fill-rating text-rating" : "text-muted-foreground",
                    )}
                  />
                  <span aria-hidden>{option.value}</span>
                </Label>
              </div>
            );
          })}
        </div>
        <p aria-live="polite" className="text-sm text-muted-foreground">
          {selectedRating
            ? `${selectedRating.value}/5 · ${selectedRating.label}`
            : "Chọn từ 1 đến 5 sao."}
        </p>
        {errors.rating ? (
          <p
            className="text-base text-destructive"
            id={ratingErrorId}
            role="alert"
          >
            {errors.rating}
          </p>
        ) : null}
      </div>

      <div className="grid min-w-0 gap-2">
        <Label className="text-base font-semibold" htmlFor={textId}>
          Chia sẻ trải nghiệm của bạn
        </Label>
        <Textarea
          aria-describedby={errors.text ? `${hintId} ${textErrorId}` : hintId}
          aria-invalid={errors.text || overLimit ? true : undefined}
          className="min-h-28 min-w-0 resize-y text-base max-sm:field-sizing-fixed sm:min-h-32 md:text-base"
          disabled={pending}
          id={textId}
          onChange={(event) => setText(event.target.value)}
          value={text}
        />
        <div className="grid grid-cols-[minmax(0,1fr)_auto] items-baseline gap-2 text-sm text-muted-foreground">
          <span id={hintId}>
            Viết từ {REVIEW_TEXT_MIN_LENGTH} đến {REVIEW_TEXT_MAX_LENGTH} ký tự.
          </span>
          <span className={cn("tabular-nums", overLimit && "text-destructive")}>
            {characterCount}/{REVIEW_TEXT_MAX_LENGTH}
          </span>
        </div>
        {errors.text ? (
          <p
            className="text-base text-destructive"
            id={textErrorId}
            role="alert"
          >
            {errors.text}
          </p>
        ) : null}
      </div>

      <p className="text-sm leading-relaxed text-muted-foreground sm:text-base">
        Đánh giá sẽ hiển thị công khai sau khi phòng khám duyệt.
      </p>

      <div className="grid gap-2 sm:flex sm:gap-3">
        <button
          className={publicButtonClass("primary")}
          disabled={!canSubmit}
          type="submit"
        >
          {pending ? "Đang gửi..." : "Gửi đánh giá"}
        </button>
        <Link
          className={publicButtonClass("secondary")}
          href={PATIENT_ROUTES.reviews}
        >
          Quay lại
        </Link>
      </div>
    </form>
  );
}

/** Lỗi 400 gắn vào đúng ô (rating/text). Không gắn ô nào thì báo MSG-57 dưới ô nội dung. */
function fieldErrorsOf(error: ApiError): FieldErrors {
  const codes = toFieldErrorMap(error.fieldErrors);
  const mapped: FieldErrors = {};
  if (codes.rating) mapped.rating = formatAppMessage(codes.rating);
  if (codes.text) mapped.text = formatAppMessage(codes.text);
  if (!mapped.rating && !mapped.text) mapped.text = formatAppMessage("MSG-57");
  return mapped;
}
