"use client";

import { useEffect } from "react";
import { usePagedList } from "@/hooks/usePagedList";
import type { PageQuery } from "@/lib/api/pagination";
import { listPrescriptions } from "../api/prescriptionApi";
import type {
  PrescriptionListItem,
  PrescriptionListQuery,
} from "../types/prescription";

/**
 * Đơn thuốc của hồ sơ đang chọn (`undefined` = tất cả hồ sơ đã gắn). Đổi hồ sơ thì về
 * trang đầu và huỷ request cũ, nhờ `usePagedList`.
 */
export function usePrescriptionList(patientId: string | undefined) {
  const list = usePagedList<PrescriptionListItem, PrescriptionListQuery>(
    (query: PrescriptionListQuery & PageQuery, signal: AbortSignal) =>
      listPrescriptions(query, signal),
    { patientId },
  );
  const { filters, setFilters } = list;

  // Đồng bộ với `?profileId=` trên URL mà không tải lại thừa.
  useEffect(() => {
    if (filters.patientId !== patientId) setFilters({ patientId });
  }, [filters.patientId, patientId, setFilters]);

  return list;
}
