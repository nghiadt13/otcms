"use client";

import { useCallback, useEffect, useState } from "react";
import type { ServiceCategory } from "@/lib/domain/clinicCategories";
import { messageFromError } from "@/lib/messages/appMessages";
import { listRoomServiceOptions } from "../api/roomApi";
import type { RoomServiceOption } from "../types/room";

/**
 * Dịch vụ đang cung cấp theo loại (bỏ `category` = mọi loại). Cờ `active` bỏ response cũ
 * khi người dùng đổi loại nhanh. `enabled=false` thì không gọi API.
 */
export function useRoomServiceOptions(
  category?: ServiceCategory,
  enabled = true,
) {
  const [state, setState] = useState<{
    key: string;
    options: RoomServiceOption[];
    error: string | null;
  } | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const key = `${category ?? "ALL"}:${reloadToken}`;

  useEffect(() => {
    if (!enabled) return;
    let active = true;
    const controller = new AbortController();
    listRoomServiceOptions(category, controller.signal)
      .then((options) => {
        if (active) setState({ key, options, error: null });
      })
      .catch((err: unknown) => {
        if (active)
          setState({ key, options: [], error: messageFromError(err) });
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [category, enabled, key]);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);
  const ready = enabled && state?.key === key;
  return {
    options: ready ? state.options : [],
    loading: enabled && !ready,
    error: ready ? state.error : null,
    reload,
  };
}
