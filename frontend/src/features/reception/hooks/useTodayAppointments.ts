import { listTodayAppointments } from "../api/checkInApi";
import type { TodayAppointmentStatusFilter } from "../types/checkIn";
import { useReceptionLoader } from "./useReceptionLoader";

/** Danh sách lịch hẹn hôm nay theo trạng thái; `reload()` sau khi tiếp nhận xong. */
export function useTodayAppointments(status: TodayAppointmentStatusFilter) {
  return useReceptionLoader(`today:${status}`, (signal) =>
    listTodayAppointments(status, signal),
  );
}
