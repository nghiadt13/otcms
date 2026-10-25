"use client";

import { useCallback, useState } from "react";
import { notifySuccess } from "@/lib/messages/notify";
import { isApiError } from "@/lib/api/apiError";
import { changeRoomStatus } from "../api/roomApi";
import { notifyRoomError, roomStatusUpdatedText } from "../roomMessages";
import { ROOM_STATUS_LABELS, type Room } from "../types/room";

type Options = {
  /** Phòng đã đổi trạng thái thành công (bản ghi mới). */
  onChanged: (room: Room) => void;
  /** Phòng không còn (404): cha đóng Sheet và tải lại danh sách. */
  onGone: (roomId: string) => void;
};

/**
 * Luồng đổi trạng thái có xác nhận (UC67): Switch và nút trong Sheet cùng gọi
 * `requestChange`; dữ liệu chỉ đổi sau khi BE trả 200.
 */
export function useRoomStatusChange({ onChanged, onGone }: Options) {
  const [target, setTarget] = useState<Room | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);
  const next = target?.status === "AVAILABLE" ? "UNAVAILABLE" : "AVAILABLE";

  const requestChange = useCallback((room: Room) => setTarget(room), []);
  const cancel = useCallback(() => setTarget(null), []);

  const confirm = useCallback(async () => {
    if (!target) return;
    setPendingId(target.id);
    try {
      const updated = await changeRoomStatus(target.id, next);
      notifySuccess(roomStatusUpdatedText(ROOM_STATUS_LABELS[next]));
      onChanged(updated);
    } catch (err) {
      notifyRoomError(err);
      if (isApiError(err) && err.status === 404) onGone(target.id);
      // 409/404: đóng hộp xác nhận (trả về bình thường). Lỗi khác: ném để hộp giữ mở.
      if (!isApiError(err) || (err.status !== 409 && err.status !== 404)) throw err;
    } finally {
      setPendingId(null);
    }
  }, [target, next, onChanged, onGone]);

  return { target, next, pendingId, requestChange, cancel, confirm };
}
