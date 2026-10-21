import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import { SERVICE_CATEGORIES, type ServiceCategory } from "@/lib/domain/clinicCategories";
import type {
  Room,
  RoomCreateInput,
  RoomListFilters,
  RoomServiceOption,
  RoomStatus,
  RoomUpdateInput,
} from "../types/room";
import {
  MOCK_IN_USE_ROOM_NAME,
  MOCK_SERVICES,
  buildSeedRooms,
} from "./roomMockData";

// Giai đoạn mock: mỗi hàm tương ứng một endpoint `/api/v1/admin/rooms...`.
// Khi nối BE thay thân hàm bằng `apiRequest`, giữ nguyên chữ ký. Không có deleteRoom (spec: không xóa phòng).

let store: Room[] = buildSeedRooms();

/** Dùng trong test để đưa kho mock về dữ liệu mẫu. */
export function resetRoomMockStore() {
  store = buildSeedRooms();
}

const isCategory = (v: string): v is ServiceCategory =>
  (SERVICE_CATEGORIES as string[]).includes(v);
const isStatus = (v: string): v is RoomStatus =>
  v === "AVAILABLE" || v === "UNAVAILABLE";
const norm = (v: string) => v.trim().toLocaleLowerCase("vi");

function findRoom(id: string): Room {
  const room = store.find((r) => r.id === id);
  if (!room) mockFail(404, "MSG-50");
  return room;
}

/** Lấy dịch vụ theo id; sai loại hoặc không tồn tại → 400 MSG-62 trên `serviceIds`. */
function resolveServices(ids: string[], type: ServiceCategory) {
  const refs = ids.map((id) => MOCK_SERVICES.find((s) => s.id === id));
  if (ids.length === 0 || refs.some((s) => !s || s.category !== type))
    mockFail(400, "MSG-62", undefined, [{ field: "serviceIds", code: "MSG-62" }]);
  return refs.map((s) => ({ id: s!.id, name: s!.name }));
}

export async function listRooms(
  query: PageQuery & Partial<RoomListFilters>,
  signal?: AbortSignal,
): Promise<PageResponse<Room>> {
  await mockDelay(150, signal);
  const { q = "", type = "", status = "", serviceId = "" } = query;
  if (q === "khong-quyen") mockFail(403, "MSG-50");
  if (q === "loi-may-chu") mockFail(500, "MSG-04");
  if ((type && !isCategory(type)) || (status && !isStatus(status)))
    mockFail(400, "MSG-54");

  const keyword = norm(q);
  const desc = query.sort === "name,desc";
  const items = store
    .filter(
      (r) =>
        (!keyword || norm(r.name).includes(keyword)) &&
        (!type || r.type === type) &&
        (!status || r.status === status) &&
        (!serviceId || r.supportedServices.some((s) => s.id === serviceId)),
    )
    .sort((a, b) => a.name.localeCompare(b.name, "vi") * (desc ? -1 : 1));
  return clone(paginate(items, query));
}

/** Dịch vụ đang cung cấp để gắn phòng; bỏ `category` = mọi loại. */
export async function listRoomServiceOptions(
  category?: ServiceCategory | string,
  signal?: AbortSignal,
): Promise<RoomServiceOption[]> {
  await mockDelay(120, signal);
  if (category && !isCategory(category)) mockFail(400, "MSG-54");
  return clone(MOCK_SERVICES.filter((s) => !category || s.category === category));
}

export async function getRoom(id: string, signal?: AbortSignal): Promise<Room> {
  await mockDelay(120, signal);
  return clone(findRoom(id));
}

export async function createRoom(input: RoomCreateInput): Promise<Room> {
  await mockDelay();
  const name = input.name.trim();
  if (!name)
    mockFail(400, "MSG-62", undefined, [{ field: "name", code: "MSG-62" }]);
  if (!isCategory(input.type))
    mockFail(400, "MSG-62", undefined, [{ field: "type", code: "MSG-62" }]);
  if (store.some((r) => norm(r.name) === norm(name)))
    mockFail(409, "MSG-98", undefined, [{ field: "name", code: "MSG-98" }]);
  const room: Room = {
    id: crypto.randomUUID(),
    name,
    type: input.type,
    description: input.description.trim(),
    status: "AVAILABLE",
    supportedServices: resolveServices(input.serviceIds, input.type),
    allowedActions: ["edit", "changeStatus"],
  };
  store = [...store, room];
  return clone(room);
}

/** Chỉ đổi mô tả và dịch vụ; tên và loại giữ nguyên (UC66). */
export async function updateRoom(id: string, input: RoomUpdateInput): Promise<Room> {
  await mockDelay();
  const room = findRoom(id);
  room.supportedServices = resolveServices(input.serviceIds, room.type);
  room.description = input.description.trim();
  return clone(room);
}

export async function changeRoomStatus(id: string, status: RoomStatus): Promise<Room> {
  await mockDelay();
  const room = findRoom(id);
  if (status === "UNAVAILABLE" && room.name === MOCK_IN_USE_ROOM_NAME)
    mockFail(409, "MSG-64");
  room.status = status;
  return clone(room);
}
