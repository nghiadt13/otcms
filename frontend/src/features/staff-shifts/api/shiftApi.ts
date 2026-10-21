import type { ServiceCategory } from "@/lib/domain/clinicCategories";
import type { StaffRole } from "@/lib/domain/clinicRoles";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { clone, mockDelay, mockFail } from "@/lib/api/mockServer";
import { timeToMinutes } from "../schema/shiftFieldRules";
import type {
  AffectedAppointment,
  AffectedAppointmentsResponse,
  RoomOptionsResponse,
  Shift,
  ShiftInput,
  ShiftListQuery,
  ShiftListResponse,
  ShiftUpdateInput,
  StaffOptionsResponse,
} from "../types/shift";
import {
  actionsFor,
  buildShift,
  MOCK_ROOMS,
  MOCK_STAFF,
  SERVICE_ERROR_FROM,
  seedAppointments,
  seedShifts,
  shiftMockConfig,
} from "./shiftMockData";

// Lớp API giả cho /api/v1/admin/shifts. Chữ ký và lỗi (ApiError + code) giống BE thật,
// nên khi nối backend chỉ thay thân hàm bằng apiRequest.

let shifts: Shift[] = seedShifts();
let appointments: Record<string, AffectedAppointment[]> = seedAppointments();
let nextId = 1;

/** Đặt lại kho giả về dữ liệu khởi tạo (dùng trong test). */
export function resetShiftMockStore() {
  shifts = seedShifts();
  appointments = seedAppointments();
  nextId = 1;
}

const pause = (signal?: AbortSignal) =>
  mockDelay(shiftMockConfig.delayMs, signal);

/** Gắn `allowedActions` theo thời điểm hiện tại khi trả ra. */
const present = (shift: Shift): Shift =>
  clone({ ...shift, allowedActions: actionsFor(shift) });

const overlaps = (a: Shift | ShiftInput, b: Shift | ShiftInput) =>
  a.date === b.date &&
  timeToMinutes(a.startTime) < timeToMinutes(b.endTime) &&
  timeToMinutes(b.startTime) < timeToMinutes(a.endTime);

const findShift = (id: string) => shifts.find((s) => s.id === id);

/** Kiểm tra dùng chung cho POST và PUT, ném đúng mã như BE. */
function validateAssignment(
  candidate: ShiftInput,
  staff: { role: StaffRole; eligible: boolean },
  ignoreId?: string,
) {
  const room = MOCK_ROOMS.find((r) => r.roomId === candidate.roomId);
  const start = timeToMinutes(candidate.startTime);
  const end = timeToMinutes(candidate.endTime);
  if (Number.isNaN(start) || Number.isNaN(end) || end <= start)
    mockFail(400, "MSG-34", undefined, [{ field: "endTime", code: "MSG-34" }]);
  if (candidate.date < todayIsoDate())
    mockFail(400, "MSG-34", undefined, [{ field: "date", code: "MSG-34" }]);
  if (!room || !room.ready)
    mockFail(400, "MSG-34", undefined, [{ field: "roomId", code: "MSG-34" }]);
  // Lễ tân chưa có loại phòng phù hợp (câu hỏi mở 1): chưa tạo được ca.
  if (staff.role === "RECEPTIONIST")
    mockFail(400, "MSG-34", undefined, [{ field: "roomId", code: "MSG-34" }]);
  if (!staff.eligible) mockFail(409, "MSG-35");

  for (const other of shifts) {
    if (other.id === ignoreId || !overlaps(other, candidate)) continue;
    if (other.staffId === candidate.staffId) mockFail(409, "MSG-35");
    if (
      staff.role === "ORTHOPEDIC_DOCTOR" &&
      other.role === "ORTHOPEDIC_DOCTOR" &&
      other.roomId === candidate.roomId
    )
      mockFail(409, "MSG-35");
  }
}

