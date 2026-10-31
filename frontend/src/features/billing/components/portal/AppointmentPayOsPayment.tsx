"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import {
  createAppointmentInvoice,
  createPayOsAttempt,
  getBillingInvoice,
  getPayOsAttempt,
  payOsIdempotencyKey,
  reconcilePayOsAttempt,
  type BillingInvoice,
  type PayOsPaymentAttempt,
} from "../../api/payOsPaymentApi";

const POLL_INTERVAL_MS = 5_000;

function countdownLabel(milliseconds: number): string {
  const seconds = Math.max(0, Math.floor(milliseconds / 1_000));
  const hours = Math.floor(seconds / 3_600);
  const minutes = Math.floor((seconds % 3_600) / 60);
  const remainder = seconds % 60;
  return [hours, minutes, remainder]
    .map((value) => String(value).padStart(2, "0"))
    .join(":");
}

function isTerminal(status: PayOsPaymentAttempt["status"]): boolean {
  return status !== "PENDING";
}

function isExpired(expiresAt: string | null): boolean {
  return expiresAt !== null && Date.parse(expiresAt) <= Date.now();
}

function msUntilExpiry(expiresAt: string | null): number {
  return expiresAt === null
    ? 0
    : Math.max(0, Date.parse(expiresAt) - Date.now());
}

