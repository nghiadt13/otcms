import { apiRequest } from "@/lib/api/client";
import { addDays } from "@/lib/format/formatDate";
import type { ItemsResponse } from "@/lib/api/pagination";
import { ApiError } from "@/lib/api/apiError";
import { peekCurrentAccount } from "@/lib/auth/currentAccountApi";
import { vnIsoOf } from "../schema/bookingTime";
import type { DoctorRef } from "../types/appointment";
import type {
  BookableService,
  BookedAppointment,
  CreateAppointmentRequest,
  OpenTimesQuery,
  OpenTimesResponse,
} from "../types/booking";
import {
  groupSlotsByDay,
  toBookableService,
  toBookingApiError,
  type AvailabilitySlotDto,
  type ClinicServiceDto,
} from "./bookingLiveMapping";

// Lớp API THẬT của luồng đặt lịch khu bệnh nhân (khu Lễ tân vẫn dùng bookingApi giả). Cùng chữ ký
// với bookingApi để hook dùng chung. Backend: GET /api/v1/clinic-services, GET
// /api/v1/appointments/availability, POST /api/v1/appointments.

const SLOT_MINUTES = 15;
const DEFAULT_DAYS = 14;
const MAX_HORIZON_DAYS = 30;
const DEFAULT_ROOM = "Phòng khám";

// Backend không trả lại tên dịch vụ, tên bác sĩ hay giá trong phản hồi đặt lịch, nên nhớ lại từ
// hai lần GET gần nhất để dựng BookedAppointment.
const servicesById = new Map<string, BookableService>();
const doctorsByStart = new Map<string, DoctorRef[]>();
const doctorsById = new Map<string, DoctorRef>();

const startKey = (serviceId: string, startAt: string) =>
  `${serviceId}|${startAt}`;

/** GET /api/v1/clinic-services (công khai: chỉ dịch vụ đang mở, đặt trực tuyến được). */
export async function listBookableServices(
  signal?: AbortSignal,
): Promise<ItemsResponse<BookableService>> {
  const { items } = await apiRequest<{ items: ClinicServiceDto[] }>(
    "/api/v1/clinic-services",
    { signal },
  );
  const services = items.map(toBookableService);
  for (const service of services) servicesById.set(service.id, service);
  return { items: services };
}

/** GET /api/v1/appointments/availability. `patientId` không gửi: backend dùng tài khoản đăng nhập. */
export async function getOpenTimes(
  query: OpenTimesQuery,
  signal?: AbortSignal,
): Promise<OpenTimesResponse> {
  const now = new Date();
  const today = vnIsoOf(now).slice(0, 10);
  const from = query.from ?? today;
  const horizonEnd = addDays(today, MAX_HORIZON_DAYS);
  const requestedTo = query.to ?? addDays(from, DEFAULT_DAYS - 1);
  const to = requestedTo > horizonEnd ? horizonEnd : requestedTo;

  const params = new URLSearchParams({
    serviceId: query.serviceId,
    from,
    to,
  });
  const { slots } = await apiRequest<{ slots: AvailabilitySlotDto[] }>(
    `/api/v1/appointments/availability?${params.toString()}`,
    { signal },
  );

  const days = groupSlotsByDay(slots, from, to);
  for (const day of days)
    for (const time of day.times) {
      doctorsByStart.set(startKey(query.serviceId, time.startAt), time.doctors);
      for (const doctor of time.doctors) doctorsById.set(doctor.id, doctor);
    }
  return {
    serviceId: query.serviceId,
    from,
    to,
    slotMinutes: SLOT_MINUTES,
    serverNow: vnIsoOf(now),
    days,
  };
}

interface AppointmentDto {
  id: string;
  patientProfileId: string;
  serviceId: string;
  doctorId: string;
  startTime: string;
}

/**
 * POST /api/v1/appointments. `doctorId` null (phòng khám sắp xếp): chọn bác sĩ đầu tiên còn trống ở
 * giờ đó, vì backend bắt buộc `doctorId`. `patientProfileId` bỏ trống: backend dùng hồ sơ của tài
 * khoản. `purpose` bắt buộc ở backend: lấy lý do khám bệnh nhân nhập ở bước xác nhận.
 */
export async function createAppointment(
  body: CreateAppointmentRequest,
  signal?: AbortSignal,
): Promise<BookedAppointment> {
  const service = servicesById.get(body.serviceId);
  const doctorId =
    body.doctorId ??
    doctorsByStart.get(startKey(body.serviceId, body.startAt))?.[0]?.id;
  if (!service || !doctorId) throw new ApiError(400, { code: "MSG-31" });

  let created: AppointmentDto;
  try {
    created = await apiRequest<AppointmentDto>("/api/v1/appointments", {
      method: "POST",
      body: JSON.stringify({
        serviceId: body.serviceId,
        doctorId,
        startTime: body.startAt,
        purpose: body.purpose?.trim() || service.name,
      }),
      signal,
    });
  } catch (error) {
    throw toBookingApiError(error);
  }

  return {
    id: created.id,
    startAt: vnIsoOf(new Date(created.startTime)),
    patient: {
      id: created.patientProfileId,
      fullName: peekCurrentAccount()?.displayName ?? "",
    },
    service: { id: service.id, name: service.name },
    doctor: doctorsById.get(doctorId) ?? { id: doctorId, fullName: "" },
    roomName: DEFAULT_ROOM,
    status: "BOOKED",
    examFee: { amount: service.price, status: "PENDING_PAYMENT" },
    isFollowUp: false,
  };
}
