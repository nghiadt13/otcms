import { apiRequest } from "@/lib/api/client";
import type { ServiceCategory } from "@/lib/domain/clinicCategories";
import type { StaffRole } from "@/lib/domain/clinicRoles";
import { ApiError } from "@/lib/api/apiError";
import type {
  AffectedAppointmentsResponse,
  RoomOptionsResponse,
  Shift,
  ShiftInput,
  ShiftListQuery,
  ShiftListResponse,
  ShiftUpdateInput,
  StaffOptionsResponse,
} from "../types/shift";
import { actionsFor } from "./shiftMockData";

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
    year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(new Date(value));
  return Object.fromEntries(parts.map((part) => [part.type, part.value]));
};
const profileRole = (role: string): StaffRole | undefined =>
  role === "DOCTOR" || role === "ORTHOPEDIC_DOCTOR" ? "ORTHOPEDIC_DOCTOR"
    : ["RECEPTIONIST", "LAB_TECHNICIAN", "IMAGING_TECHNICIAN"].includes(role)
      ? role as StaffRole : undefined;
const roomCategory = (type: string): ServiceCategory =>
  type === "IMAGING" ? "IMAGING" : type === "LABORATORY" ? "LABORATORY" : "EXAMINATION";
const doctorPath = (doctorId: string) => `/api/v1/admin/doctors/${doctorId}/shifts` as const;
const staffShiftsPath = "/api/v1/admin/staff-shifts" as const;
const listStaffProfiles = (signal?: AbortSignal) =>
  apiRequest<{ items: { staffId: string; fullName: string; role: string }[] }>("/api/v1/admin/staff-shifts/staff-options", { signal });
/** Tương thích các test cũ của kho mock; API thật không giữ state phía trình duyệt. */
export function resetShiftMockStore() {
  doctorByShiftId.clear();
  staffShiftIds.clear();
  doctorShiftById.clear();
}

function toShift(item: DoctorShift, profile: StaffProfile, rooms: VisitRoom[]): Shift {
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
    allowedActions: actionsFor({ date: `${parts.year}-${parts.month}-${parts.day}`, startTime: `${parts.hour}:${parts.minute}` }),
  };
  doctorShiftById.set(item.id, shift);
  return shift;
}

function interval(date: string, start: string, end: string) {
  return { startsAt: parseLocal(`${date}T${start}:00`).toISOString(), endsAt: parseLocal(`${date}T${end}:00`).toISOString() };
}

export async function listShifts(query: ShiftListQuery, signal?: AbortSignal): Promise<ShiftListResponse> {
  if (query.from > query.to) throw new ApiError(400, { detail: "Khoảng ngày không hợp lệ." });
  const [profilesPage, roomsResponse] = await Promise.all([
    listStaffProfiles(signal),
    apiRequest<VisitRooms>("/api/v1/admin/visit-rooms", { signal }),
  ]);
  const profiles = profilesPage.items.map((p) => ({ id: p.staffId, role: p.role, displayName: p.fullName, status: "ACTIVE" }));
  const selected = query.role && query.role !== "ORTHOPEDIC_DOCTOR" ? []
    : profiles.filter((p) => profileRole(p.role) === "ORTHOPEDIC_DOCTOR")
      .filter((p) => !query.staffId || query.staffId === p.id);
  const from = parseLocal(`${query.from}T00:00:00`).toISOString();
  const nextDay = new Date(parseLocal(`${query.to}T00:00:00`).getTime() + 86400000).toISOString();
  const params = new URLSearchParams({ from: query.from, to: query.to });
  if (query.staffId) params.set("staffId", query.staffId);
  if (query.role && query.role !== "ORTHOPEDIC_DOCTOR") params.set("role", query.role);
  if (query.roomId) params.set("roomId", query.roomId);
  const nonDoctorShifts = query.role === "ORTHOPEDIC_DOCTOR"
    ? { items: [] as Shift[] }
    : await apiRequest<{ items: Shift[] }>(`${staffShiftsPath}?${params}`, { signal });
  nonDoctorShifts.items.forEach((shift) => staffShiftIds.add(shift.id));
  const rooms = roomsResponse.items;
  const groups = await Promise.all(selected.map(async (profile) => {
    const params = new URLSearchParams({ from, to: nextDay });
    const response = await apiRequest<DoctorShifts>(`${doctorPath(profile.id)}?${params}`, { signal });
    return response.items.map((item) => toShift(item, profile, rooms));
  }));
  const items = [...nonDoctorShifts.items, ...groups.flat()].filter((shift) => !query.roomId || shift.roomId === query.roomId)
    .sort((a, b) => `${a.date}${a.startTime}`.localeCompare(`${b.date}${b.startTime}`));
  return { items };
}

