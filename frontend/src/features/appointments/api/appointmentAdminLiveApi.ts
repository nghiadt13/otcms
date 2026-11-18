import { apiRequest } from "@/lib/api/client";
import { toQueryString, type PageResponse } from "@/lib/api/pagination";
import { todayIsoDate } from "@/lib/format/formatDate";
import type {
  Appointment,
  AppointmentListQuery,
  AppointmentStatus,
  EligibleDoctor,
} from "../types/appointment";

type AdminAppointmentDto = {
  id: string;
  patientProfileId: string;
  patientName: string;
  patientPhone: string | null;
  serviceId: string;
  serviceName: string;
  servicePrice: number | null;
  doctorId: string | null;
  doctorDisplayName: string | null;
  status: string;
  startTime: string;
  examFeePaid: boolean | null;
};

export function getAdminRescheduleSlots(id: string, date: string, signal?: AbortSignal) {
  const params = new URLSearchParams({ from: date, to: date });
  return apiRequest<{ slots: { doctorId: string; doctorDisplayName: string; startTime: string }[] }>(
    `/api/v1/appointments/${encodeURIComponent(id)}/reschedule-availability?${params}`, { signal },
  ).then((result) => result.slots);
}

export function rescheduleAdminAppointment(id: string, doctorId: string, startTime: string, reason: string) {
  return apiRequest(`/api/v1/appointments/${encodeURIComponent(id)}/reschedule`, {
    method: "PATCH", body: JSON.stringify({ doctorId, startTime, reason }),
  });
}

export function cancelAdminAppointment(id: string, reason: string, clinicFault: boolean) {
  return apiRequest(`/api/v1/appointments/${encodeURIComponent(id)}/cancel`, {
    method: "POST", body: JSON.stringify({ reason, clinicFault }),
  });
}

type AdminAppointmentPageDto = {
  items: AdminAppointmentDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

// Backend CONFIRMED ↔ màn hình BOOKED. AWAITING_PAYMENT / PAYMENT_EXPIRED (giữ giờ chờ thanh toán) đã
// được BE ẩn khỏi admin và có trong AppointmentStatus nên không bị hiện chuỗi thô nếu lỡ trả ra.
const appointmentStatus = (status: string): AppointmentStatus =>
  status === "CONFIRMED" ? "BOOKED" : (status as AppointmentStatus);

function toAppointment(dto: AdminAppointmentDto): Appointment {
  const expired = Date.parse(dto.startTime) <= Date.now();
  const status = appointmentStatus(dto.status);
  return {
    id: dto.id,
    startAt: dto.startTime,
    patientName: dto.patientName,
    patientPhone: dto.patientPhone ?? "",
    bookedForDependent: false,
    serviceName: dto.serviceName,
    doctor: dto.doctorId
      ? { id: dto.doctorId, fullName: dto.doctorDisplayName ?? "Bác sĩ" }
      : null,
    status,
    requiresReception: status === "BOOKED" && expired,
    examFeePaid: dto.examFeePaid,
    allowedActions: status === "BOOKED" && !expired ? ["reassign"] : [],
  };
}

/** Candidates for the appointment's existing time; the backend checks shifts and service assignment. */
export async function listAdminReassignmentDoctors(
  appointmentId: string,
  startAt: string,
  signal?: AbortSignal,
): Promise<{ items: EligibleDoctor[] }> {
  const date = todayIsoDate(new Date(startAt));
  const query = new URLSearchParams({ from: date, to: date });
  try {
    const { slots } = await apiRequest<{
      slots: {
        doctorId: string;
        doctorDisplayName: string;
        startTime: string;
      }[];
    }>(
      `/api/v1/appointments/${encodeURIComponent(appointmentId)}/reassignment-availability?${query}`,
      { signal },
    );
    const doctors = new Map<string, EligibleDoctor>();
    for (const slot of slots) {
      if (Date.parse(slot.startTime) === Date.parse(startAt))
        doctors.set(slot.doctorId, {
          id: slot.doctorId,
          fullName: slot.doctorDisplayName,
        });
    }
    return { items: [...doctors.values()] };
  } catch (error) {
    throw error; // BE đã trả mã MSG (MSG-24/30/31/32) ở error.code; không đổi câu ở đây.
  }
}

/** Backend keeps the booked time and validates the candidate again before updating. */
export async function reassignAdminAppointment(
  appointment: Appointment,
  doctorId: string,
  reason: string,
): Promise<Appointment> {
  try {
    const updated = await apiRequest<{
      doctorId: string;
      doctorDisplayName: string;
      startTime: string;
      status: string;
    }>(`/api/v1/appointments/${encodeURIComponent(appointment.id)}/reassign`, {
      method: "POST",
      body: JSON.stringify({ doctorId, reason: reason.trim() }),
    });
    return {
      ...appointment,
      startAt: updated.startTime,
      doctor: { id: updated.doctorId, fullName: updated.doctorDisplayName },
      status: appointmentStatus(updated.status),
    };
  } catch (error) {
    throw error; // BE đã trả mã MSG (MSG-24/30/31/32) ở error.code; không đổi câu ở đây.
  }
}

/** Danh sách lịch đặt thật của Admin. Thông tin liên hệ chỉ trả từ endpoint admin. */
export async function listAdminAppointments(
  query: AppointmentListQuery & { page?: number; size?: number },
  signal?: AbortSignal,
): Promise<PageResponse<Appointment>> {
  const status = query.status === "BOOKED" ? "CONFIRMED" : query.status;
  const params = toQueryString({
    page: query.page ?? 0,
    size: query.size ?? 20,
    from: query.from,
    to: query.to,
    doctorId: query.doctorId,
    status,
  });
  const result = await apiRequest<AdminAppointmentPageDto>(
    `/api/v1/admin/appointments${params}`,
    { signal },
  );
  const items = result.items.map(toAppointment);
  const q = query.q?.trim().toLocaleLowerCase("vi");
  const visibleItems = q
    ? items.filter(
        (item) =>
          item.patientName.toLocaleLowerCase("vi").includes(q) ||
          (item.patientPhone?.includes(query.q!.trim()) ?? false),
      )
    : items;
  return {
    items: visibleItems,
    page: result.page,
    size: result.size,
    totalItems: q ? visibleItems.length : result.totalElements,
    totalPages: q ? 1 : result.totalPages,
  };
}
