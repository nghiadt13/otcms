"use client";

import { useCallback, useEffect, useState } from "react";
import { listBookableServices as listMockServices } from "../api/bookingApi";
import { listBookableServices as listLiveServices } from "../api/bookingLiveApi";
import type { BookableService } from "../types/booking";

export interface BookableServicesState {
  services: BookableService[];
  /** Chỉ `true` ở lần tải đầu; tải lại không đưa khối về khung chờ. */
  loading: boolean;
  /** `true` khi lần tải gần nhất lỗi. Câu chữ do khối hiển thị quyết định. */
  failed: boolean;
  reload: () => void;
}

/** Dịch vụ Khám đặt được (tải một lần khi mở luồng). `live`: lấy từ backend thật (khu bệnh nhân). */
export function useBookableServices(live = false): BookableServicesState {
  const [services, setServices] = useState<BookableService[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    (live ? listLiveServices : listMockServices)(controller.signal)
      .then(({ items }) => {
        if (controller.signal.aborted) return;
        setServices(items);
        setFailed(false);
        setLoading(false);
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setFailed(true);
        setLoading(false);
      });
    return () => controller.abort();
  }, [reloadToken, live]);

  const reload = useCallback(() => {
    setLoading(true);
    setReloadToken((n) => n + 1);
  }, []);

  return { services, loading, failed, reload };
}
