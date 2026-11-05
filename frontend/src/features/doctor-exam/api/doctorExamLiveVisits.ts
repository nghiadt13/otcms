import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import type { VisitStatus } from "@/lib/domain/clinicVisit";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import type {
  TodayPatient,
  TodayStatus,
  VisitContext,
} from "../types/doctorExam";
import type { MedicalHistory, PatientAppointment } from "./doctorExamApi";
import {
  ageFromBirthDate,
  fetchActiveVisits,
  sexFromGender,
  sourceOfVisit,
  type ActiveVisitDto,
} from "./doctorExamLiveActive";

// Phần "lượt khám" của bản live: danh sách hôm nay, ngữ cảnh bệnh nhân, bắt đầu/hoàn tất,
// tiền sử. Ghép hai nguồn của BE: `GET /appointments` (hẹn của chính bác sĩ, kể cả chưa đến) và
// `GET /doctor/visits/active` (lượt đang khám, gồm cả vãng lai, kèm họ tên/tuổi/giới).

/** Mã bước khám gửi cho BE; BE chỉ nhận mã có trong `otcms.visit.care-step-duration-minutes`. */
export const WALK_IN_CARE_STEP = "CONSULTATION";

const MAX_CONTEXT_PAGES = 5;

type AppointmentDto = {
  id: string;
  patientProfileId: string;
  patientName: string | null;
  serviceName: string;
  doctorId: string;
  status: string;
  startTime: string;
  visitId: string | null;
};
type AppointmentPage = {
  items: AppointmentDto[];
  page?: number;
  totalPages?: number;
};

const TODAY_STATUS: Record<string, TodayStatus | undefined> = {
  CONFIRMED: "NOT_ARRIVED",
  CHECKED_IN: "WAITING_FOR_EXAMINATION",
  IN_PROGRESS: "IN_EXAMINATION",
  COMPLETED: "COMPLETED",
};

function fetchAppointments(
  from: string,
  to: string,
  signal?: AbortSignal,
  page = 0,
) {
  return apiRequest<AppointmentPage>(
    `/api/v1/appointments?from=${from}&to=${to}&size=100&page=${page}`,
    { signal },
  );
}

const STEP_LABEL: Record<string, string> = {
  SCHEDULED_APPOINTMENT: "Khám theo lịch hẹn",
  CONSULTATION: "Khám bệnh",
};

function activeRow(active: ActiveVisitDto): TodayPatient {
  return {
    appointmentId: active.linkedAppointmentId,
    visitId: active.visitId,
    patientProfileId: active.patientProfileId,
    patientName: active.patient.fullName,
    age: ageFromBirthDate(active.patient.dateOfBirth),
    sex: sexFromGender(active.patient.gender),
    patientCode: null,
    serviceName: STEP_LABEL[active.stepCode] ?? "Khám bệnh",
    scheduledAt: active.startedAt,
    source: sourceOfVisit(active.visitSource),
    status: "IN_EXAMINATION",
  };
}

export async function listTodayPatients(
  signal?: AbortSignal,
): Promise<TodayPatient[]> {
  const today = todayIsoDate();
  const [page, active] = await Promise.all([
    fetchAppointments(today, today, signal),
    fetchActiveVisits(signal),
  ]);
  const matched = new Set<string>();
  const rows = page.items.flatMap((item): TodayPatient[] => {
    const current = active.find(
      (a) =>
        (item.visitId !== null && a.visitId === item.visitId) ||
        a.linkedAppointmentId === item.id,
    );
    if (current) matched.add(current.visitId);
    const status = current ? "IN_EXAMINATION" : TODAY_STATUS[item.status];
    if (!status) return []; // đã hủy hoặc không đến: không nằm trong danh sách khám
    return [
      {
        appointmentId: item.id,
        visitId: item.visitId ?? current?.visitId ?? null,
        patientProfileId: item.patientProfileId,
        patientName:
          item.patientName ?? current?.patient.fullName ?? "Bệnh nhân",
        age: current ? ageFromBirthDate(current.patient.dateOfBirth) : null,
        sex: current ? sexFromGender(current.patient.gender) : null,
        patientCode: null,
        serviceName: item.serviceName,
        scheduledAt: item.startTime,
        source: "BOOKED",
        status,
      },
    ];
  });
  // Lượt đang khám không có lịch hẹn hôm nay: vãng lai, hoặc lượt đặt hẹn kéo dài từ ngày trước.
  for (const a of active) if (!matched.has(a.visitId)) rows.push(activeRow(a));
  return rows;
}

