import { apiRequest } from "@/lib/api/client";
import { toQueryString, type PageResponse } from "@/lib/api/pagination";
import type {
  Appointment,
  AppointmentListQuery,
  AppointmentStatus,
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
};

type AdminAppointmentPageDto = {
  items: AdminAppointmentDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

const appointmentStatus = (status: string): AppointmentStatus =>
  status === "CONFIRMED" ? "BOOKED" : (status as AppointmentStatus);

function toAppointment(dto: AdminAppointmentDto): Appointment {
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
    status: appointmentStatus(dto.status),
    examFeePaid: null,
    allowedActions: [],
  };
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
