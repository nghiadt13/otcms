import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
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

const USE_MOCK = process.env.NODE_ENV === "test";

// ==========================================
// MOCK IMPLEMENTATION (dùng cho Vitest tests)
// ==========================================

let shifts: Shift[] = seedShifts();
let appointments: Record<string, AffectedAppointment[]> = seedAppointments();
let nextId = 1;

/** Đặt lại kho giả về dữ liệu khởi tạo (dùng trong test). */
export function resetShiftMockStore() {
  shifts = seedShifts();
  appointments = seedAppointments();
  nextId = 1;
  doctorByShiftId.clear();
  staffShiftIds.clear();
  doctorShiftById.clear();
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

async function mockListShifts(
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

async function mockCreateShift(input: ShiftInput): Promise<Shift> {
  await pause();
  const staff = MOCK_STAFF.find((s) => s.staffId === input.staffId);
  if (!staff) mockFail(409, "MSG-35");
  validateAssignment(input, staff);
  const created = buildShift({ ...input, id: `sh-new-${nextId++}` });
  shifts.push(created);
  return present(created);
}

async function mockUpdateShift(
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

async function mockDeleteShift(id: string): Promise<void> {
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

async function mockListAffectedAppointments(
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

async function mockListStaffOptions(
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

async function mockListRoomOptions(
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

// ==========================================
// LIVE BE IMPLEMENTATION (dùng khi chạy ứng dụng thật)
// ==========================================

type StaffProfile = {
  id: string;
  role: string;
  displayName: string;
  status: string;
};
type VisitRoom = {
  id: string;
  roomCode: string;
  displayLabel: string;
  type: string;
  active: boolean;
};
type VisitRooms = { items: VisitRoom[] };
type DoctorShift = {
  id: string;
  doctorProfileId: string;
  startsAt: string;
  endsAt: string;
  roomId?: string | null;
};
type DoctorShifts = { items: DoctorShift[] };

const clinicZone = "Asia/Ho_Chi_Minh";
const doctorByShiftId = new Map<string, string>();
const staffShiftIds = new Set<string>();
const doctorShiftById = new Map<string, Shift>();
const parseLocal = (value: string) => new Date(`${value}+07:00`);
const localParts = (value: string) => {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: clinicZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(value));
  return Object.fromEntries(parts.map((part) => [part.type, part.value]));
};
const profileRole = (role: string): StaffRole | undefined =>
  role === "DOCTOR" || role === "ORTHOPEDIC_DOCTOR"
    ? "ORTHOPEDIC_DOCTOR"
    : ["RECEPTIONIST", "LAB_TECHNICIAN", "IMAGING_TECHNICIAN"].includes(role)
      ? (role as StaffRole)
      : undefined;
const roomCategory = (type: string): ServiceCategory =>
  type === "IMAGING"
    ? "IMAGING"
    : type === "LABORATORY"
      ? "LABORATORY"
      : "EXAMINATION";
const doctorPath = (doctorId: string) =>
  `/api/v1/admin/doctors/${doctorId}/shifts` as const;
const staffShiftsPath = "/api/v1/admin/staff-shifts" as const;
const listStaffProfiles = (signal?: AbortSignal) =>
  apiRequest<{ items: { staffId: string; fullName: string; role: string }[] }>(
    "/api/v1/admin/staff-shifts/staff-options",
    { signal },
  );

function toShift(
  item: DoctorShift,
  profile: StaffProfile,
  rooms: VisitRoom[],
): Shift {
  const parts = localParts(item.startsAt);
  const end = localParts(item.endsAt);
  const room = rooms.find((candidate) => candidate.id === item.roomId);
  doctorByShiftId.set(item.id, item.doctorProfileId);
  const shift: Shift = {
    id: item.id,
    date: `${parts.year}-${parts.month}-${parts.day}`,
    startTime: `${parts.hour}:${parts.minute}`,
    endTime: `${end.hour}:${end.minute}`,
    staffId: profile.id,
    staffName: profile.displayName,
    role: "ORTHOPEDIC_DOCTOR",
    roomId: room?.id ?? "",
    roomName: room?.displayLabel ?? "Chưa gán phòng",
    roomCategory: room ? roomCategory(room.type) : "EXAMINATION",
    allowedActions: actionsFor({
      date: `${parts.year}-${parts.month}-${parts.day}`,
      startTime: `${parts.hour}:${parts.minute}`,
    }),
  };
  doctorShiftById.set(item.id, shift);
  return shift;
}

function interval(date: string, start: string, end: string) {
  return {
    startsAt: parseLocal(`${date}T${start}:00`).toISOString(),
    endsAt: parseLocal(`${date}T${end}:00`).toISOString(),
  };
}

async function liveListShifts(
  query: ShiftListQuery,
  signal?: AbortSignal,
): Promise<ShiftListResponse> {
  if (query.from > query.to)
    throw new ApiError(400, { detail: "Khoảng ngày không hợp lệ." });
  const [profilesPage, roomsResponse] = await Promise.all([
    listStaffProfiles(signal),
    apiRequest<VisitRooms>("/api/v1/admin/visit-rooms", { signal }),
  ]);
  const profiles = profilesPage.items.map((p) => ({
    id: p.staffId,
    role: p.role,
    displayName: p.fullName,
    status: "ACTIVE",
  }));
  const selected =
    query.role && query.role !== "ORTHOPEDIC_DOCTOR"
      ? []
      : profiles
          .filter((p) => profileRole(p.role) === "ORTHOPEDIC_DOCTOR")
          .filter((p) => !query.staffId || query.staffId === p.id);
  const from = parseLocal(`${query.from}T00:00:00`).toISOString();
  const nextDay = new Date(
    parseLocal(`${query.to}T00:00:00`).getTime() + 86400000,
  ).toISOString();
  const params = new URLSearchParams({ from: query.from, to: query.to });
  if (query.staffId) params.set("staffId", query.staffId);
  if (query.role && query.role !== "ORTHOPEDIC_DOCTOR")
    params.set("role", query.role);
  if (query.roomId) params.set("roomId", query.roomId);
  const nonDoctorShifts =
    query.role === "ORTHOPEDIC_DOCTOR"
      ? { items: [] as Shift[] }
      : await apiRequest<{ items: Shift[] }>(`${staffShiftsPath}?${params}`, {
          signal,
        });
  nonDoctorShifts.items.forEach((shift) => staffShiftIds.add(shift.id));
  const rooms = roomsResponse.items;
  const groups = await Promise.all(
    selected.map(async (profile) => {
      const p = new URLSearchParams({ from, to: nextDay });
      const response = await apiRequest<DoctorShifts>(
        `${doctorPath(profile.id)}?${p}`,
        { signal },
      );
      return response.items.map((item) => toShift(item, profile, rooms));
    }),
  );
  const items = [...nonDoctorShifts.items, ...groups.flat()]
    .filter((shift) => !query.roomId || shift.roomId === query.roomId)
    .sort((a, b) =>
      `${a.date}${a.startTime}`.localeCompare(`${b.date}${b.startTime}`),
    );
  return { items };
}

async function liveCreateShift(input: ShiftInput): Promise<Shift> {
  const staffOption = (await listStaffProfiles()).items.find(
    (p) => p.staffId === input.staffId,
  );
  const staff = staffOption && {
    id: staffOption.staffId,
    role: staffOption.role,
    displayName: staffOption.fullName,
    status: "ACTIVE",
  };
  if (!staff)
    throw new ApiError(404, {
      detail: "Không tìm thấy hồ sơ nhân viên đang hoạt động.",
    });
  if (profileRole(staff.role) !== "ORTHOPEDIC_DOCTOR") {
    const result = await apiRequest<Shift>(staffShiftsPath, {
      method: "POST",
      body: JSON.stringify(input),
    });
    staffShiftIds.add(result.id);
    return result;
  }
  const result = await apiRequest<DoctorShift>(doctorPath(staff.id), {
    method: "POST",
    body: JSON.stringify({
      ...interval(input.date, input.startTime, input.endTime),
      roomId: input.roomId,
    }),
  });
  const rooms = (await apiRequest<VisitRooms>("/api/v1/admin/visit-rooms"))
    .items;
  return toShift(result, staff, rooms);
}

async function liveUpdateShift(
  id: string,
  input: ShiftUpdateInput,
): Promise<Shift> {
  const doctorId = doctorByShiftId.get(id);
  if (!doctorId && staffShiftIds.has(id))
    return apiRequest<Shift>(`${staffShiftsPath}/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    });
  if (!doctorId)
    throw new ApiError(404, {
      detail: "Không tìm thấy ca cần sửa. Tải lại danh sách ca.",
    });
  const result = await apiRequest<DoctorShift>(
    `${doctorPath(doctorId)}/${id}`,
    {
      method: "PUT",
      body: JSON.stringify({
        ...interval(input.date, input.startTime, input.endTime),
        roomId: input.roomId,
      }),
    },
  );
  return liveListShifts({
    from: input.date,
    to: input.date,
    staffId: doctorId,
  }).then(({ items }) => items.find((shift) => shift.id === result.id)!);
}

async function liveDeleteShift(id: string): Promise<void> {
  const doctorId = doctorByShiftId.get(id);
  if (doctorId) {
    await apiRequest<void>(`${doctorPath(doctorId)}/${id}`, {
      method: "DELETE",
    });
    doctorByShiftId.delete(id);
    doctorShiftById.delete(id);
    return;
  }
  if (!staffShiftIds.has(id))
    throw new ApiError(404, {
      detail: "Không tìm thấy ca cần xóa. Tải lại danh sách ca.",
    });
  await apiRequest<void>(`${staffShiftsPath}/${id}`, { method: "DELETE" });
  staffShiftIds.delete(id);
}

async function liveListAffectedAppointments(
  shiftId: string,
  signal?: AbortSignal,
): Promise<AffectedAppointmentsResponse> {
  if (staffShiftIds.has(shiftId)) return { shiftId, totalItems: 0, items: [] };
  const shift = doctorShiftById.get(shiftId);
  const doctorId = doctorByShiftId.get(shiftId);
  if (!shift || !doctorId)
    throw new ApiError(404, { detail: "Không tìm thấy ca." });
  const result = await apiRequest<{ affectedBookingCount: number }>(
    `/api/v1/admin/doctors/${doctorId}/mutation-impact`,
    {
      method: "POST",
      body: JSON.stringify({
        resourceType: "SHIFT",
        resourceId: shiftId,
        ...interval(shift.date, shift.startTime, shift.endTime),
      }),
      signal,
    },
  );
  return { shiftId, totalItems: result.affectedBookingCount, items: [] };
}

async function liveListStaffOptions(
  _role?: StaffRole,
  signal?: AbortSignal,
): Promise<StaffOptionsResponse> {
  const response = await listStaffProfiles(signal);
  return {
    items: response.items
      .map((profile) => ({
        staffId: profile.staffId,
        fullName: profile.fullName,
        role: profileRole(profile.role),
      }))
      .filter(
        (
          staff,
        ): staff is { staffId: string; fullName: string; role: StaffRole } =>
          staff.role !== undefined,
      )
      .filter((staff) => !_role || staff.role === _role),
  };
}

async function liveListRoomOptions(
  _category?: ServiceCategory,
  signal?: AbortSignal,
): Promise<RoomOptionsResponse> {
  const response = await apiRequest<VisitRooms>("/api/v1/admin/visit-rooms", {
    signal,
  });
  return {
    items: response.items
      .filter((room) => room.active)
      .map((room) => ({
        roomId: room.id,
        name: room.displayLabel || room.roomCode,
        category: roomCategory(room.type),
      }))
      .filter((room) => !_category || room.category === _category),
  };
}

// ==========================================
// EXPORTS
// ==========================================

export async function listShifts(
  query: ShiftListQuery,
  signal?: AbortSignal,
): Promise<ShiftListResponse> {
  if (USE_MOCK) return mockListShifts(query, signal);
  return liveListShifts(query, signal);
}

export async function createShift(input: ShiftInput): Promise<Shift> {
  if (USE_MOCK) return mockCreateShift(input);
  return liveCreateShift(input);
}

export async function updateShift(
  id: string,
  input: ShiftUpdateInput,
): Promise<Shift> {
  if (USE_MOCK) return mockUpdateShift(id, input);
  return liveUpdateShift(id, input);
}

export async function deleteShift(id: string): Promise<void> {
  if (USE_MOCK) return mockDeleteShift(id);
  return liveDeleteShift(id);
}

export async function listAffectedAppointments(
  shiftId: string,
  signal?: AbortSignal,
): Promise<AffectedAppointmentsResponse> {
  if (USE_MOCK) return mockListAffectedAppointments(shiftId, signal);
  return liveListAffectedAppointments(shiftId, signal);
}

export async function listStaffOptions(
  role?: StaffRole,
  signal?: AbortSignal,
): Promise<StaffOptionsResponse> {
  if (USE_MOCK) return mockListStaffOptions(role, signal);
  return liveListStaffOptions(role, signal);
}

export async function listRoomOptions(
  category?: ServiceCategory,
  signal?: AbortSignal,
): Promise<RoomOptionsResponse> {
  if (USE_MOCK) return mockListRoomOptions(category, signal);
  return liveListRoomOptions(category, signal);
}

export const mockTomorrow = () => addDays(todayIsoDate(), 1);
