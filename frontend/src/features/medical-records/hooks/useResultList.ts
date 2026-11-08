"use client";

import { useEffect } from "react";
import { usePagedList } from "@/hooks/usePagedList";
import type { PageQuery } from "@/lib/api/pagination";
import { listVisitResults } from "../api/examinationResultApi";
import type {
  ResultVisitListQuery,
  ResultVisitSummary,
} from "../types/examinationResult";

/** Tải một lần tối đa 100 lượt (cỡ trang lớn nhất BE cho) để nhóm tháng, đếm và lọc ở giao diện. */
const RESULT_LIST_PAGE_SIZE = 100;

/**
 * Danh sách lượt khám của hồ sơ đang chọn (`undefined` = tất cả hồ sơ đã gắn).
 * Đổi hồ sơ thì về trang đầu và huỷ request cũ, nhờ `usePagedList`.
 */
export function useResultList(patientId: string | undefined) {
  const list = usePagedList<ResultVisitSummary, ResultVisitListQuery>(
    (query: ResultVisitListQuery & PageQuery, signal: AbortSignal) =>
      listVisitResults(query, signal),
    { patientId },
    RESULT_LIST_PAGE_SIZE,
  );
  const { filters, setFilters } = list;

  // Đồng bộ với `?profileId=` trên URL (nguồn sự thật) mà không tải lại thừa.
  useEffect(() => {
    if (filters.patientId !== patientId) setFilters({ patientId });
  }, [filters.patientId, patientId, setFilters]);

  return list;
}
