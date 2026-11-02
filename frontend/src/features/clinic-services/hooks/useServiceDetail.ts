"use client";

import { useEffect, useRef, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { notifyError } from "@/lib/messages/notify";
import { getServiceCatalog } from "../api/serviceCatalogApi";
import type { ServiceCatalogDetail } from "../types/serviceCatalog";

/** Lỗi nào coi là "bản ghi không còn / ngoài quyền" → màn phải tải lại danh sách. */
export const isStaleServiceError = (error: unknown) =>
  isApiError(error) && error.code === "MSG-50";

/**
 * Tải chi tiết dịch vụ khi mở Sheet. Lỗi → toast, rồi `onFail(stale)` để màn đóng Sheet
 * (và tải lại danh sách nếu bản ghi không còn).
 */
export function useServiceDetail(id: string, onFail: (stale: boolean) => void) {
  const [detail, setDetail] = useState<ServiceCatalogDetail | null>(null);
  const onFailRef = useRef(onFail);
  useEffect(() => {
    onFailRef.current = onFail;
  });

  useEffect(() => {
    const controller = new AbortController();
    getServiceCatalog(id, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setDetail(result);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        notifyError(error);
        onFailRef.current(isStaleServiceError(error));
      });
    return () => controller.abort();
  }, [id]);

  return { detail, setDetail, loading: detail === null };
}