export async function getMedicalHistory(
  patientProfileId: string,
  signal?: AbortSignal,
): Promise<MedicalHistory> {
  return apiRequest<MedicalHistory>(
    `/api/v1/patient-profiles/${encodeURIComponent(patientProfileId)}/medical-history`,
    { signal },
  );
}

export function saveMedicalHistory(
  patientProfileId: string,
  value: MedicalHistory,
) {
  return apiRequest<MedicalHistory>(
    `/api/v1/patient-profiles/${encodeURIComponent(patientProfileId)}/medical-history`,
    {
      method: "PUT",
      body: JSON.stringify({
        conditions: value.conditions,
        allergies: value.allergies,
        procedures: value.procedures,
        injuries: value.injuries,
        orthopedicHistory: value.orthopedicHistory,
        version: value.version,
      }),
    },
  );
}

/** Danh sách sắp theo giờ hẹn mới nhất trước; duyệt từng trang (tối đa 5) cho tới khi gặp lượt khám. */
async function findAppointmentOfVisit(
  visitId: string,
  today: string,
  signal?: AbortSignal,
): Promise<AppointmentDto | undefined> {
  const from = addDays(today, -7);
  for (let page = 0; page < MAX_CONTEXT_PAGES; page += 1) {
    const result = await fetchAppointments(from, today, signal, page);
    const found = result.items.find((item) => item.visitId === visitId);
    if (found) return found;
    if (page + 1 >= (result.totalPages ?? 1)) return undefined;
  }
  return undefined;
}

function fetchAppointment(id: string, signal?: AbortSignal) {
  return apiRequest<AppointmentDto>(
    `/api/v1/appointments/${encodeURIComponent(id)}`,
    { signal },
  );
}

const EMPTY_CONTEXT = {
  patientCode: null,
  allergies: "",
  allergiesLoaded: false,
  medicalHistory: "",
} as const;

/** Lượt đang khám: danh tính từ `/doctor/visits/active`, chi tiết hẹn (nếu có) từ lịch hẹn. */
async function contextOfActive(
  active: ActiveVisitDto,
  signal?: AbortSignal,
): Promise<VisitContext> {
  let appointment: AppointmentDto | undefined;
  if (active.linkedAppointmentId) {
    try {
      appointment = await fetchAppointment(active.linkedAppointmentId, signal);
    } catch (error) {
      if (signal?.aborted) throw error; // thiếu chi tiết hẹn không chặn việc khám
    }
  }
  return {
    ...EMPTY_CONTEXT,
    visitId: active.visitId,
    appointmentId: active.linkedAppointmentId,
    patientProfileId: active.patientProfileId,
    doctorProfileId: appointment?.doctorId ?? null,
    patientName: active.patient.fullName,
    age: ageFromBirthDate(active.patient.dateOfBirth),
    sex: sexFromGender(active.patient.gender),
    status: "IN_EXAMINATION",
    serviceName:
      appointment?.serviceName ?? STEP_LABEL[active.stepCode] ?? null,
    scheduledAt: appointment?.startTime ?? active.startedAt,
    source: sourceOfVisit(active.visitSource),
  };
}

