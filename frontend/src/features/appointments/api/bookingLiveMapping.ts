import { ApiError } from "@/lib/api/apiError";
import { addDays } from "@/lib/format/formatDate";
import { dateOfStart, vnIsoOf } from "../schema/bookingTime";
import type { DoctorRef } from "../types/appointment";
import type { BookableService, OpenDay, OpenTime } from "../types/booking";

// Chuyển hình JSON thật của backend sang hình FE dùng. Hàm thuần, không gọi mạng.

/** `GET /api/v1/clinic-services` → một phần tử `items`. */
export interface ClinicServiceDto {
  id: string;
  name: string;
  description: string | null;
  /** Đơn giá VND, JSON number (BE: NUMERIC(12,2), 0 = chưa nhập giá). */
  price: number;
}

/** `GET /api/v1/appointments/availability` → một phần tử `slots`. */
export interface AvailabilitySlotDto {
  doctorId: string;
  doctorDisplayName: string;
  /** Instant UTC, ví dụ 2026-10-29T01:00:00Z */
  startTime: string;
}

/** Giá lấy thẳng từ `price` của backend; mô tả ngắn là hướng dẫn đặt lịch. */
export function toBookableService(dto: ClinicServiceDto): BookableService {
  const details = (dto.description ?? "").trim();
  return {
    id: dto.id,
    name: dto.name,
    price: dto.price,
    bookingDetails: details.length > 0 ? details : null,
  };
}

/**
 * Slot phẳng → ngày → giờ → bác sĩ còn trống. Ngày tính theo giờ VN; ngày không có slot vẫn có mặt
 * (mảng giờ rỗng) để dải ngày hiện "Hết giờ".
 */
export function groupSlotsByDay(
  slots: readonly AvailabilitySlotDto[],
  from: string,
  to: string,
): OpenDay[] {
  const byStart = new Map<string, Map<string, DoctorRef>>();
  for (const slot of slots) {
    const startAt = vnIsoOf(new Date(slot.startTime));
    const doctors = byStart.get(startAt) ?? new Map<string, DoctorRef>();
    doctors.set(slot.doctorId, {
      id: slot.doctorId,
      fullName: slot.doctorDisplayName,
    });
    byStart.set(startAt, doctors);
  }

  const days: OpenDay[] = [];
  for (let date = from; date <= to; date = addDays(date, 1)) {
    const times: OpenTime[] = [...byStart.entries()]
      .filter(([startAt]) => dateOfStart(startAt) === date)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([startAt, doctors]) => ({
        startAt,
        doctors: [...doctors.values()].sort((a, b) =>
          a.fullName.localeCompare(b.fullName, "vi"),
        ),
      }));
    days.push({ date, times });
  }
  return days;
}

/** Mã lỗi thô của backend → mã MSG mà BookingFlow đã biết cách xử lý. */
const BACKEND_TO_MSG: Record<string, `MSG-${string}`> = {
  SLOT_UNAVAILABLE: "MSG-30",
  VISIT_CAPACITY_NOT_AVAILABLE: "MSG-30",
  BOOKING_TIME_IN_PAST: "MSG-30",
  BOOKING_NOTICE_TOO_SHORT: "MSG-30",
  DOCTOR_NOT_ELIGIBLE: "MSG-24",
  SERVICE_NOT_BOOKABLE: "MSG-29",
  PATIENT_PROFILE_NOT_FOUND: "MSG-29",
  VALIDATION_FAILED: "MSG-31",
  BOOKING_WINDOW_VIOLATION: "MSG-31",
};

export function toBookingApiError(error: unknown): unknown {
  if (!(error instanceof ApiError) || !error.errorCode) return error;
  const code = BACKEND_TO_MSG[error.errorCode];
  return code ? new ApiError(error.status, { code }) : error;
}
