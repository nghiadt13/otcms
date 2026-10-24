import type {
  CheckInContext,
  CheckInContextQuery,
  CheckInOption,
  IneligibleAppointment,
  PriorityGroup,
  TodayAppointmentRow,
  TodayAppointmentStatusFilter,
  TodayAppointmentsResponse,
} from "../types/checkIn";
import { SEED_APPOINTMENTS, type SeedAppointment } from "./checkInMockAppointments";
import { PRIORITY_GROUPS, SEED_NOTICES, WALK_IN_SERVICES } from "./checkInMockData";
import { findQueueEntryByAppointment, findQueueEntryByVisit } from "./receptionMockStore";
import {
  appointmentOrFail,
  byStartTime,
  canWalkIn,
  feeOf,
  isToday,
  openVisitOf,
  patientOrFail,
  statusOf,
} from "./checkInMockState";

// Các truy vấn của lớp API giả: danh sách lịch hôm nay, bối cảnh tiếp nhận, nhóm ưu tiên.

function toRow(seed: SeedAppointment): TodayAppointmentRow {
  const patient = patientOrFail(seed.patientId);
  return {
    appointmentId: seed.id,
    startAt: seed.startAt,
    patientName: patient.fullName,
    patientCode: patient.patientCode,
    phone: patient.phone,
    serviceName: seed.serviceName,
    doctorName: seed.doctorName,
    roomName: seed.roomName,
    isFollowUp: seed.isFollowUp,
    examFeeStatus: feeOf(seed).status,
    status: statusOf(seed),
  };
}

/** Lịch hôm nay. `status` rỗng = tất cả (kể cả đã hủy). Đếm luôn theo cả ngày. */
export function listTodayAppointmentsMock(
  status: TodayAppointmentStatusFilter,
): TodayAppointmentsResponse {
  const today = SEED_APPOINTMENTS.filter((seed) => isToday(seed.startAt))
    .sort(byStartTime)
    .map(toRow);
  return {
    items: status === "" ? today : today.filter((row) => row.status === status),
    counts: {
      waiting: today.filter((row) => row.status === "BOOKED").length,
      checkedIn: today.filter((row) => row.status === "CHECKED_IN").length,
    },
  };
}

function appointmentOption(seed: SeedAppointment): CheckInOption {
  return {
    kind: "APPOINTMENT",
    appointmentId: seed.id,
    startAt: seed.startAt,
    serviceName: seed.serviceName,
    visitType: seed.visitType,
    doctorName: seed.doctorName,
    roomName: seed.roomName,
    examFee: feeOf(seed),
  };
}

function ineligibleOf(seed: SeedAppointment): IneligibleAppointment {
  const base = { appointmentId: seed.id, startAt: seed.startAt };
  const entry = findQueueEntryByAppointment(seed.id);
  if (entry) {
    return {
      ...base,
      reason: "ALREADY_CHECKED_IN",
      checkedIn: { at: entry.checkedInAt, queueNumber: entry.queueNumber, roomName: entry.roomName },
    };
  }
  if (seed.status === "CANCELLED") return { ...base, reason: "CANCELLED" };
  if (seed.status === "NO_SHOW") return { ...base, reason: "NO_SHOW" };
  return { ...base, reason: "NOT_TODAY" };
}

/** Bối cảnh của bệnh nhân (tra theo lịch hẹn thì lấy bệnh nhân của lịch đó). */
export function getCheckInContextMock(query: CheckInContextQuery): CheckInContext {
  const patientId =
    "appointmentId" in query ? appointmentOrFail(query.appointmentId).patientId : query.patientId;
  const patient = patientOrFail(patientId);

  const options: CheckInOption[] = [];
  const ineligible: IneligibleAppointment[] = [];
  const seeds = SEED_APPOINTMENTS.filter((seed) => seed.patientId === patient.id).sort(byStartTime);
  for (const seed of seeds) {
    if (statusOf(seed) === "BOOKED" && isToday(seed.startAt)) options.push(appointmentOption(seed));
    else ineligible.push(ineligibleOf(seed));
  }

  const visit = openVisitOf(patient.id);
  if (visit && !findQueueEntryByVisit(visit.visitId)) {
    options.push({
      kind: "RETURN_VISIT",
      visitId: visit.visitId,
      firstCheckInDate: visit.firstCheckInDate,
      dayOfPeriod: visit.dayOfPeriod,
      nextQueue: { ...visit.nextQueue },
      examFee: { amount: 0, status: "NOT_REQUIRED", paidOnline: false },
    });
  }
  if (canWalkIn(patient.id)) {
    options.push({
      kind: "WALK_IN",
      services: WALK_IN_SERVICES.map((service) => ({ ...service })),
    });
  }

  return {
    patient: { ...patient },
    options,
    ineligible,
    notice: SEED_NOTICES[patient.id] ?? null,
  };
}

export function listPriorityGroupsMock(): PriorityGroup[] {
  return PRIORITY_GROUPS.map((group) => ({ ...group }));
}
