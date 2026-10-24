import { useEffect, useRef, useState } from "react";
import { getExamFeePayment } from "../api/checkInApi";
import type { ExamFeePayment } from "../types/checkIn";

// Theo dõi khoản VNPay đang chờ: hỏi trạng thái mỗi 3 giây và đếm ngược theo `expiresAt`.
// Dừng khi SUCCESS, FAILED, hết hạn hoặc unmount, để không còn request rò rỉ sau khi rời màn.

const POLL_INTERVAL_MS = 3000;
const CLOCK_INTERVAL_MS = 1000;

export interface PendingVnpayPayment {
  id: string;
  expiresAt: string;
}

export interface ExamFeePaymentHandlers {
  onSuccess: (payment: ExamFeePayment) => void;
  /** Payment có khi BE báo FAILED; null khi FE thấy hết hạn trước khi BE báo. */
  onFailed: (payment: ExamFeePayment | null) => void;
}

export interface ExamFeePaymentWatch {
  /** Giây còn lại đến hạn; null khi không có khoản đang chờ. */
  secondsLeft: number | null;
  /** Lần hỏi gần nhất lỗi; vẫn hỏi tiếp cho đến khi có kết quả hoặc hết hạn. */
  pollFailed: boolean;
}

export function useExamFeePayment(
  pending: PendingVnpayPayment | null,
  handlers: ExamFeePaymentHandlers,
): ExamFeePaymentWatch {
  const handlersRef = useRef(handlers);
  useEffect(() => {
    handlersRef.current = handlers;
  });

  const [now, setNow] = useState(() => Date.now());
  const [pollFailed, setPollFailed] = useState(false);
  const id = pending?.id ?? null;
  const expiresAt = pending?.expiresAt ?? null;

  useEffect(() => {
    if (id === null || expiresAt === null) return;
    const expiryAt = Date.parse(expiresAt);
    const controller = new AbortController();
    let stopped = false;
    let pollTimer: ReturnType<typeof setTimeout> | undefined;

    const stop = () => {
      stopped = true;
      controller.abort();
      clearTimeout(pollTimer);
      clearInterval(clock);
    };

    const clock = setInterval(() => {
      const current = Date.now();
      setNow(current);
      if (current >= expiryAt) {
        stop();
        handlersRef.current.onFailed(null);
      }
    }, CLOCK_INTERVAL_MS);

    const poll = async () => {
      try {
        const payment = await getExamFeePayment(id, controller.signal);
        if (stopped) return;
        setPollFailed(false);
        if (payment.status === "SUCCESS") {
          stop();
          handlersRef.current.onSuccess(payment);
          return;
        }
        if (payment.status === "FAILED") {
          stop();
          handlersRef.current.onFailed(payment);
          return;
        }
      } catch {
        // Lỗi mạng/máy chủ không dừng việc theo dõi: báo trên màn và hỏi lại ở lượt sau.
        if (stopped) return;
        setPollFailed(true);
      }
      if (!stopped) pollTimer = setTimeout(poll, POLL_INTERVAL_MS);
    };
    pollTimer = setTimeout(poll, POLL_INTERVAL_MS);

    return stop;
  }, [id, expiresAt]);

  return {
    secondsLeft:
      id !== null && expiresAt !== null
        ? Math.max(0, Math.ceil((Date.parse(expiresAt) - now) / 1000))
        : null,
    pollFailed,
  };
}
