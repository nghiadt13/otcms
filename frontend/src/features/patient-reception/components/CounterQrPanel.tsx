"use client";

import { useEffect, useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { Button } from "@/components/ui/button";
import {
  getPayOsAttempt,
  reconcilePayOsAttempt,
  type PayOsPaymentAttempt,
} from "@/lib/api/billingApi";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { messageFromError } from "@/lib/messages/appMessages";

const POLL_INTERVAL_MS = 5_000;

/**
 * Mã QR PayOS đưa cho khách tại quầy (kênh COUNTER_QR). BE quyết định trạng thái: màn chỉ đọc lại
 * và báo. Khi CONFIRMED gọi `onConfirmed` với mã lần thanh toán (để mở biên lai).
 */
export function CounterQrPanel({
  attempt: initial,
  onConfirmed,
}: {
  attempt: PayOsPaymentAttempt;
  onConfirmed: (paymentId: string) => void;
}) {
  const [attempt, setAttempt] = useState(initial);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const confirmed = useRef(false);

  useEffect(() => {
    if (attempt.status !== "PENDING" || confirmed.current) return;
    const timer = window.setTimeout(() => {
      void getPayOsAttempt(attempt.id)
        .then((latest) => {
          setAttempt(latest);
          setError("");
        })
        .catch((cause: unknown) => setError(messageFromError(cause)));
    }, POLL_INTERVAL_MS);
    return () => window.clearTimeout(timer);
  }, [attempt]);

  useEffect(() => {
    if (attempt.status !== "CONFIRMED" || confirmed.current) return;
    confirmed.current = true;
    onConfirmed(attempt.id);
  }, [attempt, onConfirmed]);

  async function checkNow() {
    setBusy(true);
    setError("");
    try {
      setAttempt(await reconcilePayOsAttempt(attempt.id));
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setBusy(false);
    }
  }

  if (attempt.status === "CONFIRMED")
    return (
      <p role="status" className="text-sm">
        Đã nhận thanh toán {formatCurrencyVnd(attempt.amount)}.
      </p>
    );

  if (attempt.status !== "PENDING")
    return (
      <p role="alert" className="text-sm text-destructive">
        {attempt.status === "RECONCILIATION_REQUIRED"
          ? "Giao dịch cần đối soát với PayOS trước khi thu lại. Không thu thêm khoản này."
          : "Mã QR đã hết hiệu lực hoặc không thu được. Hãy tạo mã mới để thử lại."}
      </p>
    );

  return (
    <div className="space-y-3">
      <p className="text-sm">
        Đưa mã QR cho khách quét bằng ứng dụng ngân hàng. Số tiền{" "}
        <strong>{formatCurrencyVnd(attempt.amount)}</strong>. Khoản chỉ chuyển
        sang đã thanh toán khi PayOS xác nhận.
      </p>
      {attempt.qrCode ? (
        <div className="inline-block rounded-lg bg-white p-3">
          <QRCodeSVG value={attempt.qrCode} size={192} />
        </div>
      ) : null}
      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
      <Button variant="outline" disabled={busy} onClick={() => void checkNow()}>
        {busy ? "Đang kiểm tra..." : "Kiểm tra trạng thái"}
      </Button>
    </div>
  );
}
