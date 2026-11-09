"use client";

import { useEffect, useState } from "react";

export interface HoldDeadline {
  /** Có hạn giữ lịch hợp lệ (ISO parse được). */
  hasDeadline: boolean;
  /** Mili giây còn lại tới hạn; null khi không có hạn. */
  remainingMs: number | null;
  /** Đồng hồ máy đã tới hạn. Chỉ để hiển thị: BE mới quyết định lịch còn hay mất. */
  ended: boolean;
}

/** Đếm ngược tới `paymentDeadline` của lịch giữ chỗ chờ thanh toán, cập nhật mỗi giây. */
export function useHoldDeadline(paymentDeadline?: string): HoldDeadline {
  const deadlineMs = paymentDeadline ? Date.parse(paymentDeadline) : Number.NaN;
  const hasDeadline = Number.isFinite(deadlineMs);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!hasDeadline) return;
    const interval = window.setInterval(() => {
      setNow(Date.now());
      if (Date.now() >= deadlineMs) window.clearInterval(interval);
    }, 1_000);
    return () => window.clearInterval(interval);
  }, [hasDeadline, deadlineMs]);

  if (!hasDeadline)
    return { hasDeadline: false, remainingMs: null, ended: false };
  const remainingMs = Math.max(0, deadlineMs - now);
  return { hasDeadline: true, remainingMs, ended: remainingMs === 0 };
}
