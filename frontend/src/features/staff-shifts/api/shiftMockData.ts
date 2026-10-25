import type { ServiceCategory } from "@/lib/domain/clinicCategories";
import type { StaffRole } from "@/lib/domain/clinicRoles";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import type { AffectedAppointment, Shift } from "../types/shift";

// Kho dữ liệu giả của màn Ca làm việc. Ngày của ca tính theo "hôm nay" (giờ VN)
// để lưới tuần hiện tại luôn có dữ liệu. Mã id cố định để test và demo gọi theo tên.

export const shiftMockConfig = { delayMs: 300 };

/** Mốc `from` làm API trả 503 MSG-04 (dùng để thử trạng thái lỗi tải). */
export const SERVICE_ERROR_FROM = "2030-01-07";

export interface MockStaff {
  staffId: string;
  fullName: string;
  role: StaffRole;
  /** false = tài khoản bị vô hiệu hóa hoặc chưa có hồ sơ, không nhận ca. */
  eligible: boolean;
}

export interface MockRoom {
  roomId: string;
  name: string;
  category: ServiceCategory;
  /** false = đang bảo trì, không xếp ca mới. */
  ready: boolean;
}

export const MOCK_STAFF: MockStaff[] = [
  { staffId: "st-bs1", fullName: "BS. Nguyễn Minh Anh", role: "ORTHOPEDIC_DOCTOR", eligible: true },
  { staffId: "st-bs2", fullName: "BS. Lê Quốc Hưng", role: "ORTHOPEDIC_DOCTOR", eligible: true },
  { staffId: "st-lt1", fullName: "Phạm Thu Hà", role: "RECEPTIONIST", eligible: true },
  { staffId: "st-cdha", fullName: "Lê Quang Bình", role: "IMAGING_TECHNICIAN", eligible: true },
  { staffId: "st-xn", fullName: "Võ Thanh Tâm", role: "LAB_TECHNICIAN", eligible: true },
  { staffId: "st-off", fullName: "Đặng Văn Khoa", role: "LAB_TECHNICIAN", eligible: false },
];

export const MOCK_ROOMS: MockRoom[] = [
  { roomId: "rm-k1", name: "Phòng khám 1", category: "EXAMINATION", ready: true },
  { roomId: "rm-k2", name: "Phòng khám 2", category: "EXAMINATION", ready: true },
  { roomId: "rm-cdha1", name: "Phòng CĐHA 1", category: "IMAGING", ready: true },
  { roomId: "rm-xn1", name: "Phòng xét nghiệm 1", category: "LABORATORY", ready: false },
];

const appointmentsByShift: Record<string, AffectedAppointment[]> = {
  "sh-bs1-am": [
    { appointmentId: "ap-1", startAt: "T+1T08:30:00+07:00", patientName: "Trần Thị Hoa", serviceName: "Khám chấn thương vai" },
    { appointmentId: "ap-2", startAt: "T+1T09:15:00+07:00", patientName: "Nguyễn Văn Long", serviceName: "Tái khám gãy xương cẳng tay" },
    { appointmentId: "ap-3", startAt: "T+1T10:00:00+07:00", patientName: "Phan Thị Mai", serviceName: "Khám đau thắt lưng" },
  ],
};

type SeedShift = Omit<Shift, "staffName" | "role" | "roomName" | "roomCategory" | "allowedActions">;

/** Ca khởi tạo; `offset` là số ngày so với hôm nay. */
const SEED: { offset: number; shift: Omit<SeedShift, "date"> }[] = [
  { offset: 1, shift: { id: "sh-bs1-am", staffId: "st-bs1", startTime: "08:00", endTime: "12:00", roomId: "rm-k2" } },
  { offset: 1, shift: { id: "sh-bs1-pm", staffId: "st-bs1", startTime: "13:30", endTime: "17:30", roomId: "rm-k2" } },
  { offset: 1, shift: { id: "sh-bs2-am", staffId: "st-bs2", startTime: "08:00", endTime: "12:00", roomId: "rm-k1" } },
  { offset: 2, shift: { id: "sh-bs2-pm", staffId: "st-bs2", startTime: "13:00", endTime: "17:00", roomId: "rm-k1" } },
  { offset: 2, shift: { id: "sh-cdha-am", staffId: "st-cdha", startTime: "07:30", endTime: "11:30", roomId: "rm-cdha1" } },
  { offset: 3, shift: { id: "sh-xn-am", staffId: "st-xn", startTime: "07:00", endTime: "11:00", roomId: "rm-xn1" } },
  // Ca đã qua: chỉ xem. "Tuần trước" và "hôm qua".
  { offset: -7, shift: { id: "sh-past-bs1", staffId: "st-bs1", startTime: "08:00", endTime: "12:00", roomId: "rm-k2" } },
  { offset: -1, shift: { id: "sh-past-cdha", staffId: "st-cdha", startTime: "07:30", endTime: "11:30", roomId: "rm-cdha1" } },
];

function buildShift(seed: SeedShift): Shift {
  const staff = MOCK_STAFF.find((s) => s.staffId === seed.staffId)!;
  const room = MOCK_ROOMS.find((r) => r.roomId === seed.roomId)!;
  return {
    ...seed,
    staffName: staff.fullName,
    role: staff.role,
    roomName: room.name,
    roomCategory: room.category,
    allowedActions: [],
  };
}

/** Hành động cho phép theo ngày giờ hiện tại (giờ VN): ca đã qua hoặc đã bắt đầu chỉ xem. */
export function actionsFor(shift: Pick<Shift, "date" | "startTime">, now = new Date()): Shift["allowedActions"] {
  const today = todayIsoDate(now);
  if (shift.date < today) return ["view"];
  if (shift.date === today) {
    const nowTime = new Intl.DateTimeFormat("en-GB", {
      timeZone: "Asia/Ho_Chi_Minh",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    })
      .format(now)
      .replace("24:", "00:");
    if (shift.startTime <= nowTime) return ["view"];
  }
  return ["edit", "delete"];
}

export function seedShifts(): Shift[] {
  const today = todayIsoDate();
  return SEED.map(({ offset, shift }) =>
    buildShift({ ...shift, date: addDays(today, offset) }),
  );
}

export function seedAppointments(): Record<string, AffectedAppointment[]> {
  const tomorrow = addDays(todayIsoDate(), 1);
  const result: Record<string, AffectedAppointment[]> = {};
  for (const [shiftId, items] of Object.entries(appointmentsByShift))
    result[shiftId] = items.map((item) => ({
      ...item,
      startAt: item.startAt.replace("T+1", tomorrow),
    }));
  return result;
}

export { buildShift };
