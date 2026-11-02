"use client";

import { listMyReviews } from "../api/myReviewApi";
import { useLoadedItems } from "./useLoadedItems";

/** Đánh giá đã gửi của tài khoản, kèm trạng thái duyệt và phản hồi. */
export function useMyReviews() {
  return useLoadedItems(listMyReviews);
}