/** Lượt chưa/không còn đang khám (chờ khám, đã hoàn tất): chỉ tìm được qua lịch hẹn, thiếu tuổi/giới. */
async function contextOfAppointment(
  visitId: string,
  signal?: AbortSignal,
): Promise<VisitContext> {
  // Lượt khám còn hiệu lực 7 ngày kể từ lần check-in đầu (BR-65/66).
  const appointment = await findAppointmentOfVisit(
    visitId,
    todayIsoDate(),
    signal,
  );
  return {
    ...EMPTY_CONTEXT,
    visitId,
    appointmentId: appointment?.id ?? null,
    patientProfileId: appointment?.patientProfileId ?? null,
    doctorProfileId: appointment?.doctorId ?? null,
    patientName: appointment?.patientName ?? null,
    age: null,
    sex: null,
    status: appointment ? visitStatusOf(appointment.status) : null,
    serviceName: appointment?.serviceName ?? null,
    scheduledAt: appointment?.startTime ?? null,
    source: appointment ? "BOOKED" : null,
  };
}

export async function getVisitContext(
  visitId: string,
  signal?: AbortSignal,
): Promise<VisitContext> {
  const active = (await fetchActiveVisits(signal)).find(
    (item) => item.visitId === visitId,
  );
  const base = active
    ? await contextOfActive(active, signal)
    : await contextOfAppointment(visitId, signal);
  if (!base.patientProfileId) return base;
  try {
    const history = await getMedicalHistory(base.patientProfileId, signal);
    return {
      ...base,
      allergies: history.allergies,
      allergiesLoaded: true,
      medicalHistory: [history.conditions, history.orthopedicHistory]
        .filter(Boolean)
        .join("; "),
    };
  } catch (error) {
    if (signal?.aborted) throw error;
    return base; // thiếu tiền sử không chặn việc khám
  }
}

function visitStatusOf(appointmentStatus: string): VisitStatus | null {
  const mapped = TODAY_STATUS[appointmentStatus];
  return mapped && mapped !== "NOT_ARRIVED" ? mapped : null;
}

const patch = (appointmentId: string, targetStatus: string) =>
  apiRequest<unknown>(
    `/api/v1/appointments/${encodeURIComponent(appointmentId)}/status`,
    { method: "PATCH", body: JSON.stringify({ targetStatus }) },
  );

export async function startExamination(context: VisitContext): Promise<void> {
  if (context.appointmentId) {
    await patch(context.appointmentId, "IN_PROGRESS");
    return;
  }
  await apiRequest<unknown>(
    `/api/v1/visits/${encodeURIComponent(context.visitId)}/care-assignments`,
    { method: "POST", body: JSON.stringify({ stepCode: WALK_IN_CARE_STEP }) },
  );
}

/** Hẹn đặt trước đóng qua lệnh trạng thái của lịch hẹn; vãng lai đóng qua `/closure`. */
export async function completeVisit(context: VisitContext): Promise<void> {
  if (context.appointmentId) {
    await patch(context.appointmentId, "COMPLETED");
    return;
  }
  await apiRequest<void>(
    `/api/v1/visits/${encodeURIComponent(context.visitId)}/closure`,
    { method: "POST" },
  );
}

export async function listPatientAppointments(
  patientProfileId: string,
  signal?: AbortSignal,
): Promise<PatientAppointment[]> {
  const page = await apiRequest<AppointmentPage>(
    `/api/v1/appointments?patientProfileId=${encodeURIComponent(patientProfileId)}&size=50`,
    { signal },
  );
  return page.items.map((item) => ({
    id: item.id,
    serviceName: item.serviceName,
    startTime: item.startTime,
    status: item.status,
  }));
}

export async function bookFollowUp(
  context: VisitContext,
  input: {
    serviceId: string;
    startTime: string;
    purpose: string;
    reason: string;
  },
): Promise<void> {
  if (!context.doctorProfileId) throw new ApiError(400, { code: "MSG-39" });
  await apiRequest<unknown>(
    `/api/v1/visits/${encodeURIComponent(context.visitId)}/follow-up-appointments`,
    {
      method: "POST",
      body: JSON.stringify({ ...input, doctorId: context.doctorProfileId }),
    },
  );
}
