import { apiRequest } from "@/lib/api/client";

/** Phòng trong phạm vi công việc của người đăng nhập (UC-64), do BE lọc theo vai trò. */
export type StaffRoom = {
  id: string;
  code: string;
  name: string;
  type: string;
  description: string;
  capabilities: string[];
  supportedServiceCount: number;
  active: boolean;
};

type RoomDto = {
  id: string;
  roomCode: string;
  displayLabel: string;
  type: string;
  description: string | null;
  capabilities: string[] | null;
  supportedServiceIds: string[] | null;
  active: boolean;
};

function toRoom(room: RoomDto): StaffRoom {
  return {
    id: room.id,
    code: room.roomCode,
    name: room.displayLabel || room.roomCode,
    type: room.type,
    description: room.description ?? "",
    capabilities: room.capabilities ?? [],
    supportedServiceCount: room.supportedServiceIds?.length ?? 0,
    active: room.active,
  };
}

export async function listStaffRooms(signal?: AbortSignal): Promise<StaffRoom[]> {
  const { items } = await apiRequest<{ items: RoomDto[] }>("/api/v1/staff/rooms", { signal });
  return items.map(toRoom);
}

/** BE trả MSG-50 khi phòng không tồn tại hoặc nằm ngoài phạm vi của người dùng. */
export async function getStaffRoom(roomId: string, signal?: AbortSignal): Promise<StaffRoom> {
  return toRoom(
    await apiRequest<RoomDto>(`/api/v1/staff/rooms/${encodeURIComponent(roomId)}`, { signal }),
  );
}
