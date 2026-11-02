"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { getPayment } from "../api/patientBillingApi";
import type { PatientPayment } from "../types/patientBilling";
import { toError } from "./useBillingLoad";

/** Kiểm lại mỗi 10 giây khi BE còn PENDING (kịch bản phase 06 §4). */
export const PAYMENT_POLL_INTERVAL_MS = 10_000;
/** Quá 15 phút còn PENDING thì coi như thất bại (plan phase 06 §2). */
export const PAYMENT_POLL_WINDOW_MS = 15 * 60_000;

/** CHECKING: chưa có kết quả đầu tiên từ BE. */
export type PaymentDisplayStatus =
  "CHECKING" | "SUCCESS" | "PENDING" | "FAILED";

export interface PaymentStatusPolling {
  payment: PatientPayment | null;
  status: PaymentDisplayStatus;
  /** Đang chờ một lần đọc từ BE. */
  checking: boolean;
  /** Lỗi đọc kết quả; `null` khi không lỗi. Nơi gọi đọc mã HTTP để chọn thông báo. */
  error: Error | null;
  /** Kiểm tra ngay (nút "Kiểm tra lại" hoặc "Thử lại"). */
  checkNow: () => void;
}

/**
 * Đọc kết quả thanh toán từ BE, chỉ tin BE (plan phase 06 §2): PENDING thì tự kiểm lại mỗi 10 giây tối đa
 * 15 phút rồi hiển thị như thất bại. Rời trang thì dừng; lỗi thì dừng và để người dùng bấm lại.
 */
export function usePaymentStatusPolling(
  paymentId: string,
): PaymentStatusPolling {
  const [payment, setPayment] = useState<PatientPayment | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [checking, setChecking] = useState(true);
  const [timedOut, setTimedOut] = useState(false);
  const [checkToken, setCheckToken] = useState(0);
  // Hạn 15 phút tính từ lần đầu vào trang. Bấm "Kiểm tra lại" không gia hạn.
  const deadlineRef = useRef<number | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    deadlineRef.current ??= Date.now() + PAYMENT_POLL_WINDOW_MS;
    const deadline = deadlineRef.current;

    const check = () => {
      setChecking(true);
      getPayment(paymentId, controller.signal)
        .then((next) => {
          if (controller.signal.aborted) return;
          setPayment(next);
          setError(null);
          setChecking(false);
          if (next.status !== "PENDING") return;
          if (Date.now() >= deadline) {
            setTimedOut(true);
            return;
          }
          timer = setTimeout(check, PAYMENT_POLL_INTERVAL_MS);
        })
        .catch((err: unknown) => {
          if (controller.signal.aborted) return;
          setError(toError(err));
          setChecking(false);
        });
    };

    check();
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [paymentId, checkToken]);

  const checkNow = useCallback(() => setCheckToken((count) => count + 1), []);

  const status: PaymentDisplayStatus = !payment
    ? "CHECKING"
    : timedOut && payment.status === "PENDING"
      ? "FAILED"
      : payment.status;

  return { payment, status, checking, error, checkNow };
}
