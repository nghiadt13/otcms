"use client";

import { useEffect } from "react";
import { usePagedList } from "@/hooks/usePagedList";
import type { PageQuery } from "@/lib/api/pagination";
import { listPatientStudies } from "../api/imagingStudyApi";
import type {
  ImagingStudyListQuery,
  PatientImagingStudySummary,
} from "../types/imagingStudy";

/**
 * Ảnh chụp của hồ sơ đang chọn (`undefined` = tất cả hồ sơ đã gắn). Đổi hồ sơ thì về
 * trang đầu và huỷ request cũ, nhờ `usePagedList`.
 */
export function usePatientStudyList(patientId: string | undefined) {
  const list = usePagedList<PatientImagingStudySummary, ImagingStudyListQuery>(
    (query: ImagingStudyListQuery & PageQuery, signal: AbortSignal) =>
      listPatientStudies(query, signal),
    { patientId },
  );
  const { filters, setFilters } = list;

  // Đồng bộ với `?profileId=` trên URL mà không tải lại thừa.
  useEffect(() => {
    if (filters.patientId !== patientId) setFilters({ patientId });
  }, [filters.patientId, patientId, setFilters]);

  return list;
}
