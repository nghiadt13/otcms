import { mockFail } from "@/lib/api/mockServer";
import type { QueueType } from "@/lib/domain/clinicVisit";
import type {
  CheckInPatient,
  CheckInQueueEntry,
  CheckInRequest,
  CheckInResult,
} from "../types/checkIn";
import {
  CONFLICT_APPOINTMENT_ID,
  SEED_APPOINTMENTS,
  type SeedAppointment,
} from "./checkInMockAppointments";
import { PRIORITY_GROUPS, ROOM_DOCTOR, ROOM_NAMES, WALK_IN_SERVICES } from "./checkInMockData";
import {
  addCheckInQueueEntry,
  countWaitingInRoom,
  findQueueEntryByAppointment,
  findQueueEntryByVisit,
  nextMockSequence,
} from "./receptionMockStore";
import {
  canWalkIn,
  feeOf,
  isToday,
  openVisitOf,
  patientOrFail,
  statusOf,
  toVietnamIso,
} from "./checkInMockState";
import { paymentCovers } from "./checkInMockPayments";

// Tiếp nhận trong lớp API giả: kiểm điều kiện theo từng nhánh (lịch hẹn, quay lại trong 7 ngày,
// vãng lai), rồi đưa vào hàng đợi và cấp số theo phòng. Lỗi trả đúng mã MSG của BE.

interface Placement {
  visitId: string;
  queueType: QueueType;
  roomName: string;
  doctorName: string;
  appointmentId: string | null;
}

const newVisitId = () => `vis-${nextMockSequence("visit")}`;

function placementOf(seed: SeedAppointment): Placement {
  return {
    visitId: newVisitId(),
    queueType: "EXAMINATION",
    roomName: seed.roomName,
    doctorName: seed.doctorName,
    appointmentId: seed.id,
  };
}

function enqueue(
  patient: CheckInPatient,
  placement: Placement,
  priorityLabel: string | null,
): { entry: CheckInQueueEntry; peopleAhead: number } {
  const peopleAhead = countWaitingInRoom(placement.roomName);
  const entry = addCheckInQueueEntry({
    visitId: placement.visitId,
    patientId: patient.id,
    patientName: patient.fullName,
    patientCode: patient.patientCode,
    appointmentId: placement.appointmentId,
    queueType: placement.queueType,
    roomName: placement.roomName,
    doctorName: placement.doctorName,
    priorityLabel,
    checkedInAt: toVietnamIso(new Date()),
  });
  return { entry, peopleAhead };
}

function placeAppointment(
  patient: CheckInPatient,
  appointmentId: string,
  paymentId: string | null,
): Placement {
  const seed = SEED_APPOINTMENTS.find((item) => item.id === appointmentId) ?? mockFail(404, "MSG-41");
  if (seed.patientId !== patient.id) mockFail(409, "MSG-37");
  if (seed.id === CONFLICT_APPOINTMENT_ID && !findQueueEntryByAppointment(seed.id)) {
    // Giả lập: lễ tân khác vừa tiếp nhận lịch này trước.
    enqueue(patient, placementOf(seed), null);
    mockFail(409, "MSG-37");
  }
  if (statusOf(seed) !== "BOOKED" || !isToday(seed.startAt)) mockFail(409, "MSG-37");
  if (
    feeOf(seed).status === "PENDING_PAYMENT" &&
    !paymentCovers(paymentId, { patientId: patient.id, appointmentId: seed.id })
  ) {
    mockFail(409, "MSG-49");
  }
  return placementOf(seed);
}

function placeReturnVisit(patient: CheckInPatient, visitId: string): Placement {
  const visit = openVisitOf(patient.id);
  if (!visit || visit.visitId !== visitId || findQueueEntryByVisit(visitId)) mockFail(409, "MSG-37");
  return {
    visitId,
    queueType: visit.nextQueue.type,
    roomName: visit.nextQueue.roomName,
    doctorName: visit.nextQueue.doctorName,
    appointmentId: null,
  };
}

function placeWalkIn(
  patient: CheckInPatient,
  serviceId: string | null,
  paymentId: string | null,
): Placement {
  const service = WALK_IN_SERVICES.find((item) => item.id === serviceId);
  if (!service) mockFail(400, "MSG-39");
  if (!canWalkIn(patient.id)) mockFail(409, "MSG-37");
  if (!paymentCovers(paymentId, { patientId: patient.id, serviceId: service.id })) {
    mockFail(409, "MSG-49");
  }
  // BR-27: phòng ít người chờ nhất (hòa thì phòng đứng trước); bác sĩ theo phòng.
  const roomName = ROOM_NAMES.reduce((best, room) =>
    countWaitingInRoom(room) < countWaitingInRoom(best) ? room : best,
  );
  return {
    visitId: newVisitId(),
    queueType: "EXAMINATION",
    roomName,
    doctorName: ROOM_DOCTOR[roomName],
    appointmentId: null,
  };
}

/** POST check-ins: kiểm điều kiện theo nhánh, rồi đưa vào hàng đợi và cấp số. */
export function checkInPatientMock(body: CheckInRequest): CheckInResult {
  const patient = patientOrFail(body.patientId);
  const chosen = [body.appointmentId, body.visitId, body.serviceId].filter((value) => value !== null);
  if (chosen.length !== 1) mockFail(400, "MSG-39");
  const priority =
    body.priorityGroupCode === null
      ? null
      : PRIORITY_GROUPS.find((group) => group.code === body.priorityGroupCode) ?? mockFail(400, "MSG-39");

  const placement =
    body.appointmentId !== null
      ? placeAppointment(patient, body.appointmentId, body.examFeePaymentId)
      : body.visitId !== null
        ? placeReturnVisit(patient, body.visitId)
        : placeWalkIn(patient, body.serviceId, body.examFeePaymentId);

  const { entry, peopleAhead } = enqueue(patient, placement, priority?.label ?? null);
  return {
    visitId: entry.visitId,
    queueEntryId: entry.id,
    queueNumber: entry.queueNumber,
    queueType: entry.queueType,
    roomName: entry.roomName,
    doctorName: entry.doctorName,
    priorityLabel: entry.priorityLabel,
    peopleAhead,
  };
}