export async function createShift(input: ShiftInput): Promise<Shift> {
  const staffOption = (await listStaffProfiles()).items.find((p) => p.staffId === input.staffId);
  const staff = staffOption && { id: staffOption.staffId, role: staffOption.role, displayName: staffOption.fullName, status: "ACTIVE" };
  if (!staff) throw new ApiError(404, { detail: "Không tìm thấy hồ sơ nhân viên đang hoạt động." });
  if (profileRole(staff.role) !== "ORTHOPEDIC_DOCTOR") {
    const result = await apiRequest<Shift>(staffShiftsPath, { method: "POST", body: JSON.stringify(input) });
    staffShiftIds.add(result.id);
    return result;
  }
  const result = await apiRequest<DoctorShift>(doctorPath(staff.id), {
    method: "POST", body: JSON.stringify({ ...interval(input.date, input.startTime, input.endTime), roomId: input.roomId }),
  });
  const rooms = (await apiRequest<VisitRooms>("/api/v1/admin/visit-rooms")).items;
  return toShift(result, staff, rooms);
}

export async function updateShift(id: string, input: ShiftUpdateInput): Promise<Shift> {
  const doctorId = doctorByShiftId.get(id);
  if (!doctorId && staffShiftIds.has(id))
    return apiRequest<Shift>(`${staffShiftsPath}/${id}`, { method: "PUT", body: JSON.stringify(input) });
  if (!doctorId) throw new ApiError(404, { detail: "Không tìm thấy ca cần sửa. Tải lại danh sách ca." });
  const result = await apiRequest<DoctorShift>(`${doctorPath(doctorId)}/${id}`, {
    method: "PUT", body: JSON.stringify({ ...interval(input.date, input.startTime, input.endTime), roomId: input.roomId }),
  });
  return listShifts({ from: input.date, to: input.date, staffId: doctorId }).then(({ items }) => items.find((shift) => shift.id === result.id)!);
}

export async function deleteShift(id: string): Promise<void> {
  const doctorId = doctorByShiftId.get(id);
  if (doctorId) {
    await apiRequest<void>(`${doctorPath(doctorId)}/${id}`, { method: "DELETE" });
    doctorByShiftId.delete(id);
    doctorShiftById.delete(id);
    return;
  }
  if (!staffShiftIds.has(id)) throw new ApiError(404, { detail: "Không tìm thấy ca cần xóa. Tải lại danh sách ca." });
  await apiRequest<void>(`${staffShiftsPath}/${id}`, { method: "DELETE" });
  staffShiftIds.delete(id);
}

export async function listAffectedAppointments(shiftId: string, signal?: AbortSignal): Promise<AffectedAppointmentsResponse> {
  if (staffShiftIds.has(shiftId)) return { shiftId, totalItems: 0, items: [] };
  const shift = doctorShiftById.get(shiftId);
  const doctorId = doctorByShiftId.get(shiftId);
  if (!shift || !doctorId) throw new ApiError(404, { detail: "Không tìm thấy ca." });
  const result = await apiRequest<{ affectedBookingCount: number }>(
    `/api/v1/admin/doctors/${doctorId}/mutation-impact`,
    { method: "POST", body: JSON.stringify({
      resourceType: "SHIFT", resourceId: shiftId,
      ...interval(shift.date, shift.startTime, shift.endTime),
    }), signal },
  );
  return { shiftId, totalItems: result.affectedBookingCount, items: [] };
}

export async function listStaffOptions(_role?: StaffRole, signal?: AbortSignal): Promise<StaffOptionsResponse> {
  const response = await listStaffProfiles(signal);
  return { items: response.items
    .map((profile) => ({ staffId: profile.staffId, fullName: profile.fullName, role: profileRole(profile.role) }))
    .filter((staff): staff is { staffId: string; fullName: string; role: StaffRole } => staff.role !== undefined)
    .filter((staff) => !_role || staff.role === _role) };
}

export async function listRoomOptions(_category?: ServiceCategory, signal?: AbortSignal): Promise<RoomOptionsResponse> {
  const response = await apiRequest<VisitRooms>("/api/v1/admin/visit-rooms", { signal });
  return { items: response.items.filter((room) => room.active)
    .map((room) => ({ roomId: room.id, name: room.displayLabel || room.roomCode, category: roomCategory(room.type) }))
    .filter((room) => !_category || room.category === _category) };
}

/** Ngày mai theo giờ VN, giữ API cũ để tương thích component đang dùng hàm này. */
export const mockTomorrow = () => {
  const now = new Date();
  const date = new Intl.DateTimeFormat("en-CA", { timeZone: clinicZone }).format(now);
  const parts = date.split("-").map(Number);
  const next = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2] + 1));
  return next.toISOString().slice(0, 10);
};
