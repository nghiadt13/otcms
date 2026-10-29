import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import { toQueryString } from "@/lib/api/pagination";
import { peekCurrentAccount } from "@/lib/auth/currentAccountApi";
import type { AppointmentStatus } from "@/lib/domain/clinicVisit";
import { vnIsoOf } from "../schema/bookingTime";
import type {
  PatientAppointment,
  PatientAppointmentQuery,
} from "../types/patientAppointment";

// Lớp API THẬT của lịch hẹn khu bệnh nhân (UC23, UC24). Backend: GET /api/v1/appointments (chỉ lịch
// của hồ sơ gắn với tài khoản đăng nhập), GET /api/v1/appointments/{id}, POST .../{id}/cancel.
// Backend chưa có phòng khám, quan hệ người khám và thanh toán phí: dùng giá trị mặc định ở dưới.

interface AppointmentDto {
  id: string;
  patientProfileId: string;
  serviceId: string;
  serviceName: string | null;
  servicePrice: number | null;
  doctorId: string;
  doctorDisplayName: string | null;
  status: string;
  startTime: string;
  visitId: string | null;
  sourceVisitId: string | null;
}

interface AppointmentPageDto {
  items: AppointmentDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

const DEFAULT_ROOM = "Phòng khám";

/** Trạng thái backend → trạng thái màn hình (đang khám/đã khám gộp vào "Đã tiếp nhận"). */
function toStatus(status: string): AppointmentStatus {
  switch (status) {
    case "CONFIRMED":
      return "BOOKED";
    case "CANCELLED":
      return "CANCELLED";
    case "NO_SHOW":
      return "NO_SHOW";
    default:
      return "CHECKED_IN";
  }
}

function toPatientAppointment(dto: AppointmentDto): PatientAppointment {
  const status = toStatus(dto.status);
  return {
    id: dto.id,
    startAt: vnIsoOf(new Date(dto.startTime)),
    patient: {
      id: dto.patientProfileId,
      fullName: peekCurrentAccount()?.displayName ?? "",
      relationship: "SELF",
    },
    visitId: dto.visitId,
    service: { id: dto.serviceId, name: dto.serviceName ?? "Khám bệnh" },
    doctor: { id: dto.doctorId, fullName: dto.doctorDisplayName ?? "" },
    roomName: DEFAULT_ROOM,
    isFollowUp: dto.sourceVisitId !== null,
    status,
    examFee: { amount: dto.servicePrice ?? 0, status: "PENDING_PAYMENT" },
    doctorUnavailable: false,
    allowedActions: status === "BOOKED" ? ["cancel"] : [],
  };
}

/** Mã lỗi thô của backend → mã MSG mà màn hình lịch hẹn đã biết cách xử lý. */
const BACKEND_TO_MSG: Record<string, `MSG-${string}`> = {
  APPOINTMENT_NOT_FOUND: "MSG-29",
  APPOINTMENT_NOT_CHANGEABLE: "MSG-32",
  APPOINTMENT_CHANGE_TOO_LATE: "MSG-32",
};

function toAppointmentApiError(error: unknown): unknown {
  if (!(error instanceof ApiError) || !error.errorCode) return error;
  const code = BACKEND_TO_MSG[error.errorCode];
  return code ? new ApiError(error.status, { code }) : error;
}

/** `when` → lọc backend: sắp tới là lịch CONFIRMED, đã qua là các lịch còn lại (lọc nốt ở FE). */
export async function listMyAppointments(
  query: PatientAppointmentQuery & PageQuery,
  signal?: AbortSignal,
): Promise<PageResponse<PatientAppointment>> {
  const upcoming = query.when === "upcoming";
  const page = await apiRequest<AppointmentPageDto>(
    `/api/v1/appointments${toQueryString({
      page: query.page,
      size: query.size,
      status: upcoming ? "CONFIRMED" : undefined,
    })}`,
    { signal },
  );
  const items = page.items
    .map(toPatientAppointment)
    .filter((item) => (item.status === "BOOKED") === upcoming)
    .sort((a, b) =>
      upcoming ? a.startAt.localeCompare(b.startAt) : b.startAt.localeCompare(a.startAt),
    );
  return {
    items,
    page: page.page,
    size: page.size,
    totalItems: page.totalElements,
    totalPages: page.totalPages,
  };
}

/** GET /api/v1/appointments/{id} */
export async function getMyAppointment(
  id: string,
  signal?: AbortSignal,
): Promise<PatientAppointment> {
  try {
    return toPatientAppointment(
      await apiRequest<AppointmentDto>(`/api/v1/appointments/${id}`, {
        signal,
      }),
    );
  } catch (error) {
    throw toAppointmentApiError(error);
  }
}

/** POST /api/v1/appointments/{id}/cancel */
export async function cancelMyAppointment(
  id: string,
): Promise<PatientAppointment> {
  try {
    return toPatientAppointment(
      await apiRequest<AppointmentDto>(`/api/v1/appointments/${id}/cancel`, {
        method: "POST",
        body: JSON.stringify({}),
      }),
    );
  } catch (error) {
    throw toAppointmentApiError(error);
  }
}
