"use client";

import { useEffect } from "react";
import { usePagedList } from "@/hooks/usePagedList";
import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import { listAdminAppointments } from "../api/appointmentAdminLiveApi";
import { rangeErrorCode } from "../schema/appointmentUrlRules";
import type { Appointment, AppointmentListQuery } from "../types/appointment";

/** Khoảng ngày sai thì không gọi API (MSG-54 hiện dưới "Đến ngày"). */
function fetchAppointments(
  query: AppointmentListQuery & PageQuery,
  signal: AbortSignal,
): Promise<PageResponse<Appointment>> {
  if (rangeErrorCode(query.from, query.to))
    return Promise.resolve({
      items: [],
      page: 0,
      size: query.size ?? 20,
      totalItems: 0,
      totalPages: 1,
    });
  return listAdminAppointments(query, signal);
}

/**
 * Danh sách lịch hẹn. `urlFilters` (từ URL) là nguồn sự thật; `q` giữ cục bộ
 * vì là dữ liệu cá nhân, không đưa lên URL.
 */
export function useAppointmentList(
  urlFilters: Required<Pick<AppointmentListQuery, "from" | "to">> &
    Pick<AppointmentListQuery, "doctorId" | "status">,
  q: string,
) {
  const { from, to, doctorId, status } = urlFilters;
  const list = usePagedList<Appointment, AppointmentListQuery>(
    fetchAppointments,
    { from, to, doctorId, status, q },
  );
  const { filters, setFilters } = list;

  // Đồng bộ URL (và ô tìm) → bộ lọc của hook; so sánh trước để không tải lại thừa.
  useEffect(() => {
    if (
      filters.from !== from ||
      filters.to !== to ||
      filters.doctorId !== doctorId ||
      filters.status !== status ||
      filters.q !== q
    )
      setFilters({ from, to, doctorId, status, q });
  }, [filters, setFilters, from, to, doctorId, status, q]);

  return list;
}
