import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import { apiRequest } from "@/lib/api/client";
import { toQueryString } from "@/lib/api/pagination";
import { ApiError } from "@/lib/api/apiError";
import {
  SERVICE_CATEGORIES,
  type ServiceCategory,
} from "@/lib/domain/clinicCategories";
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

// Dùng API thật khi chạy ứng dụng; kho mock chỉ giữ ranh giới API trong test.
// Backend tạm ngưng phòng thay vì xóa để giữ lịch sử phân công.

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
const USE_MOCK = process.env.NODE_ENV === "test";

async function liveRequest<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (!(error instanceof ApiError) || !error.errorCode) throw error;
    switch (error.errorCode) {
      case "ROOM_HAS_ACTIVE_RESERVATIONS":
        throw new ApiError(error.status, { code: "MSG-64" });
      case "DUPLICATE_ROOM_CODE":
        throw new ApiError(error.status, {
          fieldErrors: [{ field: "name", code: "MSG-98" }],
        });
      case "ROOM_NOT_FOUND":
        throw new ApiError(error.status, { code: "MSG-50" });
      case "VALIDATION_FAILED":
        throw new ApiError(error.status, { code: "MSG-62" });
      case "ACCESS_DENIED":
        throw new ApiError(error.status, { code: "MSG-50" });
      default:
        throw error;
    }
  }
}

type BackendRoom = {
  id: string;
  roomCode: string;
  displayLabel: string;
  type: ServiceCategory;
  description: string;
  capabilities: string[];
  supportedServiceIds: string[];
  active: boolean;
};
type BackendServiceOption = {
  id: string;
  name: string;
  type: ServiceCategory;
  active: boolean;
};
type ItemsResponse<T> = { items: T[] };

async function backendServiceOptions(
  category?: ServiceCategory | string,
  signal?: AbortSignal,
  includeInactive = false,
) {
  const path = `/api/v1/admin/clinic-services/room-options${toQueryString({
    type: category,
    includeInactive: includeInactive || undefined,
  })}` as const;
  const result = await liveRequest(
    apiRequest<ItemsResponse<BackendServiceOption>>(path, { signal }),
  );
  return result.items.map((item) => ({
    id: item.id,
    name: item.name,
    category: item.type,
  }));
}

function mapBackendRoom(room: BackendRoom, options: RoomServiceOption[]): Room {
  const byId = new Map(options.map((option) => [option.id, option]));
  return {
    id: room.id,
    name: room.roomCode,
    type: room.type,
    description: room.description ?? "",
    status: room.active ? "AVAILABLE" : "UNAVAILABLE",
    supportedServices: room.supportedServiceIds.map((id) =>
      byId.has(id) ? { id, name: byId.get(id)!.name } : { id, name: id },
    ),
    allowedActions: ["edit", "changeStatus"],
  };
}

function findRoom(id: string): Room {
  const room = store.find((r) => r.id === id);
  if (!room) mockFail(404, "MSG-50");
  return room;
}

/** Lấy dịch vụ theo id; sai loại hoặc không tồn tại → 400 MSG-62 trên `serviceIds`. */
function resolveServices(ids: string[], type: ServiceCategory) {
  const refs = ids.map((id) => MOCK_SERVICES.find((s) => s.id === id));
  if (ids.length === 0 || refs.some((s) => !s || s.category !== type))
    mockFail(400, "MSG-62", undefined, [
      { field: "serviceIds", code: "MSG-62" },
    ]);
  return refs.map((s) => ({ id: s!.id, name: s!.name }));
}

export async function listRooms(
  query: PageQuery & Partial<RoomListFilters>,
  signal?: AbortSignal,
): Promise<PageResponse<Room>> {
  if (!USE_MOCK) {
    const [response, options] = await Promise.all([
      liveRequest(apiRequest<ItemsResponse<BackendRoom>>("/api/v1/admin/visit-rooms", { signal })),
      backendServiceOptions(undefined, signal, true),
    ]);
    const { q = "", type = "", status = "", serviceId = "" } = query;
    const keyword = norm(q);
    const filtered = response.items
      .map((room) => mapBackendRoom(room, options))
      .filter((room) => (!keyword || norm(room.name).includes(keyword))
        && (!type || room.type === type)
        && (!status || room.status === status)
        && (!serviceId || room.supportedServices.some((service) => service.id === serviceId)))
      .sort((a, b) => a.name.localeCompare(b.name, "vi")
        * (query.sort === "name,desc" ? -1 : 1));
    return paginate(filtered, query);
  }
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
  if (!USE_MOCK) return backendServiceOptions(category, signal);
  await mockDelay(120, signal);
  if (category && !isCategory(category)) mockFail(400, "MSG-54");
  return clone(
    MOCK_SERVICES.filter((s) => !category || s.category === category),
  );
}

export async function getRoom(id: string, signal?: AbortSignal): Promise<Room> {
  if (!USE_MOCK) {
    const room = await liveRequest(apiRequest<BackendRoom>(`/api/v1/admin/visit-rooms/${id}`, { signal }));
    const options = await backendServiceOptions(room.type, signal, true);
    return mapBackendRoom(room, options);
  }
  await mockDelay(120, signal);
  return clone(findRoom(id));
}

export async function createRoom(input: RoomCreateInput): Promise<Room> {
  if (!USE_MOCK) {
    const room = await liveRequest(apiRequest<BackendRoom>("/api/v1/admin/visit-rooms", {
      method: "POST",
      body: JSON.stringify({
        roomCode: input.name,
        displayLabel: input.name,
        type: input.type,
        description: input.description,
        serviceIds: input.serviceIds,
      }),
    }));
    const options = await backendServiceOptions(input.type);
    return mapBackendRoom(room, options);
  }
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
export async function updateRoom(
  id: string,
  input: RoomUpdateInput,
): Promise<Room> {
  if (!USE_MOCK) {
    const room = await liveRequest(apiRequest<BackendRoom>(`/api/v1/admin/visit-rooms/${id}`, {
      method: "PUT",
      body: JSON.stringify({
        displayLabel: input.name,
        description: input.description,
        serviceIds: input.serviceIds,
      }),
    }));
    const options = await backendServiceOptions(room.type);
    return mapBackendRoom(room, options);
  }
  await mockDelay();
  const room = findRoom(id);
  room.supportedServices = resolveServices(input.serviceIds, room.type);
  room.description = input.description.trim();
  return clone(room);
}

export async function changeRoomStatus(
  id: string,
  status: RoomStatus,
): Promise<Room> {
  if (!USE_MOCK) {
    await liveRequest(apiRequest<void>(
      `/api/v1/admin/visit-rooms/${id}${status === "AVAILABLE" ? "/activation" : ""}`,
      { method: status === "AVAILABLE" ? "POST" : "DELETE" },
    ));
    return getRoom(id);
  }
  await mockDelay();
  const room = findRoom(id);
  if (status === "UNAVAILABLE" && room.name === MOCK_IN_USE_ROOM_NAME)
    mockFail(409, "MSG-64");
  room.status = status;
  return clone(room);
}
