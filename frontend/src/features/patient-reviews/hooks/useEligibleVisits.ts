"use client";

import { listEligibleVisits } from "../api/myReviewApi";
import { useLoadedItems } from "./useLoadedItems";

/** Lượt khám có thể đánh giá của tài khoản (BR-54). */
export function useEligibleVisits() {
  return useLoadedItems(listEligibleVisits);
}
