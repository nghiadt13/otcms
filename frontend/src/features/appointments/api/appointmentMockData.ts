import type {
  Appointment,
  AppointmentAction,
  AppointmentStatus,
  DoctorLookupItem,
} from "../types/appointment";

// Dữ liệu giả cố định quanh ngày 2026-10-20 (Thứ ba), múi +07:00. Không dùng new Date().

export const SLOT_MINUTES = 15;

const docId = (n: number) => `b3a9e8f0-2c4d-4e7a-8f11-5d6e7a8b9c0${n}`;
export const apptId = (n: number) =>
  `3f6c2a10-8b1e-4d7a-9c55-0a1b2c3d4e${String(n).padStart(2, "0")}`;

export const DOCTORS: DoctorLookupItem[] = [
  { id: docId(1), fullName: "BS. Phạm Thu Hà", bookable: true },
  { id: docId(2), fullName: "BS. Lê Quang Minh", bookable: true },
  { id: docId(3), fullName: "BS. Đỗ Minh Tuấn", bookable: true },
  { id: docId(4), fullName: "BS. Nguyễn Hải Yến", bookable: false },
  { id: docId(5), fullName: "BS. Đặng Quốc Bảo", bookable: true },
  { id: docId(6), fullName: "BS. Võ Thanh Trúc", bookable: true },
];
export const DOCTOR_IDS = {
  ha: docId(1),
  minh: docId(2),
  tuan: docId(3),
  yen: docId(4),
  bao: docId(5),
  truc: docId(6),
} as const;

/** Lịch cố định theo kịch bản (xem bảng "Ca thử" trong plan). */
export const SCENARIO_IDS = {
  af1: apptId(2),
  changedMidway: apptId(8),
  slotLostOnPick: apptId(9),
} as const;

/** Bác sĩ có ca Thứ hai đến Thứ bảy: 08:00-11:30 và 13:30-17:00. */
export const SHIFT_WINDOWS: [number, number][] = [
  [8 * 60, 11 * 60 + 30],
  [13 * 60 + 30, 17 * 60],
];

export type MockAppointment = Appointment & { patientPhone: string };

const doctorRef = (id: string) => {
  const doctor = DOCTORS.find((item) => item.id === id);
  return doctor ? { id: doctor.id, fullName: doctor.fullName } : null;
};

const at = (date: string, time: string) => `${date}T${time}:00+07:00`;

function make(
  n: number,
  startAt: string,
  patientName: string,
  serviceName: string,
  doctorIdOrNull: string | null,
  status: AppointmentStatus,
  options: {
    dependent?: boolean;
    paid?: boolean;
    actions?: AppointmentAction[];
  } = {},
): MockAppointment {
  const bookedActions: AppointmentAction[] = ["reassign"];
  return {
    id: apptId(n),
    startAt,
    patientName,
    bookedForDependent: options.dependent ?? false,
    serviceName,
    doctor: doctorIdOrNull ? doctorRef(doctorIdOrNull) : null,
    status,
    examFeePaid: options.paid ?? false,
    allowedActions:
      options.actions ?? (status === "BOOKED" ? bookedActions : []),
    patientPhone: `09${String(10000000 + n * 7919).slice(0, 8)}`,
  };
}

const FILLER_PATIENTS = [
  "Lê Thị Mai",
  "Phạm Văn Long",
  "Hoàng Thu Trang",
  "Vũ Đức Anh",
  "Bùi Ngọc Lan",
  "Đinh Quang Huy",
  "Ngô Thị Hạnh",
  "Dương Minh Châu",
];
const FILLER_SERVICES = [
  "Khám chỉnh hình",
  "Tái khám",
  "Chụp X-quang",
  "Khám cột sống",
];
const BOOKABLE_IDS = DOCTORS.filter((d) => d.bookable).map((d) => d.id);

const pad2 = (n: number) => String(n).padStart(2, "0");

