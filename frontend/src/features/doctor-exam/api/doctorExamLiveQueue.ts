import { apiRequest } from "@/lib/api/client";
import {
  completeWalkInVisit,
  getExamQueueEntry,
  listDoctorExamQueue,
  type ExamQueueItem,
} from "@/lib/api/examQueueApi";
import { isApiError } from "@/lib/api/apiError";
import type { ExamQueueStatus, VisitStatus } from "@/lib/domain/clinicVisit";
import { todayIsoDate } from "@/lib/format/formatDate";
import type { TodayPatient, VisitContext } from "../types/doctorExam";
import {
  ageFromBirthDate,
  fetchActiveVisits,
  sexFromGender,
  sourceOfVisit,
  type ActiveVisitDto,
} from "./doctorExamLiveActive";
import {
  getMedicalHistory,
  type AppointmentDto,
  type AppointmentPage,
} from "./doctorExamLiveVisits";

// Phần hàng chờ khám của bản live (plan 261111-0208 mục 3: A2, A6, A8, A9). Hàng chờ
// (`visit_exam_queue`) là nguồn duy nhất cho danh sách hôm nay và trạng thái lượt. Lịch đã xác nhận
// nhưng chưa check-in (chưa có lượt) lấy từ `GET /appointments` và nằm ở nhóm "Chưa đến".

/** Mã bước khám gửi cho BE; BE chỉ nhận mã có trong `otcms.visit.care-step-duration-minutes`. */
export const WALK_IN_CARE_STEP = "CONSULTATION";

/** Tên dịch vụ khi không lấy được lịch hẹn (vãng lai, hoặc lịch của ngày trước). */
const DEFAULT_SERVICE_LABEL = "Khám bệnh";

/** Trạng thái hiển thị của dòng hàng chờ; null với trạng thái bác sĩ không thấy (chờ thu phí, đã hủy). */
function visitStatusOfQueue(
  examStatus: ExamQueueStatus,
): VisitStatus | null {
  switch (examStatus) {
    case "WAITING":
      return "WAITING_FOR_EXAMINATION";
    case "IN_EXAMINATION":
      return "IN_EXAMINATION";
    case "AWAITING_CONCLUSION":
      return "AWAITING_CONCLUSION";
    case "COMPLETED":
      return "COMPLETED";
    default:
      return null;
  }
}

function fetchAppointments(from: string, to: string, signal?: AbortSignal) {
  return apiRequest<AppointmentPage>(
    `/api/v1/appointments?from=${from}&to=${to}&size=100&page=0`,
    { signal },
  );
}

function fetchAppointment(id: string, signal?: AbortSignal) {
  return apiRequest<AppointmentDto>(
    `/api/v1/appointments/${encodeURIComponent(id)}`,
    { signal },
  );
}

/**
 * Danh sách hôm nay: hàng chờ của chính bác sĩ (A2), cộng lịch đã xác nhận chưa đến. Lỗi ở bất kỳ
 * nguồn nào được ném ra để màn hiện lỗi và cho thử lại; không dựng danh sách từ phần còn lại.
 * Thứ tự hiển thị do màn hình sắp (`sortTodayPatients`).
 */
export async function listTodayPatients(
  signal?: AbortSignal,
): Promise<TodayPatient[]> {
  const today = todayIsoDate();
  const [queue, page] = await Promise.all([
    listDoctorExamQueue(),
    fetchAppointments(today, today, signal),
  ]);
  const appointments = new Map<string, AppointmentDto>(
    page.items.map((item): [string, AppointmentDto] => [item.id, item]),
  );
  const queued = new Set<string>();
  const rows: TodayPatient[] = [];
  for (const item of queue.items) {
    const status = visitStatusOfQueue(item.examStatus);
    if (!status) continue;
    if (item.appointmentId) queued.add(item.appointmentId);
    const appointment = item.appointmentId
      ? appointments.get(item.appointmentId)
      : undefined;
    rows.push({
      appointmentId: item.appointmentId,
      visitId: item.visitId,
      patientProfileId: item.patientProfileId,
      patientName: item.patientName,
      age: ageFromBirthDate(item.dateOfBirth),
      sex: sexFromGender(item.gender),
      patientCode: null,
      serviceName: appointment?.serviceName ?? DEFAULT_SERVICE_LABEL,
      scheduledAt: appointment?.startTime ?? item.queuedAt ?? item.startedAt,
      source: sourceOfVisit(item.source),
      status,
      queueNumber: item.queueNumber,
    });
  }
  for (const item of page.items) {
    if (item.status !== "CONFIRMED" || queued.has(item.id)) continue;
    rows.push({
      appointmentId: item.id,
      visitId: null,
      patientProfileId: item.patientProfileId,
      patientName: item.patientName ?? "Bệnh nhân",
      age: null,
      sex: null,
      patientCode: null,
      serviceName: item.serviceName,
      scheduledAt: item.startTime,
      source: "BOOKED",
      status: "NOT_ARRIVED",
      queueNumber: null,
    });
  }
  return rows;
}

