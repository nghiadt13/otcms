import { apiRequest } from "@/lib/api/client";

export type ImagingRoom = {
  id: string;
  code: string;
  name: string;
  type: string;
  description: string;
  capabilities: string[];
  availability: "Hoạt động" | "Tạm ngưng";
};

type RoomDto = {
  id: string;
  roomCode: string;
  displayLabel: string;
  type: string;
  description: string;
  capabilities: string[];
  active: boolean;
};

export async function listImagingRooms(
  signal?: AbortSignal,
): Promise<ImagingRoom[]> {
  const { items } = await apiRequest<{ items: RoomDto[] }>(
    "/api/v1/staff/rooms",
    { signal },
  );
  return items
    .filter((room) => room.type === "IMAGING")
    .map((room) => ({
      id: room.id,
      code: room.roomCode,
      name: room.displayLabel || room.roomCode,
      type: room.type,
      description: room.description ?? "",
      capabilities: room.capabilities ?? [],
      availability: room.active ? "Hoạt động" : "Tạm ngưng",
    }));
}