/** 36 lịch Đã đặt buổi chiều (14:00-15:15, ngày 21-26/10) để danh sách có nhiều trang. */
function fillers(): MockAppointment[] {
  return Array.from({ length: 36 }, (_, i) => {
    const day = 21 + (i % 6);
    const minutes = 14 * 60 + Math.floor(i / 6) * SLOT_MINUTES;
    const time = `${pad2(Math.floor(minutes / 60))}:${pad2(minutes % 60)}`;
    return make(
      13 + i,
      at(`2026-10-${day}`, time),
      FILLER_PATIENTS[i % FILLER_PATIENTS.length],
      FILLER_SERVICES[i % FILLER_SERVICES.length],
      BOOKABLE_IDS[i % BOOKABLE_IDS.length],
      "BOOKED",
      { paid: i % 3 === 0 },
    );
  });
}

export function createInitialAppointments(): MockAppointment[] {
  const list: MockAppointment[] = [
    make(1, at("2026-10-21", "08:00"), "Nguyễn Thị Hoa", "Khám chỉnh hình", DOCTOR_IDS.ha, "BOOKED", { paid: true }),
    // AF1: không có bác sĩ thay thế, Admin báo bệnh nhân.
    make(2, at("2026-10-21", "08:15"), "Trần Minh Khoa", "Chụp X-quang", null, "BOOKED", {
      dependent: true,
      actions: ["reassign", "notifyNoReplacement"],
    }),
    make(3, at("2026-10-22", "09:00"), "Lương Gia Bảo", "Khám cột sống", null, "BOOKED"),
    make(4, at("2026-10-20", "08:30"), "Phan Thị Ngọc", "Khám chỉnh hình", DOCTOR_IDS.ha, "CHECKED_IN", { paid: true }),
    make(5, at("2026-10-20", "10:00"), "Đoàn Văn Hậu", "Tái khám", DOCTOR_IDS.tuan, "CANCELLED"),
    make(6, at("2026-10-20", "13:30"), "Trịnh Kim Oanh", "Chụp X-quang", DOCTOR_IDS.minh, "NO_SHOW"),
    make(7, at("2026-10-21", "09:15"), "Nguyễn Bảo Châu", "Khám chỉnh hình", DOCTOR_IDS.tuan, "BOOKED", { dependent: true }),
    // Trạng thái đổi giữa chừng: chuyển hoặc báo đều trả 409 MSG-32.
    make(8, at("2026-10-22", "10:30"), "Cao Thế Vinh", "Tái khám", DOCTOR_IDS.minh, "BOOKED", {
      actions: ["reassign", "notifyNoReplacement"],
    }),
    // Chọn BS. Lê Quang Minh thì báo "mất ca" (409 MSG-24).
    make(9, at("2026-10-23", "08:45"), "Mai Thị Thu", "Khám cột sống", DOCTOR_IDS.ha, "BOOKED", { paid: true }),
    make(10, at("2026-10-23", "10:00"), "Tạ Quốc Việt", "Khám chỉnh hình", DOCTOR_IDS.ha, "BOOKED"),
    make(11, at("2026-10-24", "13:30"), "Lý Hồng Nhung", "Chụp X-quang", DOCTOR_IDS.minh, "BOOKED", { paid: true }),
    make(12, at("2026-10-27", "14:00"), "Hồ Anh Tú", "Tái khám", DOCTOR_IDS.tuan, "CANCELLED"),
    ...fillers(),
  ];
  return list.sort(
    (a, b) => a.startAt.localeCompare(b.startAt) || a.id.localeCompare(b.id),
  );
}

/** Thứ trong tuần (0 = Chủ nhật) của chuỗi yyyy-MM-dd. */
export const weekdayOf = (date: string) =>
  new Date(`${date}T00:00:00Z`).getUTCDay();

/** Các khung giờ bắt đầu ("HH:mm") trong một ngày có ca. Chủ nhật không có ca. */
export function shiftSlotTimes(date: string): string[] {
  if (weekdayOf(date) === 0) return [];
  const times: string[] = [];
  for (const [start, end] of SHIFT_WINDOWS)
    for (let m = start; m + SLOT_MINUTES <= end; m += SLOT_MINUTES)
      times.push(`${pad2(Math.floor(m / 60))}:${pad2(m % 60)}`);
  return times;
}
