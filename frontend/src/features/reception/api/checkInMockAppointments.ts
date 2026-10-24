import type {
  AppointmentStatus,
  ExamFeeStatus,
  QueueEntryStatus,
  VisitType,
} from "@/lib/domain/clinicVisit";
import type { CheckInQueueEntry, CheckInServiceOption, ExamFee } from "../types/checkIn";
import {
  MOCK_TODAY,
  ROOM_DOCTOR,
  ROOM_NAMES,
  SEED_PATIENTS,
  SVC_GENERAL,
  SVC_SPINE,
  YESTERDAY,
  at,
} from "./checkInMockData";

// Lịch hẹn và hàng đợi của "hôm nay" trong dữ liệu giả (chỉ lớp API giả dùng): 23 lịch hẹn đang
// chờ hoặc đã tiếp nhận, lịch đã hủy và lịch hôm qua không đến, cùng số thứ tự đã cấp.

export interface SeedAppointment {
  id: string;
  patientId: string;
  startAt: string;
  serviceName: string;
  visitType: VisitType;
  roomName: string;
  doctorName: string;
  isFollowUp: boolean;
  /** Trạng thái gốc; CHECKED_IN còn được xác nhận bởi bản ghi hàng đợi trong kho. */
  status: AppointmentStatus;
  fee: ExamFee;
}

type ServiceKey = "tq" | "cs";
type RoomNo = 1 | 2;
/** [id, giờ, patientId, dịch vụ, phòng, tái khám, trạng thái, trạng thái phí, trả online]. */
type SeedRow = [
  string,
  string,
  string,
  ServiceKey,
  RoomNo,
  boolean,
  AppointmentStatus,
  ExamFeeStatus,
  boolean,
];

const SERVICE_BY_KEY: Record<ServiceKey, CheckInServiceOption> = {
  tq: SVC_GENERAL,
  cs: SVC_SPINE,
};

function toSeed(row: SeedRow, date: string = MOCK_TODAY): SeedAppointment {
  const [id, time, patientId, serviceKey, roomNo, followUp, status, feeStatus, paidOnline] = row;
  const service = SERVICE_BY_KEY[serviceKey];
  const roomName = ROOM_NAMES[roomNo - 1];
  return {
    id,
    patientId,
    startAt: at(date, time),
    serviceName: service.name,
    visitType: followUp ? "FOLLOW_UP" : "FIRST_VISIT",
    roomName,
    doctorName: ROOM_DOCTOR[roomName],
    isFollowUp: followUp,
    status,
    fee: { amount: service.price, status: feeStatus, paidOnline },
  };
}

const TODAY_ROWS: SeedRow[] = [
  // 14 lịch Đã đặt: có trả online, chưa trả, tái khám.
  ["apt-1001", "08:30", "pat-an", "tq", 2, true, "BOOKED", "PENDING_PAYMENT", false],
  ["apt-1002", "08:45", "pat-binh", "tq", 1, false, "BOOKED", "PAID", true],
  ["apt-1003", "08:45", "pat-p01", "cs", 2, false, "BOOKED", "PENDING_PAYMENT", false],
  ["apt-1004", "09:00", "pat-p02", "tq", 1, true, "BOOKED", "PAID", true],
  ["apt-1005", "09:00", "pat-p03", "tq", 2, false, "BOOKED", "PENDING_PAYMENT", false],
  ["apt-1006", "09:15", "pat-p04", "cs", 1, true, "BOOKED", "PENDING_PAYMENT", false],
  // Khi thu phí, khoản này đã được trả online ở nơi khác (409 MSG-49).
  ["apt-race", "09:15", "pat-p05", "tq", 2, false, "BOOKED", "PENDING_PAYMENT", false],
  ["apt-1008", "09:30", "pat-p06", "tq", 1, false, "BOOKED", "PAID", true],
  // Khi tiếp nhận, lịch này đã bị lễ tân khác tiếp nhận trước (409 MSG-37).
  ["apt-1009", "09:30", "pat-p09", "tq", 2, true, "BOOKED", "PENDING_PAYMENT", false],
  ["apt-1010", "09:45", "pat-p07", "cs", 1, false, "BOOKED", "PENDING_PAYMENT", false],
  ["apt-1011", "10:00", "pat-p08", "tq", 2, true, "BOOKED", "PAID", false],
  ["apt-1012", "10:15", "pat-p10", "tq", 1, false, "BOOKED", "PENDING_PAYMENT", false],
  ["apt-1013", "10:30", "pat-p11", "cs", 2, false, "BOOKED", "PAID", true],
  ["apt-1014", "10:45", "pat-p12", "tq", 1, true, "BOOKED", "PENDING_PAYMENT", false],
  // 9 lịch Đã tiếp nhận (số thứ tự nằm trong SEED_QUEUE).
  ["apt-1015", "07:45", "pat-p14", "tq", 1, false, "CHECKED_IN", "PAID", true],
  ["apt-1016", "08:00", "pat-p15", "cs", 2, false, "CHECKED_IN", "PAID", false],
  ["apt-1017", "08:00", "pat-p16", "tq", 1, true, "CHECKED_IN", "PAID", false],
  ["apt-1018", "08:15", "pat-p17", "tq", 2, false, "CHECKED_IN", "PAID", false],
  ["apt-1019", "08:20", "pat-p18", "tq", 1, false, "CHECKED_IN", "PAID", true],
  ["apt-1020", "08:30", "pat-p13", "tq", 2, false, "CHECKED_IN", "PAID", true],
  ["apt-1021", "08:45", "pat-p19", "cs", 1, false, "CHECKED_IN", "PAID", false],
  ["apt-1022", "09:00", "pat-p20", "tq", 2, true, "CHECKED_IN", "PAID", false],
  ["apt-1023", "09:15", "pat-p21", "tq", 2, false, "CHECKED_IN", "PAID", false],
];