export async function listShifts(
  query: ShiftListQuery,
  signal?: AbortSignal,
): Promise<ShiftListResponse> {
  await pause(signal);
  if (query.from === SERVICE_ERROR_FROM) mockFail(503, "MSG-04");
  if (query.from > query.to) mockFail(400, "MSG-54");
  if (query.staffId) {
    const staff = MOCK_STAFF.find((s) => s.staffId === query.staffId);
    if (!staff || !staff.eligible) mockFail(404, "MSG-33");
  }
  if (query.roomId && !MOCK_ROOMS.some((r) => r.roomId === query.roomId))
    mockFail(404, "MSG-33");

  const items = shifts
    .filter(
      (s) =>
        s.date >= query.from &&
        s.date <= query.to &&
        (!query.staffId || s.staffId === query.staffId) &&
        (!query.role || s.role === query.role) &&
        (!query.roomId || s.roomId === query.roomId),
    )
    .sort((a, b) =>
      `${a.date}${a.startTime}`.localeCompare(`${b.date}${b.startTime}`),
    )
    .map(present);
  return { items };
}

export async function createShift(input: ShiftInput): Promise<Shift> {
  await pause();
  const staff = MOCK_STAFF.find((s) => s.staffId === input.staffId);
  if (!staff) mockFail(409, "MSG-35");
  validateAssignment(input, staff);
  const created = buildShift({ ...input, id: `sh-new-${nextId++}` });
  shifts.push(created);
  return present(created);
}

export async function updateShift(
  id: string,
  input: ShiftUpdateInput,
): Promise<Shift> {
  await pause();
  const current = findShift(id);
  if (!current) mockFail(404, "MSG-33");
  // Ca đã qua hoặc đã bắt đầu: không còn sửa được.
  if (!actionsFor(current).includes("edit")) mockFail(409, "MSG-33");
  if (
    current.role === "ORTHOPEDIC_DOCTOR" &&
    (appointments[id]?.length ?? 0) > 0
  )
    mockFail(409, "MSG-36");
  const staff = MOCK_STAFF.find((s) => s.staffId === current.staffId)!;
  validateAssignment({ ...input, staffId: current.staffId }, staff, id);
  const updated = buildShift({ ...input, id, staffId: current.staffId });
  shifts = shifts.map((s) => (s.id === id ? updated : s));
  return present(updated);
}

export async function deleteShift(id: string): Promise<void> {
  await pause();
  const current = findShift(id);
  if (!current) mockFail(404, "MSG-33");
  if (!actionsFor(current).includes("delete")) mockFail(409, "MSG-33");
  if (
    current.role === "ORTHOPEDIC_DOCTOR" &&
    (appointments[id]?.length ?? 0) > 0
  )
    mockFail(409, "MSG-36");
  shifts = shifts.filter((s) => s.id !== id);
}

export async function listAffectedAppointments(
  shiftId: string,
  signal?: AbortSignal,
): Promise<AffectedAppointmentsResponse> {
  await pause(signal);
  const shift = findShift(shiftId);
  if (!shift) mockFail(404, "MSG-33");
  const all =
    shift.role === "ORTHOPEDIC_DOCTOR" ? (appointments[shiftId] ?? []) : [];
  return {
    shiftId,
    totalItems: all.length,
    items: clone(
      [...all].sort((a, b) => a.startAt.localeCompare(b.startAt)).slice(0, 5),
    ),
  };
}

export async function listStaffOptions(
  role?: StaffRole,
  signal?: AbortSignal,
): Promise<StaffOptionsResponse> {
  await pause(signal);
  return {
    items: MOCK_STAFF.filter(
      (s) => s.eligible && (!role || s.role === role),
    ).map(({ staffId, fullName, role: r }) => ({ staffId, fullName, role: r })),
  };
}

export async function listRoomOptions(
  category?: ServiceCategory,
  signal?: AbortSignal,
): Promise<RoomOptionsResponse> {
  await pause(signal);
  return {
    items: MOCK_ROOMS.filter(
      (r) => r.ready && (!category || r.category === category),
    ).map(({ roomId, name, category: c }) => ({ roomId, name, category: c })),
  };
}

/** Ngày mai theo giờ VN, tiện cho test và demo. */
export const mockTomorrow = () => addDays(todayIsoDate(), 1);