/**
 * Dòng hàng chờ của lượt (A6). 404 nghĩa là lượt không nằm trong hàng chờ: trả null, không suy ra
 * từ lịch hẹn. Lỗi khác được ném ra.
 */
async function fetchExamQueueEntry(
  visitId: string,
): Promise<ExamQueueItem | null> {
  try {
    return await getExamQueueEntry(visitId);
  } catch (error) {
    if (isApiError(error) && error.status === 404) return null;
    throw error;
  }
}

/** Chi tiết lịch hẹn chỉ để hiện dịch vụ và giờ hẹn; lỗi ở đây không chặn việc khám (như trước). */
async function fetchLinkedAppointment(
  id: string,
  signal?: AbortSignal,
): Promise<AppointmentDto | null> {
  try {
    return await fetchAppointment(id, signal);
  } catch (error) {
    if (signal?.aborted) throw error;
    return null;
  }
}

const EMPTY_CONTEXT = {
  patientCode: null,
  allergies: "",
  allergiesLoaded: false,
  medicalHistory: "",
} as const;

/** Ngữ cảnh lượt theo hàng chờ; `entry` null khi lượt không có trong hàng chờ. */
async function contextOf(
  visitId: string,
  entry: ExamQueueItem | null,
  current: ActiveVisitDto | undefined,
  signal?: AbortSignal,
): Promise<VisitContext> {
  const appointment = entry?.appointmentId
    ? await fetchLinkedAppointment(entry.appointmentId, signal)
    : null;
  return {
    ...EMPTY_CONTEXT,
    visitId,
    appointmentId: entry?.appointmentId ?? null,
    patientProfileId: entry?.patientProfileId ?? null,
    doctorProfileId: entry?.doctorProfileId ?? null,
    patientName: entry?.patientName ?? null,
    age: current ? ageFromBirthDate(current.patient.dateOfBirth) : null,
    sex: current ? sexFromGender(current.patient.gender) : null,
    status: entry ? visitStatusOfQueue(entry.examStatus) : null,
    serviceName: appointment?.serviceName ?? null,
    scheduledAt: appointment?.startTime ?? entry?.queuedAt ?? null,
    source: entry ? sourceOfVisit(entry.source) : null,
  };
}

/**
 * Ngữ cảnh lượt khám. Trạng thái và danh tính lấy từ hàng chờ (A6), không suy từ lịch hẹn, nên vãng
 * lai đang chờ có đủ trạng thái. Tuổi/giới chỉ có khi lượt đang có bước khám (`/doctor/visits/active`).
 */
export async function getVisitContext(
  visitId: string,
  signal?: AbortSignal,
): Promise<VisitContext> {
  const [entry, active] = await Promise.all([
    fetchExamQueueEntry(visitId),
    fetchActiveVisits(signal),
  ]);
  const base = await contextOf(
    visitId,
    entry,
    active.find((item) => item.visitId === visitId),
    signal,
  );
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

function patchAppointmentStatus(
  appointmentId: string,
  targetStatus: "IN_PROGRESS" | "COMPLETED",
) {
  return apiRequest<unknown>(
    `/api/v1/appointments/${encodeURIComponent(appointmentId)}/status`,
    { method: "PATCH", body: JSON.stringify({ targetStatus }) },
  );
}

/** Bắt đầu khám (A8): lịch hẹn → IN_PROGRESS, walk-in → bước chăm sóc. */
export async function startExamination(context: VisitContext): Promise<void> {
  if (context.appointmentId) {
    await patchAppointmentStatus(context.appointmentId, "IN_PROGRESS");
    return;
  }
  await apiRequest<unknown>(
    `/api/v1/visits/${encodeURIComponent(context.visitId)}/care-assignments`,
    { method: "POST", body: JSON.stringify({ stepCode: WALK_IN_CARE_STEP }) },
  );
}

/** Kết thúc lượt (A9): lịch hẹn đóng qua trạng thái COMPLETED, walk-in qua lệnh completion. */
export async function finishVisit(context: VisitContext): Promise<void> {
  if (context.appointmentId) {
    await patchAppointmentStatus(context.appointmentId, "COMPLETED");
    return;
  }
  await completeWalkInVisit(context.visitId);
}
