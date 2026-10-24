import type { AppointmentStatus, ExamFeeStatus } from "@/lib/domain/clinicVisit";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { vnIsoAt } from "../schema/bookingTime";
import { DOCTOR_IDS } from "./appointmentMockData";

// Lịch chỉ khu bệnh nhân cần để thử đủ trạng thái, không nằm trong kho dùng chung: lượt đã check-in
// (có lượt khám), lịch bác sĩ vắng, lịch quá khứ để thử tab "Đã qua" và "Xem thêm", và lịch đã
// check-in ở quầy nhưng danh sách còn ghi Đã đặt (hủy → 409 MSG-32). Mốc thời gian tính theo `now`.
// Lịch ở đây gắn với mã BN của hồ sơ; chỉ hiện khi hồ sơ đó đang gắn với tài khoản.

export interface PatientOwnSeed {
  id: string;
  /** Mã BN của hồ sơ (`LinkedProfile.patientCode`). */
  patientCode: string;
  serviceId: string;
  doctorId: string;
  /** ISO +07:00 */
  startAt: string;
  status: AppointmentStatus;
  feeStatus: ExamFeeStatus;
  visitId: string | null;
  doctorUnavailable: boolean;
}

/** Lịch đã check-in ở quầy nhưng danh sách vẫn ghi Đã đặt: hủy → 409 MSG-32 (kịch bản phase 04 §8). */
export const RACE_APPOINTMENT_ID = "apt-race";

/** Lịch quá khứ luân phiên Vắng và Đã hủy, để danh sách "Đã qua" đủ hai trang. */
const PAST_STATUSES: readonly AppointmentStatus[] = ["NO_SHOW", "CANCELLED"];

export function patientOwnSeeds(now: Date): PatientOwnSeed[] {
  const today = todayIsoDate(now);
  const fixed: PatientOwnSeed[] = [
    {
      id: "apt-2001",
      patientCode: "BN-000123",
      serviceId: "svc-kham-tq",
      doctorId: DOCTOR_IDS.ha,
      startAt: vnIsoAt(addDays(today, -1), "08:30"),
      status: "CHECKED_IN",
      feeStatus: "PAID",
      // Khớp lượt khám `vis-an-today` của mock thanh toán (phase 06).
      visitId: "vis-an-today",
      doctorUnavailable: false,
    },
    {
      id: "apt-2101",
      patientCode: "BN-000789",
      serviceId: "svc-kham-tq",
      doctorId: DOCTOR_IDS.minh,
      startAt: vnIsoAt(addDays(today, 3), "09:00"),
      status: "BOOKED",
      feeStatus: "PENDING_PAYMENT",
      visitId: null,
      doctorUnavailable: true,
    },
    {
      id: RACE_APPOINTMENT_ID,
      patientCode: "BN-000123",
      serviceId: "svc-kham-cs",
      doctorId: DOCTOR_IDS.tuan,
      startAt: vnIsoAt(addDays(today, 5), "10:00"),
      status: "BOOKED",
      feeStatus: "PENDING_PAYMENT",
      visitId: null,
      doctorUnavailable: false,
    },
  ];
  const past: PatientOwnSeed[] = Array.from({ length: 12 }, (_, index) => ({
    id: `apt-${2201 + index}`,
    patientCode: "BN-000123",
    serviceId: "svc-kham-tq",
    doctorId: DOCTOR_IDS.ha,
    startAt: vnIsoAt(
      addDays(today, -(3 + index * 4)),
      index % 2 === 0 ? "09:00" : "14:00",
    ),
    status: PAST_STATUSES[index % PAST_STATUSES.length],
    feeStatus: "PENDING_PAYMENT",
    visitId: null,
    doctorUnavailable: false,
  }));
  return [...fixed, ...past];
}