export function AppointmentPayOsPayment({
  appointmentId,
  invoiceId,
}: {
  appointmentId?: string;
  invoiceId?: string;
}) {
  const [invoice, setInvoice] = useState<BillingInvoice | null>(null);
  const [attempt, setAttempt] = useState<PayOsPaymentAttempt | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [syncError, setSyncError] = useState(false);
  const [remainingMs, setRemainingMs] = useState(0);
  const [retryVersion, setRetryVersion] = useState(0);
  const controllerRef = useRef<AbortController | null>(null);
  const renewKeyRef = useRef(false);
  const reconciledAttemptRef = useRef<string | null>(null);
  const pendingAttemptId = attempt?.status === "PENDING" ? attempt.id : null;
  const pendingAttemptExpiresAt =
    attempt?.status === "PENDING" ? attempt.expiresAt : null;

  const startPayment = useCallback(
    async (renewKey: boolean, signal: AbortSignal) => {
      const currentInvoice = invoiceId
        ? await getBillingInvoice(invoiceId, signal)
        : appointmentId
          ? await createAppointmentInvoice(appointmentId, signal)
          : null;
      if (!currentInvoice)
        throw new Error("Không tìm thấy hóa đơn cần thanh toán.");
      if (signal.aborted) return;
      setInvoice(currentInvoice);
      if (currentInvoice.outstandingBalance <= 0) {
        setAttempt(null);
        return;
      }
      const key = payOsIdempotencyKey(currentInvoice.id, renewKey);
      const currentAttempt = await createPayOsAttempt(
        currentInvoice.id,
        key,
        signal,
      );
      if (!signal.aborted) {
        setRemainingMs(msUntilExpiry(currentAttempt.expiresAt));
        setAttempt(currentAttempt);
      }
    },
    [appointmentId, invoiceId],
  );

  const load = useCallback(
    async (renewKey = false) => {
      controllerRef.current?.abort();
      const controller = new AbortController();
      controllerRef.current = controller;
      setLoading(true);
      setError(null);
      setSyncError(false);
      try {
        await startPayment(renewKey, controller.signal);
      } catch (cause) {
        if (!controller.signal.aborted) {
          setError(
            cause instanceof Error
              ? cause.message
              : "Không thể tạo yêu cầu thanh toán. Vui lòng thử lại.",
          );
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    },
    [startPayment],
  );

  useEffect(() => {
    void load(renewKeyRef.current);
    renewKeyRef.current = false;
    return () => controllerRef.current?.abort();
  }, [load, retryVersion]);

  useEffect(() => {
    if (!pendingAttemptId || !pendingAttemptExpiresAt) return;
    const updateRemaining = () =>
      setRemainingMs(
        Math.max(0, Date.parse(pendingAttemptExpiresAt) - Date.now()),
      );
    updateRemaining();
    const interval = window.setInterval(updateRemaining, 1_000);
    return () => window.clearInterval(interval);
  }, [pendingAttemptId, pendingAttemptExpiresAt]);

  useEffect(() => {
    if (!pendingAttemptId) return;
    let stopped = false;
    let timeout: number | undefined;
    const controller = new AbortController();

    const poll = async () => {
      try {
        const shouldReconcile = isExpired(pendingAttemptExpiresAt)
          && reconciledAttemptRef.current !== pendingAttemptId;
        if (shouldReconcile) reconciledAttemptRef.current = pendingAttemptId;
        const latest = shouldReconcile
          ? await reconcilePayOsAttempt(pendingAttemptId, controller.signal)
          : await getPayOsAttempt(pendingAttemptId, controller.signal);
        if (stopped || controller.signal.aborted) return;
        setRemainingMs(msUntilExpiry(latest.expiresAt));
        setAttempt(latest);
        if (latest.status === "CONFIRMED") {
          const refreshedInvoice = await getBillingInvoice(latest.invoiceId, controller.signal);
          if (stopped || controller.signal.aborted) return;
          setInvoice(refreshedInvoice);
        }
        setSyncError(false);
        if (!isTerminal(latest.status))
          timeout = window.setTimeout(poll, POLL_INTERVAL_MS);
      } catch {
        if (stopped || controller.signal.aborted) return;
        setSyncError(true);
        timeout = window.setTimeout(poll, POLL_INTERVAL_MS * 2);
      }
    };

    timeout = window.setTimeout(poll, POLL_INTERVAL_MS);
    return () => {
      stopped = true;
      controller.abort();
      if (timeout !== undefined) window.clearTimeout(timeout);
    };
  }, [pendingAttemptId, pendingAttemptExpiresAt]);

  const checkNow = async () => {
    if (!attempt) return;
    try {
      if (isExpired(attempt.expiresAt)) reconciledAttemptRef.current = attempt.id;
      const latest = await reconcilePayOsAttempt(attempt.id);
      setRemainingMs(msUntilExpiry(latest.expiresAt));
      setAttempt(latest);
      if (latest.status === "CONFIRMED")
        setInvoice(await getBillingInvoice(latest.invoiceId));
      setSyncError(false);
    } catch {
      setSyncError(true);
    }
  };

  const retryPayment = () => {
    renewKeyRef.current = true;
    setAttempt(null);
    setRetryVersion((version) => version + 1);
  };
  const returnHref = appointmentId
    ? PATIENT_ROUTES.appointmentDetail(appointmentId)
    : PATIENT_ROUTES.payments;

  if (loading) {
    return (
      <div aria-busy="true" className="space-y-4">
        <PortalPageHeader title="Đang tạo mã thanh toán" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-72 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PortalPageHeader
        description="Hóa đơn được lấy từ backend. Trạng thái thanh toán chỉ được xác nhận sau khi backend nhận và xác minh thông báo từ PayOS."
        title="Thanh toán hóa đơn"
      />

      {error ? (
        <div className="space-y-4 rounded-lg border bg-card p-5">
          <p className="text-base text-destructive" role="alert">
            {error}
          </p>
          <button
            className={publicButtonClass("secondary")}
            onClick={() => void load()}
            type="button"
          >
            Thử lại
          </button>
        </div>
      ) : null}

      {invoice ? (
        <section className="space-y-4 rounded-lg border bg-card p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-sm text-muted-foreground">
                {invoice.appointmentId ? "Hóa đơn phí khám" : "Hóa đơn dịch vụ y tế"}
              </p>
              <h2 className="text-lg font-semibold">{invoice.invoiceNumber}</h2>
              <p className="text-sm text-muted-foreground">
                Ngày lập {new Intl.DateTimeFormat("vi-VN", {
                  dateStyle: "medium",
                  timeZone: "Asia/Ho_Chi_Minh",
                }).format(new Date(invoice.issuedAt))}
              </p>
            </div>
            <span className="rounded-full border px-3 py-1 text-sm font-medium">
              {invoice.status === "PAID" || invoice.outstandingBalance <= 0
                ? "Đã thanh toán"
                : invoice.paidAmount > 0
                  ? "Đã thanh toán một phần"
                  : "Chờ thanh toán"}
            </span>
          </div>
          <div className="overflow-x-auto rounded-md border">
            <table className="w-full min-w-[520px] text-sm">
              <thead className="bg-muted/50 text-left">
                <tr>
                  <th className="px-4 py-3 font-medium">Nội dung</th>
                  <th className="px-4 py-3 text-right font-medium">Số lượng</th>
                  <th className="px-4 py-3 text-right font-medium">Đơn giá</th>
                  <th className="px-4 py-3 text-right font-medium">Thành tiền</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {invoice.items.map((item) => (
                  <tr key={item.lineNumber}>
                    <td className="px-4 py-3">
                      <p className="font-medium">
                        {invoice.appointmentId ? "Phí khám" : "Dịch vụ y tế"}
                      </p>
                      <p className="text-muted-foreground">{item.description}</p>
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums">{item.quantity}</td>
                    <td className="px-4 py-3 text-right tabular-nums">
                      {formatCurrencyVnd(item.unitPrice)}
                    </td>
                    <td className="px-4 py-3 text-right font-medium tabular-nums">
                      {formatCurrencyVnd(item.lineTotal)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <dl className="ml-auto grid max-w-sm gap-2 border-t pt-4 text-sm">
            <div className="flex justify-between gap-4">
              <dt>Tổng tiền</dt>
              <dd className="font-medium tabular-nums">{formatCurrencyVnd(invoice.totalAmount)}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt>Đã thanh toán</dt>
              <dd className="font-medium tabular-nums">{formatCurrencyVnd(invoice.paidAmount)}</dd>
            </div>
            <div className="flex justify-between gap-4 border-t pt-2 text-base">
              <dt className="font-semibold">Còn phải thanh toán</dt>
              <dd className="font-semibold tabular-nums">
                {formatCurrencyVnd(invoice.outstandingBalance)}
              </dd>
            </div>
          </dl>
        </section>
      ) : null}

      {invoice && invoice.outstandingBalance <= 0 ? (
        <p className="rounded-lg border bg-card p-5 text-base" role="status">
          Hóa đơn này đã được thanh toán.
        </p>
      ) : null}

      {attempt?.status === "PENDING" ? (
        <section className="grid gap-6 rounded-lg border bg-card p-5 sm:grid-cols-2 sm:p-7">
          <div className="space-y-4">
            <h2 className="text-lg font-semibold">
              Quét mã QR bằng ứng dụng ngân hàng
            </h2>
            {remainingMs > 0 && attempt.expiresAt ? (
              <p className="text-base" role="timer" aria-live="off">
                Mã hết hạn sau{" "}
                <span className="font-semibold tabular-nums">
                  {countdownLabel(remainingMs)}
                </span>
              </p>
            ) : attempt.expiresAt ? (
              <p className="text-base text-destructive" role="status">
                Đã hết thời gian. Đang xác nhận trạng thái với PayOS…
              </p>
            ) : (
              <p className="text-base text-muted-foreground">
                Không nhận được thời hạn thanh toán từ PayOS.
              </p>
            )}
            {syncError ? (
              <p className="text-sm text-destructive" role="alert">
                Chưa lấy được trạng thái mới nhất. Hệ thống sẽ thử lại; bạn cũng
                có thể kiểm tra ngay.
              </p>
            ) : null}
            {attempt.checkoutUrl && remainingMs > 0 ? (
              <a
                className={publicButtonClass("primary")}
                href={attempt.checkoutUrl}
                rel="noreferrer"
                target="_blank"
              >
                Mở trang thanh toán PayOS
              </a>
            ) : null}
            <p className="text-sm text-muted-foreground">
              Không đóng trang này trong lúc thanh toán. Hệ thống kiểm tra trạng
              thái từ backend tự động.
            </p>
          </div>
          <div className="flex items-center justify-center rounded-lg bg-white p-5">
            {attempt.qrCode && remainingMs > 0 ? (
              <QRCodeSVG
                aria-label="Mã QR thanh toán PayOS"
                includeMargin
                size={256}
                value={attempt.qrCode}
              />
            ) : remainingMs > 0 ? (
              <p className="text-center text-sm text-muted-foreground">
                PayOS chưa trả dữ liệu QR. Hãy mở trang thanh toán PayOS.
              </p>
            ) : (
              <p className="text-center text-sm text-muted-foreground">
                Mã QR đã hết hạn.
              </p>
            )}
          </div>
          <div className="flex flex-wrap gap-3 sm:col-span-2">
            <button
              className={publicButtonClass("secondary")}
              onClick={() => void checkNow()}
              type="button"
            >
              Kiểm tra trạng thái
            </button>
            <Link className={publicButtonClass("secondary")} href={returnHref}>
              {appointmentId ? "Quay lại lịch hẹn" : "Về hóa đơn"}
            </Link>
          </div>
        </section>
      ) : null}

      {attempt?.status === "CONFIRMED" ? (
        <section
          className="space-y-3 rounded-lg border bg-card p-5"
          role="status"
        >
          <h2 className="text-lg font-semibold">Thanh toán thành công</h2>
          <p>PayOS đã xác nhận thanh toán cho hóa đơn này.</p>
          <Link
            className={publicButtonClass("primary")}
            href={invoice ? PATIENT_ROUTES.payInvoice(invoice.id) : returnHref}
          >
            Xem hóa đơn
          </Link>
        </section>
      ) : null}

      {attempt &&
      ["EXPIRED", "CANCELLED", "FAILED"].includes(attempt.status) ? (
        <section
          className="space-y-4 rounded-lg border bg-card p-5"
          role="status"
        >
          <div className="space-y-2">
            <h2 className="text-lg font-semibold">
              Mã thanh toán đã hết hiệu lực
            </h2>
            <p className="text-sm text-muted-foreground">
              Backend đã đối soát trạng thái với PayOS. Hóa đơn vẫn còn dư nợ;
              bạn có thể tạo mã mới để thanh toán.
            </p>
          </div>
          <button
            className={publicButtonClass("primary")}
            disabled={loading}
            onClick={retryPayment}
            type="button"
          >
            Tạo mã thanh toán mới
          </button>
        </section>
      ) : null}

      {attempt?.status === "RECONCILIATION_REQUIRED" ? (
        <section
          className="space-y-3 rounded-lg border bg-card p-5"
          role="alert"
        >
          <h2 className="text-lg font-semibold">Cần kiểm tra giao dịch</h2>
          <p className="text-sm text-muted-foreground">
            Backend chưa thể xác nhận trạng thái an toàn. Không tạo mã mới; vui
            lòng liên hệ lễ tân để đối soát giao dịch.
          </p>
        </section>
      ) : null}
    </div>
  );
}
