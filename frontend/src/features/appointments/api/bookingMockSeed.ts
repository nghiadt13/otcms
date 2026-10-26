import type { LinkedProfile } from "@/lib/patient-scope/types";
import type {
  AppointmentStatus,
  ExamFeeStatus,
} from "@/lib/domain/clinicVisit";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { vnIsoAt, weekdayIndex } from "../schema/bookingTime";
import type { StoredAppointment } from "../types/booking";
import { DOCTOR_IDS } from "./appointmentMockData";
import {
  BOOKABLE_SERVICES,
  doctorById,
  patientIdOfCode,
  resolveBookingPatient,
  type BookingDoctor,
} from "./bookingMockCatalog";

// Dữ liệu ban đầu của kho lịch dùng chung. Mọi ngày tính theo `now` để demo luôn có lịch sắp tới
// và lịch hôm nay. Chỉ dùng trong lớp API giả.

type SeedDay =
  /** Lệch so với hôm nay (âm = đã qua). */
  | { kind: "calendar"; offset: number }
  /** Ngày làm việc thứ `nth` của bác sĩ kể từ ngày mai. */
  | { kind: "workday"; nth: number };

interface SeedSpec {
  id: string;
  patientCode: string;
  serviceId: string;
  doctorId: string;
  day: SeedDay;
  hhmm: string;
  status: AppointmentStatus;
  feeStatus: ExamFeeStatus;
  isFollowUp?: boolean;
}

const SEEDS: readonly SeedSpec[] = [
  // Khu bệnh nhân: An có lịch sắp tới và lịch đã hủy; Lan (mẹ của An) có lịch đã thanh toán.
  {
    id: "apt-1001",
    patientCode: "BN-000123",
    serviceId: "svc-kham-tq",
    doctorId: DOCTOR_IDS.ha,
    day: { kind: "workday", nth: 1 },
    hhmm: "08:30",
    status: "BOOKED",
    feeStatus: "PENDING_PAYMENT",
  },
  {
    id: "apt-1002",
    patientCode: "BN-000123",
    serviceId: "svc-kham-cs",
    doctorId: DOCTOR_IDS.minh,
    day: { kind: "calendar", offset: -9 },
    hhmm: "10:00",
    status: "CANCELLED",
    feeStatus: "PENDING_PAYMENT",
  },
  {
    id: "apt-1003",
    patientCode: "BN-000456",
    serviceId: "svc-kham-tq",
    doctorId: DOCTOR_IDS.tuan,
    day: { kind: "workday", nth: 2 },
    hhmm: "14:00",
    status: "BOOKED",
    feeStatus: "PAID",
  },
  // Khu Lễ tân, hôm nay: đủ trạng thái để thử danh sách và tiếp nhận.
  {
    id: "apt-1004",
    patientCode: "BN-000124",
    serviceId: "svc-kham-tq",
    doctorId: DOCTOR_IDS.ha,
    day: { kind: "calendar", offset: 0 },
    hhmm: "08:00",
    status: "CHECKED_IN",
    feeStatus: "PAID",
    isFollowUp: true,
  },
  {
    id: "apt-1005",
    patientCode: "BN-000127",
    serviceId: "svc-kham-tq",
    doctorId: DOCTOR_IDS.ha,
    day: { kind: "calendar", offset: 0 },
    hhmm: "08:15",
    status: "NO_SHOW",
    feeStatus: "PENDING_PAYMENT",
  },
  {
    id: "apt-1006",
    patientCode: "BN-000127",
    serviceId: "svc-kham-cs",
    doctorId: DOCTOR_IDS.minh,
    day: { kind: "calendar", offset: 0 },
    hhmm: "08:30",
    status: "CANCELLED",
    feeStatus: "PENDING_PAYMENT",
  },
  {
    id: "apt-1007",
    patientCode: "BN-000125",
    serviceId: "svc-kham-cs",
    doctorId: DOCTOR_IDS.tuan,
    day: { kind: "calendar", offset: 0 },
    hhmm: "16:45",
    status: "BOOKED",
    feeStatus: "PENDING_PAYMENT",
  },
  // Lễ tân, hôm nay, AF1: BS. Lê Quang Minh vắng cả ngày (ABSENT_TODAY_DOCTOR_ID). 08:00 không có ai
  // thay: Hà kín bởi apt-1004, Tuấn kín bởi apt-1042 → chỉ còn "Báo cho bệnh nhân".
  {
    id: "apt-1041",
    patientCode: "BN-000127",
    serviceId: "svc-kham-tq",
    doctorId: DOCTOR_IDS.minh,
    day: { kind: "calendar", offset: 0 },
    hhmm: "08:00",
    status: "BOOKED",
    feeStatus: "PENDING_PAYMENT",
  },
  {
    id: "apt-1042",
    patientCode: "BN-000125",
    serviceId: "svc-kham-cs",
    doctorId: DOCTOR_IDS.tuan,
    day: { kind: "calendar", offset: 0 },
    hhmm: "08:00",
    status: "BOOKED",
    feeStatus: "PENDING_PAYMENT",
  },
];

/** Ngày làm việc thứ `nth` của bác sĩ kể từ ngày mai (bỏ ngày nghỉ theo ca). */
function nthWorkday(doctor: BookingDoctor, today: string, nth: number): string {
  let found = 0;
  for (let offset = 1; offset < 400; offset += 1) {
    const date = addDays(today, offset);
    if (doctor.weekdays.includes(weekdayIndex(date))) {
      found += 1;
      if (found === nth) return date;
    }
  }
  return addDays(today, 1);
}

function dateOf(spec: SeedSpec, doctor: BookingDoctor, today: string): string {
  return spec.day.kind === "calendar"
    ? addDays(today, spec.day.offset)
    : nthWorkday(doctor, today, spec.day.nth);
}

function buildRecord(
  spec: SeedSpec,
  now: Date,
  linked: readonly LinkedProfile[],
): StoredAppointment | null {
  const doctor = doctorById(spec.doctorId);
  const service = BOOKABLE_SERVICES.find((item) => item.id === spec.serviceId);
  const patientId = patientIdOfCode(spec.patientCode, linked);
  const patient = patientId ? resolveBookingPatient(patientId, linked) : null;
  if (!doctor || !service || !patient) return null;

  return {
    id: spec.id,
    startAt: vnIsoAt(dateOf(spec, doctor, todayIsoDate(now)), spec.hhmm),
    patient: { id: patient.id, fullName: patient.fullName },
    service: { id: service.id, name: service.name },
    doctor: { id: doctor.id, fullName: doctor.fullName },
    roomName: doctor.room,
    status: spec.status,
    examFee: { amount: service.price, status: spec.feeStatus },
    isFollowUp: spec.isFollowUp ?? false,
    patientCode: patient.patientCode,
    patientDateOfBirth: patient.dateOfBirth,
    patientPhone: patient.phone,
    bookedFor: patient.bookedFor,
  };
}

/**
 * Lịch đặt ban đầu. Dòng nào không tra được người hoặc bác sĩ thì bỏ qua (dữ liệu giả
 * không được làm vỡ khởi tạo kho).
 */
export function createSeedAppointments(
  now: Date,
  linked: readonly LinkedProfile[],
): StoredAppointment[] {
  return SEEDS.flatMap((spec) => {
    const record = buildRecord(spec, now, linked);
    return record ? [record] : [];
  });
}
