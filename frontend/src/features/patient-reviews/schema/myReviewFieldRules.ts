import type { MsgCode } from "@/lib/api/apiError";

// Giới hạn của backend tính theo Unicode code point sau khi cắt khoảng trắng.
export const REVIEW_TEXT_MIN_LENGTH = 10;
export const REVIEW_TEXT_MAX_LENGTH = 1000;

export function reviewTextLength(text: string): number {
  return Array.from(text.trim()).length;
}

/** Nội dung hợp lệ: 10–1000 ký tự sau khi cắt khoảng trắng đầu/cuối. Trả MSG-57 nếu sai. */
export function validateReviewText(text: string): MsgCode | null {
  const length = reviewTextLength(text);
  return length < REVIEW_TEXT_MIN_LENGTH || length > REVIEW_TEXT_MAX_LENGTH
    ? "MSG-57"
    : null;
}

/** Điểm hợp lệ: số nguyên 1–5. Trả MSG-57 nếu sai. */
export function validateReviewRating(rating: number | null): MsgCode | null {
  const valid =
    typeof rating === "number" &&
    Number.isInteger(rating) &&
    rating >= 1 &&
    rating <= 5;
  return valid ? null : "MSG-57";
}