export const RACE_APPOINTMENT_ID = "apt-race";
export const CONFLICT_APPOINTMENT_ID = "apt-1009";

export const SEED_APPOINTMENTS: SeedAppointment[] = [
  ...TODAY_ROWS.map((row) => toSeed(row)),
  // Pat-em: lịch hủy hôm nay và lịch hôm qua không đến (chỉ hiện ở phần không đủ điều kiện).
  toSeed(["apt-1024", "14:00", "pat-em", "tq", 2, false, "CANCELLED", "PENDING_PAYMENT", false]),
  toSeed(["apt-1000", "09:00", "pat-em", "tq", 1, false, "NO_SHOW", "PENDING_PAYMENT", false], YESTERDAY),
];

interface SeedQueueRow {
  appointmentId: string;
  checkedAt: string;
  queueNumber: number;
  status: QueueEntryStatus;
}

// Số thứ tự đã cấp hôm nay. Phòng khám 2 còn 3 người ở trạng thái Đang chờ, nên người được cấp
// số 12 thấy "Có 3 người phía trước" như phiếu mẫu trong phase S04b.
const SEED_QUEUE: SeedQueueRow[] = [
  { appointmentId: "apt-1015", checkedAt: "07:52", queueNumber: 2, status: "DONE" },
  { appointmentId: "apt-1016", checkedAt: "07:58", queueNumber: 3, status: "BEING_SERVED" },
  { appointmentId: "apt-1017", checkedAt: "08:03", queueNumber: 4, status: "WAITING" },
  { appointmentId: "apt-1018", checkedAt: "08:09", queueNumber: 5, status: "WAITING" },
  { appointmentId: "apt-1019", checkedAt: "08:18", queueNumber: 6, status: "ABSENT" },
  { appointmentId: "apt-1020", checkedAt: "08:12", queueNumber: 7, status: "WAITING" },
  { appointmentId: "apt-1021", checkedAt: "08:40", queueNumber: 8, status: "WAITING" },
  { appointmentId: "apt-1022", checkedAt: "08:55", queueNumber: 9, status: "DONE" },
  { appointmentId: "apt-1023", checkedAt: "09:12", queueNumber: 11, status: "WAITING" },
];

/** Bản ghi hàng đợi ban đầu của 9 lịch đã tiếp nhận (ghép bệnh nhân, phòng, bác sĩ từ lịch). */
export function seedQueueEntries(): CheckInQueueEntry[] {
  return SEED_QUEUE.flatMap((row, index) => {
    const appointment = SEED_APPOINTMENTS.find((item) => item.id === row.appointmentId);
    const patient = SEED_PATIENTS.find((item) => item.id === appointment?.patientId);
    if (!appointment || !patient) return [];
    return [
      {
        id: `q-${3001 + index}`,
        visitId: `vis-${6001 + index}`,
        patientId: patient.id,
        patientName: patient.fullName,
        patientCode: patient.patientCode,
        appointmentId: appointment.id,
        queueType: "EXAMINATION",
        queueNumber: row.queueNumber,
        roomName: appointment.roomName,
        doctorName: appointment.doctorName,
        priorityLabel: null,
        checkedInAt: at(MOCK_TODAY, row.checkedAt),
        status: row.status,
      },
    ];
  });
}
