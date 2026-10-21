import type { MsgCode } from "@/lib/api/apiError";

/** Khoảng ngày sai (từ sau đến) → MSG-54. Ô trống được phép (bỏ tham số). */
export function validateActivityLogDateRange(
  from: string,
  to: string,
): MsgCode | null {
  if (from && to && from > to) return "MSG-54";
  return null;
}
