import type { AppointmentStatus } from "@/lib/domain/clinicVisit";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";
import { STATUS_VALUES } from "./appointmentUrlRules";

// Hợp đồng URL của danh sách Lễ tân: `?date=&doctorId=&status=`. Từ khóa tìm (`q`) không lên URL
// vì là dữ liệu cá nhân (SĐT, tên).

export interface StaffAppointmentUrlState {
  /** YYYY-MM-DD */
  date: string;
  doctorId?: string;
  status?: AppointmentStatus;
}

type ParamReader = { get(name: string): string | null };

function validDate(value: string | null): string | undefined {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return undefined;
  const parsed = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(parsed.getTime()) ||
    parsed.toISOString().slice(0, 10) !== value
    ? undefined
    : value;
}

/** URL → bộ lọc. Ngày thiếu hoặc sai thì dùng `today` (giờ VN). Trạng thái lạ bị bỏ. */
export function parseStaffAppointmentUrl(
  params: ParamReader,
  today: string,
): StaffAppointmentUrlState {
  const status = params.get("status") as AppointmentStatus | null;
  return {
    date: validDate(params.get("date")) ?? today,
    doctorId: params.get("doctorId") || undefined,
    status: status && STATUS_VALUES.includes(status) ? status : undefined,
  };
}

/** Bộ lọc → query string. Luôn có `date` để đường dẫn tự mô tả ngày đang xem. */
export function buildStaffAppointmentQuery(state: StaffAppointmentUrlState): string {
  const search = new URLSearchParams();
  search.set("date", state.date);
  if (state.doctorId) search.set("doctorId", state.doctorId);
  if (state.status) search.set("status", state.status);
  return `?${search.toString()}`;
}

/** Đường dẫn tiếp nhận của một lịch hẹn (lối tắt từ danh sách, phase 04 xử lý màn đích). */
export const checkInHref = (appointmentId: string): string =>
  `${STAFF_ROUTES.checkIn}?appointmentId=${encodeURIComponent(appointmentId)}`;
