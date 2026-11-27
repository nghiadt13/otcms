"use client";

import { useEffect, useState } from "react";
import { findVisitSummary } from "../api/visitApi";
import type { VisitSummary } from "../types/visitProgress";

/**
 * Tóm tắt lượt (người khám, ngày, trạng thái) cho phần đầu trang chi tiết. Chỉ là thông tin thêm:
 * tải lỗi hoặc không thấy lượt thì trả null và trang vẫn hiện tiến độ bình thường. Tải lại mỗi
 * lần `refreshKey` đổi để trạng thái theo kịp tiến độ.
 */
export function useVisitSummary(
  visitId: string,
  refreshKey: string | null,
): VisitSummary | null {
  const [summary, setSummary] = useState<VisitSummary | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    findVisitSummary(visitId, controller.signal)
      .then((found) => {
        if (!controller.signal.aborted && found) setSummary(found);
      })
      .catch(() => {
        // Thông tin phụ: giữ bản đã có (nếu có), không báo lỗi.
      });
    return () => controller.abort();
  }, [visitId, refreshKey]);

  return summary;
}
