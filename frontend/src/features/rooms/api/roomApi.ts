import type { Room, RoomInput, RoomStatus } from "../types/room";
import { initialRooms } from "./roomMockData";

// LỚP API GIẢ: chưa có backend nên dữ liệu nằm trong bộ nhớ trình duyệt và mất
// khi tải lại trang. Khi có endpoint, giữ nguyên chữ ký các hàm và thay phần thân
// bằng apiRequest (`@/lib/api/client`), ví dụ GET `/api/v1/rooms`.
// Component và hook không cần đổi.

let rooms: Room[] = initialRooms.map((room) => ({ ...room }));

const wait = (ms = 250) => new Promise((resolve) => setTimeout(resolve, ms));

function assertUniqueName(name: string, ignoreId?: string) {
  const duplicated = rooms.some(
    (room) =>
      room.id !== ignoreId &&
      room.name.trim().toLowerCase() === name.trim().toLowerCase(),
  );
  if (duplicated) throw new Error("Tên phòng đã tồn tại.");
}

export async function listRooms(): Promise<Room[]> {
  await wait();
  return rooms.map((room) => ({ ...room }));
}

export async function createRoom(input: RoomInput): Promise<Room> {
  await wait();
  assertUniqueName(input.name);
  const room: Room = {
    ...input,
    name: input.name.trim(),
    id: `room-${crypto.randomUUID().slice(0, 8)}`,
    status: "available",
  };
  rooms = [...rooms, room];
  return { ...room };
}

export async function updateRoom(id: string, input: RoomInput): Promise<Room> {
  await wait();
  assertUniqueName(input.name, id);
  const current = rooms.find((room) => room.id === id);
  if (!current) throw new Error("Không tìm thấy phòng.");
  const updated: Room = { ...current, ...input, name: input.name.trim() };
  rooms = rooms.map((room) => (room.id === id ? updated : room));
  return { ...updated };
}

export async function setRoomStatus(
  id: string,
  status: RoomStatus,
): Promise<Room> {
  await wait(120);
  const current = rooms.find((room) => room.id === id);
  if (!current) throw new Error("Không tìm thấy phòng.");
  const updated: Room = { ...current, status };
  rooms = rooms.map((room) => (room.id === id ? updated : room));
  return { ...updated };
}
