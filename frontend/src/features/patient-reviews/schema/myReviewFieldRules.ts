import type { MsgCode } from "@/lib/api/apiError";

// Luật nội dung đánh giá của bệnh nhân. Giới hạn 10–1000 là đề xuất (R3 không nói rõ, xem câu hỏi
// mở P08); BE là nơi kiểm cuối, sai thì trả MSG-57.
export const REVIEW_TEXT_MIN_LENGTH = 10;
export const REVIEW_TEXT_MAX_LENGTH = 1000;

/** Nội dung hợp lệ: 10–1000 ký tự sau khi cắt khoảng trắng đầu/cuối. Trả MSG-57 nếu sai. */
export function validateReviewText(text: string): MsgCode | null {
  const length = text.trim().length;
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
