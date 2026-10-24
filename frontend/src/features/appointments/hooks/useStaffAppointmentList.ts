"use client";

import { useEffect } from "react";
import { usePagedList } from "@/hooks/usePagedList";
import { listStaffAppointments } from "../api/staffAppointmentApi";
import type {
  StaffAppointment,
  StaffAppointmentQuery,
} from "../types/staffAppointment";

/**
 * Danh sách lịch hẹn của Lễ tân theo ngày. `urlFilters` (ngày, bác sĩ, trạng thái) lấy từ URL là
 * nguồn sự thật; `q` giữ cục bộ vì là dữ liệu cá nhân, không đưa lên URL.
 */
export function useStaffAppointmentList(
  urlFilters: Required<Pick<StaffAppointmentQuery, "date">> &
    Pick<StaffAppointmentQuery, "doctorId" | "status">,
  q: string,
) {
  const { date, doctorId, status } = urlFilters;
  const list = usePagedList<StaffAppointment, StaffAppointmentQuery>(
    listStaffAppointments,
    { date, doctorId, status, q },
  );
  const { filters, setFilters } = list;

  // URL (và ô tìm) đổi → bộ lọc của hook; so sánh trước để không tải lại thừa.
  useEffect(() => {
    if (
      filters.date !== date ||
      filters.doctorId !== doctorId ||
      filters.status !== status ||
      filters.q !== q
    )
      setFilters({ date, doctorId, status, q });
  }, [filters, setFilters, date, doctorId, status, q]);

  return list;
}
