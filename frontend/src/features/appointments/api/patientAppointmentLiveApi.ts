import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import { toQueryString } from "@/lib/api/pagination";
import { peekCurrentAccount } from "@/lib/auth/currentAccountApi";
import { listLinkedProfiles } from "@/lib/patient-scope/linkedProfilesApi";
import type { LinkedProfile } from "@/lib/patient-scope/types";
import type { AppointmentStatus } from "@/lib/domain/clinicVisit";
import { vnIsoOf } from "../schema/bookingTime";
import type {
  PatientAppointment,
  PatientAppointmentQuery,
} from "../types/patientAppointment";

// Lớp API THẬT của lịch hẹn khu bệnh nhân (UC23, UC24). Backend: GET /api/v1/appointments (chỉ lịch
// của hồ sơ gắn với tài khoản đăng nhập), GET /api/v1/appointments/{id}, POST .../{id}/cancel.
// Trạng thái phí lấy từ billing qua DTO lịch; giá vẫn là snapshot dịch vụ của lịch.

interface AppointmentDto {
  id: string;
  patientProfileId: string;
  /** Tên hồ sơ người khám (backend có thể bỏ trống nếu hồ sơ không còn). */
  patientName?: string | null;
  serviceId: string;
  serviceName: string | null;
  servicePrice: number | null;
  doctorId: string;
  doctorDisplayName: string | null;
  /** CONFIRMED, AWAITING_PAYMENT, PAYMENT_EXPIRED, CANCELLED, NO_SHOW, CHECKED_IN… */
  status: string;
  /** PATIENT_ONLINE, RECEPTIONIST_ASSISTED hoặc DOCTOR_FOLLOW_UP. */
  bookingSource?: string;
  /** ISO-8601; chỉ có khi status là AWAITING_PAYMENT. */
  paymentDeadline?: string | null;
  startTime: string;
  endTime: string;
  examFeePaid: boolean | null;
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
    case "AWAITING_PAYMENT":
      return "AWAITING_PAYMENT";
    case "PAYMENT_EXPIRED":
      return "PAYMENT_EXPIRED";
    case "CANCELLED":
      return "CANCELLED";
    case "NO_SHOW":
      return "NO_SHOW";
    case "CHECKED_IN":
      return "CHECKED_IN";
    default:
      return "CHECKED_IN";
  }
}

/** Hồ sơ đã gắn theo mã, để ghi đúng tên và quan hệ của người khám. Lỗi tải thì dùng tên trong lịch. */
async function linkedProfilesById(
  signal?: AbortSignal,
): Promise<Map<string, LinkedProfile>> {
  try {
    const { items } = await listLinkedProfiles(signal);
    return new Map(items.map((profile) => [profile.id, profile]));
  } catch {
    return new Map();
  }
}

function toPatientAppointment(
  dto: AppointmentDto,
  profiles: Map<string, LinkedProfile>,
): PatientAppointment {
  const linked = profiles.get(dto.patientProfileId);
  const backendStatus = toStatus(dto.status);
  const expired = Date.parse(dto.startTime) <= Date.now();
  const feePaid = dto.examFeePaid === true;
  // Lịch đã bắt đầu mà chưa thanh toán được đóng như lịch đã hủy. Nếu đã trả,
  // giữ trạng thái để nhân viên xử lý nhưng không cho người bệnh thao tác tiếp.
  const status =
    backendStatus === "BOOKED" && expired && !feePaid
      ? "CANCELLED"
      : backendStatus;
  return {
    id: dto.id,
    startAt: vnIsoOf(new Date(dto.startTime)),
    patient: {
      id: dto.patientProfileId,
      fullName:
        linked?.fullName ??
        dto.patientName ??
        peekCurrentAccount()?.displayName ??
        "",
      relationship: linked?.relationship ?? "SELF",
    },
    visitId: dto.visitId,
    service: { id: dto.serviceId, name: dto.serviceName ?? "Khám bệnh" },
    doctor: { id: dto.doctorId, fullName: dto.doctorDisplayName ?? "" },
    roomName: DEFAULT_ROOM,
    isFollowUp: dto.sourceVisitId !== null,
    status,
    paymentDeadline: dto.paymentDeadline ?? null,
    examFee: {
      amount: dto.servicePrice ?? 0,
      status:
        dto.servicePrice == null
          ? "NOT_REQUIRED"
          : dto.examFeePaid
            ? "PAID"
            : "PENDING_PAYMENT",
    },
    doctorUnavailable: false,
    allowedActions:
      status === "BOOKED" && !expired
        ? dto.servicePrice != null && !dto.examFeePaid
          ? ["cancel", "payExamFee"]
          : ["cancel"]
        : [],
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

/**
 * Backend lọc theo giờ kết thúc và trạng thái trước khi phân trang. Lịch giữ giờ chờ thanh toán
 * (AWAITING_PAYMENT / PAYMENT_EXPIRED) không bao giờ có trong danh sách: BE đã lọc nên FE không đoán
 * hay trừ thêm; `totalItems` lấy thẳng từ BE.
 */
export async function listMyAppointments(
  query: PatientAppointmentQuery & PageQuery,
  signal?: AbortSignal,
): Promise<PageResponse<PatientAppointment>> {
  const window = query.when.toUpperCase() as "UPCOMING" | "PAST";
  const [page, profiles] = await Promise.all([
    apiRequest<AppointmentPageDto>(
      `/api/v1/appointments${toQueryString({
        page: query.page,
        size: query.size,
        window,
        // Lọc theo người khám: không gửi thì backend trả mọi lịch của tài khoản.
        patientProfileId: query.patientId,
      })}`,
      { signal },
    ),
    linkedProfilesById(signal),
  ]);
  return {
    items: page.items.map((dto) => toPatientAppointment(dto, profiles)),
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
    const [dto, profiles] = await Promise.all([
      apiRequest<AppointmentDto>(`/api/v1/appointments/${id}`, { signal }),
      linkedProfilesById(signal),
    ]);
    return toPatientAppointment(dto, profiles);
  } catch (error) {
    throw toAppointmentApiError(error);
  }
}

/** POST /api/v1/appointments/{id}/cancel */
export async function cancelMyAppointment(
  id: string,
): Promise<PatientAppointment> {
  try {
    const dto = await apiRequest<AppointmentDto>(
      `/api/v1/appointments/${id}/cancel`,
      { method: "POST", body: JSON.stringify({}) },
    );
    return toPatientAppointment(dto, await linkedProfilesById());
  } catch (error) {
    throw toAppointmentApiError(error);
  }
}
