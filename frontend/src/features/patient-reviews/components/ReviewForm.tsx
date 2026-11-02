"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState, type FormEvent } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Textarea } from "@/components/ui/textarea";
import { isApiError, toFieldErrorMap, type ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { submitReview } from "../api/myReviewApi";
import {
  REVIEW_TEXT_MAX_LENGTH,
  REVIEW_TEXT_MIN_LENGTH,
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
      className="grid gap-6"
      noValidate
      onSubmit={(event) => void handleSubmit(event)}
    >
      <div className="grid gap-3">
        <p className="text-base font-semibold" id={questionId}>
          Bạn hài lòng ở mức nào?
        </p>
        <RadioGroup
          aria-describedby={errors.rating ? ratingErrorId : undefined}
          aria-labelledby={questionId}
          className="gap-0 divide-y overflow-hidden rounded-lg border bg-card"
          disabled={pending}
          onValueChange={(value: string) => setRating(Number(value))}
          value={rating === null ? "" : String(rating)}
        >
          {RATING_OPTIONS.map((option) => {
            const id = `${formId}-rating-${option.value}`;
            return (
              <div
                className="flex min-h-12 items-center gap-3 px-4 py-3"
                key={option.value}
              >
                <RadioGroupItem id={id} value={String(option.value)} />
                <Label
                  className="flex-1 cursor-pointer text-base font-normal"
                  htmlFor={id}
                >
                  {option.value} sao · {option.label}
                </Label>
              </div>
            );
          })}
        </RadioGroup>
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

      <div className="grid gap-2">
        <Label className="text-base font-semibold" htmlFor={textId}>
          Chia sẻ trải nghiệm của bạn
        </Label>
        <Textarea
          aria-describedby={errors.text ? `${hintId} ${textErrorId}` : hintId}
          aria-invalid={errors.text ? true : undefined}
          className="min-h-32 text-base md:text-base"
          disabled={pending}
          id={textId}
          maxLength={REVIEW_TEXT_MAX_LENGTH}
          onChange={(event) => setText(event.target.value)}
          value={text}
        />
        <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
          <span id={hintId}>Viết ít nhất {REVIEW_TEXT_MIN_LENGTH} ký tự.</span>
          <span className="tabular-nums">
            {text.length}/{REVIEW_TEXT_MAX_LENGTH}
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

      <p className="text-base text-muted-foreground">
        Đánh giá sẽ hiển thị công khai sau khi phòng khám duyệt.
      </p>

      <div className="flex flex-col gap-3 sm:flex-row">
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
