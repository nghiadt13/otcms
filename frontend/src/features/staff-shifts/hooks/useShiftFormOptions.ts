"use client";

import { useCallback, useEffect, useState } from "react";
import { messageFromError } from "@/lib/messages/appMessages";
import { listRoomOptions, listStaffOptions } from "../api/shiftApi";
import type { RoomOption, StaffOption } from "../types/shift";

/**
 * Nhân viên đủ điều kiện nhận ca và phòng Sẵn sàng, tải một lần cho bộ lọc và dialog.
 * Không phân trang; tìm kiếm làm trong bộ nhớ.
 */
export function useShiftFormOptions() {
  const [staff, setStaff] = useState<StaffOption[]>([]);
  const [rooms, setRooms] = useState<RoomOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- bắt đầu tải là hệ quả của lần thử lại
    setLoading(true);
    setError(null);
    Promise.all([
      listStaffOptions(undefined, controller.signal),
      listRoomOptions(undefined, controller.signal),
    ])
      .then(([staffResult, roomResult]) => {
        if (controller.signal.aborted) return;
        setStaff(staffResult.items);
        setRooms(roomResult.items);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setError(messageFromError(err));
        setLoading(false);
      });
    return () => controller.abort();
  }, [reloadToken]);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

  return { staff, rooms, loading, error, reload };
}
