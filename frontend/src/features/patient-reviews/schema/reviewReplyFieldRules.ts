import type { MsgCode } from "@/lib/api/apiError";

export const REVIEW_REPLY_MAX_LENGTH = 1000;

/** Phản hồi hợp lệ: 1–1000 ký tự sau khi cắt khoảng trắng đầu/cuối. Trả MSG-57 nếu sai. */
export function validateReviewReply(text: string): MsgCode | null {
  const length = text.trim().length;
  return length === 0 || length > REVIEW_REPLY_MAX_LENGTH ? "MSG-57" : null;
}
