import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import type { ServiceCategory } from "@/lib/domain/clinicCategories";
import type { StaffRole } from "@/lib/domain/clinicRoles";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import type {
  Shift,
  ShiftInput,
  ShiftUpdateInput,
  ShiftListQuery,
  ShiftListResponse,
  StaffOptionsResponse,
  RoomOptionsResponse,
  AffectedAppointmentsResponse,
} from "../types/shift";
function actionsFor(
  shift: Pick<Shift, "date" | "startTime">,
  now = new Date(),
): Shift["allowedActions"] {
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
async function listStaffProfiles(signal?: AbortSignal) {
  const [staff, doctors] = await Promise.all([
    apiRequest<{
      items: { staffId: string; fullName: string; role: string }[];
    }>("/api/v1/admin/staff-shifts/staff-options", { signal }),
    apiRequest<{
      items: {
        doctorProfileId: string;
        fullName: string;
      }[];
    }>("/api/v1/admin/doctor-profiles", { signal }),
  ]);
  // Staff profiles and doctor profiles are separate identity domains. Never
  // use a generic staffId as the ID for /admin/doctors/{doctorProfileId}.
  const options = new Map(
    staff.items
      .filter((item) => profileRole(item.role) !== "ORTHOPEDIC_DOCTOR")
      .map((item) => [item.staffId, item]),
  );
  for (const doctor of doctors.items) {
    options.set(doctor.doctorProfileId, {
      staffId: doctor.doctorProfileId,
      fullName: doctor.fullName,
      role: "DOCTOR",
    });
  }
  return { items: [...options.values()] };
}

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

async function callShift<T>(action: () => Promise<T>): Promise<T> {
  try {
    return await action();
  } catch (error) {
    if (!(error instanceof ApiError) || error.code) throw error;
    const code =
      error.errorCode === "RESOURCE_MUTATION_BLOCKED"
        ? "MSG-36"
        : error.status === 409
          ? "MSG-35"
          : error.status === 400
            ? "MSG-34"
            : error.status === 404 || error.status === 403
              ? "MSG-33"
              : "MSG-04";
    throw new ApiError(error.status, {
      code,
      errorCode: error.errorCode,
      fieldErrors: error.fieldErrors,
    });
  }
}
export const listShifts: typeof liveListShifts = (...args) =>
  callShift(() => liveListShifts(...args));
export const createShift: typeof liveCreateShift = (...args) =>
  callShift(() => liveCreateShift(...args));
export const updateShift: typeof liveUpdateShift = (...args) =>
  callShift(() => liveUpdateShift(...args));
export const deleteShift: typeof liveDeleteShift = (...args) =>
  callShift(() => liveDeleteShift(...args));
export const listAffectedAppointments: typeof liveListAffectedAppointments = (
  ...args
) => callShift(() => liveListAffectedAppointments(...args));
export const listStaffOptions: typeof liveListStaffOptions = (...args) =>
  callShift(() => liveListStaffOptions(...args));
export const listRoomOptions: typeof liveListRoomOptions = (...args) =>
  callShift(() => liveListRoomOptions(...args));
export const mockTomorrow = () => addDays(todayIsoDate(), 1);
