"use client";

import { useCallback, useEffect, useState } from "react";
import {
  createRoom,
  listRooms,
  setRoomStatus,
  updateRoom,
} from "../api/roomApi";
import type { Room, RoomInput, RoomStatus } from "../types/room";

// Trạng thái danh sách phòng + các thao tác. Các hàm thao tác ném lỗi lên để
// form/hàng hiển thị thông báo; hook chỉ giữ dữ liệu.
export function useRooms() {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRooms(await listRooms());
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Không tải được danh sách phòng.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- tải dữ liệu lần đầu
    void reload();
  }, [reload]);

  const replaceRoom = (saved: Room) =>
    setRooms((current) =>
      current.map((room) => (room.id === saved.id ? saved : room)),
    );

  return {
    rooms,
    loading,
    error,
    reload,
    async addRoom(input: RoomInput) {
      const saved = await createRoom(input);
      setRooms((current) => [...current, saved]);
    },
    async editRoom(id: string, input: RoomInput) {
      replaceRoom(await updateRoom(id, input));
    },
    async changeStatus(id: string, status: RoomStatus) {
      replaceRoom(await setRoomStatus(id, status));
    },
  };
}
