import type {
  AppointmentStatus,
  ExamFeeStatus,
} from "@/lib/domain/clinicVisit";
import { mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import {
  findMockAppointmentFee,
  markMockAppointmentCancelled,
} from "@/lib/mock/sharedAppointmentFeeMock";
import { getLinkedProfileRecords } from "@/lib/patient-scope/linkedProfilesMockStore";
import type { LinkedProfile } from "@/lib/patient-scope/types";
import type { StoredAppointment } from "../types/booking";
import type {
  AppointmentWhen,
  PatientAppointment,
  PatientAppointmentAction,
  PatientAppointmentQuery,
} from "../types/patientAppointment";
import type { RescheduleSlot } from "../components/RescheduleAppointmentDialog";
import { vnIsoAt } from "../schema/bookingTime";
import { isPatientCancellable } from "../schema/patientCancelWindow";
import { BOOKABLE_SERVICES, BOOKING_DOCTORS, doctorById } from "./bookingMockCatalog";
import {
  bookingMockNow,
  cancelStoredAppointment,
  readStoredAppointments,
  rescheduleStoredAppointment,
} from "./bookingMockStore";
import {
  RACE_APPOINTMENT_ID,
  patientOwnSeeds,
  type PatientOwnSeed,
} from "./patientAppointmentMockData";

// LỚP API GIẢ của lịch hẹn khu bệnh nhân (UC23, UC24). Chữ ký khớp REST (xem api-notes/P04.md).
// Nguồn: kho đặt lịch dùng chung (cùng nguồn với khu Lễ tân) và lịch riêng khu bệnh nhân. Chỉ trả
// lịch của hồ sơ đang gắn với tài khoản (BR-19). Khi nối BE: thay thân từng hàm bằng apiRequest.

const latency = (signal?: AbortSignal) =>
  mockDelay(
    process.env.NODE_ENV === "test" ? 0 : 300 + Math.random() * 200,
    signal,
  );

/** Lịch riêng khu bệnh nhân đã hủy trong phiên (lịch dùng chung đổi thẳng trong kho chung). */
const cancelledOwnIds = new Set<string>();
/** Số lần người bệnh đã tự đổi lịch trong phiên mock (tối đa 2). */
const patientRescheduleCounts = new Map<string, number>();

/** Chỉ dùng trong test và demo: quên các lần hủy/đổi lịch riêng khu bệnh nhân. */
export function resetPatientAppointmentMock(): void {
  cancelledOwnIds.clear();
  patientRescheduleCounts.clear();
}

/**
 * Việc làm được theo trạng thái, mô phỏng BE:
 * - Chưa trả phí: hủy khi còn >= 120 phút; trả online khi PENDING_PAYMENT.
 * - Đã trả phí: không tự hủy; tự đổi lịch khi còn >= 120 phút và còn lượt (tối đa 2).
 */
function allowedActionsOf(
  status: AppointmentStatus,
  feeStatus: ExamFeeStatus,
  startAt: string,
  remainingReschedules: number,
): PatientAppointmentAction[] {
  const now = bookingMockNow().getTime();
  if (status !== "BOOKED" || Date.parse(startAt) <= now) return [];
  const actions: PatientAppointmentAction[] = [];
  if (feeStatus !== "PAID" && isPatientCancellable(startAt, now))
    actions.push("cancel");
  if (
    feeStatus === "PAID" &&
    isPatientCancellable(startAt, now) &&
    remainingReschedules > 0
  )
    actions.push("reschedule");
  if (feeStatus === "PENDING_PAYMENT") actions.push("payExamFee");
  return actions;
}

type Projection = Omit<PatientAppointment, "patient" | "allowedActions">;

/** Gắn người khám (tên, quan hệ) từ hồ sơ đã gắn và tính `allowedActions`. */
function withPatient(
  projection: Projection,
  profile: LinkedProfile,
): PatientAppointment {
  const maxPatientReschedules = 2;
  const patientRescheduleCount =
    patientRescheduleCounts.get(projection.id) ?? 0;
  const remainingReschedules = Math.max(
    0,
    maxPatientReschedules - patientRescheduleCount,
  );
  return {
    ...projection,
    patient: {
      id: profile.id,
      fullName: profile.fullName,
      relationship: profile.relationship,
    },
    patientRescheduleCount,
    remainingReschedules,
    maxPatientReschedules,
    allowedActions: allowedActionsOf(
      projection.status,
      projection.examFee.status,
      projection.startAt,
      remainingReschedules,
    ),
  };
}

/** Lịch từ kho dùng chung (bỏ SĐT, mã BN, người đặt). Chưa có check-in trong kho chung. */
function sharedView(
  record: StoredAppointment,
  profile: LinkedProfile,
): PatientAppointment {
  return withPatient(
    {
      id: record.id,
      startAt: record.startAt,
      visitId: null,
      service: record.service,
      doctor: record.doctor,
      roomName: record.roomName,
      isFollowUp: record.isFollowUp,
      status: effectiveStatus(
        record.status,
        record.examFee.status,
        record.startAt,
      ),
      examFee: record.examFee,
      doctorUnavailable: false,
    },
    profile,
  );
}

/** Quá hạn chưa thanh toán thì tự hủy; quá hạn đã trả cần nhân viên xử lý. */
function effectiveStatus(
  status: AppointmentStatus,
  feeStatus: ExamFeeStatus,
  startAt: string,
): AppointmentStatus {
  if (
    status === "BOOKED" &&
    Date.parse(startAt) <= bookingMockNow().getTime() &&
    feeStatus !== "PAID"
  )
    return "CANCELLED";
  return status;
}

/**
 * Lịch riêng khu bệnh nhân: giá theo dịch vụ hiện hành, trạng thái có thể đã đổi trong phiên. Trạng
 * thái phí lấy từ kho phí dùng chung (thanh toán đổi ở đó).
 */
function ownView(
  seed: PatientOwnSeed,
  profile: LinkedProfile,
): PatientAppointment[] {
  const service = BOOKABLE_SERVICES.find((item) => item.id === seed.serviceId);
  const doctor = doctorById(seed.doctorId);
  if (!service || !doctor) return [];
  return [
    withPatient(
      {
        id: seed.id,
        startAt: seed.startAt,
        visitId: seed.visitId,
        service: { id: service.id, name: service.name },
        doctor: { id: doctor.id, fullName: doctor.fullName },
        roomName: doctor.room,
        isFollowUp: false,
        status: cancelledOwnIds.has(seed.id)
          ? "CANCELLED"
          : effectiveStatus(
              seed.status,
              findMockAppointmentFee(seed.id)?.examFeeStatus ?? seed.feeStatus,
              seed.startAt,
            ),
        examFee: {
          amount: service.price,
          status:
            findMockAppointmentFee(seed.id)?.examFeeStatus ?? seed.feeStatus,
        },
        doctorUnavailable: seed.doctorUnavailable,
      },
      profile,
    ),
  ];
}

/**
 * Mọi lịch của hồ sơ đang gắn, chưa lọc. Bỏ gắn một hồ sơ thì lịch của hồ sơ đó biến mất.
 */
function myAppointments(
  linked: readonly LinkedProfile[],
): PatientAppointment[] {
  const byId = new Map(linked.map((profile) => [profile.id, profile]));
  const shared = readStoredAppointments().flatMap((record) => {
    const profile = byId.get(record.patient.id);
    return profile ? [sharedView(record, profile)] : [];
  });
  const own = patientOwnSeeds(bookingMockNow()).flatMap((seed) => {
    const profile = linked.find(
      (item) => item.patientCode === seed.patientCode,
    );
    return profile ? ownView(seed, profile) : [];
  });
  return [...shared, ...own];
}

/** Chi tiết một lịch của hồ sơ đang gắn; ngoài phạm vi hoặc không có → 404 MSG-29 như nhau. */
function findMine(id: string): PatientAppointment {
  return (
    myAppointments(getLinkedProfileRecords()).find((item) => item.id === id) ??
    mockFail(404, "MSG-29")
  );
}

const byStartThenId = (a: PatientAppointment, b: PatientAppointment) =>
  a.startAt.localeCompare(b.startAt) || a.id.localeCompare(b.id);

const matchesWhen = (item: PatientAppointment, when: AppointmentWhen) =>
  when === "upcoming"
    ? item.status === "BOOKED" &&
      Date.parse(item.startAt) > bookingMockNow().getTime()
    : item.status !== "BOOKED" ||
      Date.parse(item.startAt) <= bookingMockNow().getTime();

/** GET /api/v1/appointments: `when`, `patientId?`, `page`, `size`. */
export async function listMyAppointments(
  query: PatientAppointmentQuery & PageQuery,
  signal?: AbortSignal,
): Promise<PageResponse<PatientAppointment>> {
  await latency(signal);
  if (query.when !== "upcoming" && query.when !== "past")
    mockFail(400, "MSG-31", undefined, [{ field: "when", code: "MSG-31" }]);
  const linked = getLinkedProfileRecords();
  if (
    query.patientId &&
    !linked.some((profile) => profile.id === query.patientId)
  )
    mockFail(403, "MSG-29");
  const { when, patientId } = query;
  const items = myAppointments(linked)
    .filter((item) => !patientId || item.patient.id === patientId)
    .filter((item) => matchesWhen(item, when))
    .sort((a, b) =>
      when === "upcoming" ? byStartThenId(a, b) : byStartThenId(b, a),
    );
  return paginate(items, query);
}

/** GET /api/v1/appointments/{id} */
export async function getMyAppointment(
  id: string,
  signal?: AbortSignal,
): Promise<PatientAppointment> {
  await latency(signal);
  return findMine(id);
}

/**
 * POST /api/v1/appointments/{id}/cancel: chỉ lịch Đã đặt. Lịch đã check-in ở quầy (kịch bản
 * `RACE_APPOINTMENT_ID`) hoặc đã qua → 409 MSG-32. Còn dưới hạn tự hủy 120 phút → 422 MSG-32 (BE:
 * APPOINTMENT_CHANGE_TOO_LATE). Hủy xong giờ được nhả; phí đã trả không hoàn lại.
 */
export async function cancelMyAppointment(
  id: string,
): Promise<PatientAppointment> {
  await latency();
  const current = findMine(id);
  if (
    current.status !== "BOOKED" ||
    id === RACE_APPOINTMENT_ID ||
    current.examFee.status === "PAID"
  )
    mockFail(409, "MSG-32");
  if (!isPatientCancellable(current.startAt, bookingMockNow().getTime()))
    mockFail(422, "MSG-32");
  if (readStoredAppointments().some((record) => record.id === id))
    cancelStoredAppointment(id);
  else {
    cancelledOwnIds.add(id);
    markMockAppointmentCancelled(id);
  }
  return findMine(id);
}

/** GET /api/v1/appointments/{id}/reschedule-availability (mock cho test) */
export async function getMyRescheduleSlots(
  id: string,
  date: string,
  signal?: AbortSignal,
): Promise<RescheduleSlot[]> {
  await latency(signal);
  const current = findMine(id);
  const now = bookingMockNow().getTime();
  if (
    current.status !== "BOOKED" ||
    current.examFee.status !== "PAID" ||
    (current.remainingReschedules ?? 2) <= 0
  )
    mockFail(409, "MSG-32");
  if (!isPatientCancellable(current.startAt, now)) mockFail(422, "MSG-32");
  const times = ["08:30", "09:30", "14:00"];
  const slots: RescheduleSlot[] = [];
  for (const doctor of BOOKING_DOCTORS) {
    for (const time of times) {
      const startTime = vnIsoAt(date, time);
      if (Date.parse(startTime) <= now) continue;
      if (
        doctor.id === current.doctor.id &&
        Date.parse(startTime) === Date.parse(current.startAt)
      )
        continue;
      slots.push({
        doctorId: doctor.id,
        doctorDisplayName: doctor.fullName,
        startTime,
      });
    }
  }
  return slots;
}

/** PATCH /api/v1/appointments/{id}/reschedule (mock cho test) */
export async function rescheduleMyAppointment(
  id: string,
  input: { doctorId: string; startTime: string },
): Promise<PatientAppointment> {
  await latency();
  const current = findMine(id);
  const now = bookingMockNow().getTime();
  if (
    current.status !== "BOOKED" ||
    id === RACE_APPOINTMENT_ID ||
    current.examFee.status !== "PAID" ||
    (current.remainingReschedules ?? 2) <= 0
  )
    mockFail(409, "MSG-32");
  if (!isPatientCancellable(current.startAt, now)) mockFail(422, "MSG-32");
  if (Date.parse(input.startTime) <= now) mockFail(422, "MSG-30");
  if (
    input.doctorId === current.doctor.id &&
    Date.parse(input.startTime) === Date.parse(current.startAt)
  )
    mockFail(400, "MSG-31");
  const doctor = doctorById(input.doctorId);
  if (!doctor) mockFail(422, "MSG-24");

  rescheduleStoredAppointment(id, {
    doctor: { id: doctor.id, fullName: doctor.fullName },
    roomName: doctor.room,
    startAt: input.startTime,
  });
  patientRescheduleCounts.set(
    id,
    (patientRescheduleCounts.get(id) ?? 0) + 1,
  );
  return findMine(id);
}
