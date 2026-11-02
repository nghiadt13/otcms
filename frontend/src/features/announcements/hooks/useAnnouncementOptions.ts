"use client";

import { useEffect, useState } from "react";
import { notifyError } from "@/lib/messages/notify";
import { getAnnouncementOptions } from "../api/announcementApi";
import type { AnnouncementOptions } from "../types/announcement";

/** Nhóm người nhận, kênh và quyền cấp trang. Tải một lần; lỗi thì toast và trả `options = null`. */
export function useAnnouncementOptions() {
  const [state, setState] = useState<{
    options: AnnouncementOptions | null;
    loading: boolean;
  }>({ options: null, loading: true });

  useEffect(() => {
    const controller = new AbortController();
    getAnnouncementOptions(controller.signal)
      .then((options) => {
        if (!controller.signal.aborted) setState({ options, loading: false });
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setState({ options: null, loading: false });
        notifyError(err);
      });
    return () => controller.abort();
  }, []);

  return state;
}
