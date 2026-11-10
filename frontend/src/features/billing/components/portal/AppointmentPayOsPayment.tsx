"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import {
  clearPayOsIdempotencyKey,
  clearPayOsIdempotencyKeyForAppointment,
  createAppointmentInvoice,
  createPayOsAttempt,
  getBillingInvoice,
  getPayOsAttempt,
  InvoiceNotPayableError,
  payOsIdempotencyKey,
  reconcilePayOsAttempt,
  type BillingInvoice,
  type PayOsPaymentAttempt,
} from "../../api/payOsPaymentApi";
import { useHoldDeadline } from "../../hooks/useHoldDeadline";
import { CancelPaymentHoldButton } from "./CancelPaymentHoldButton";
import {
  PayOsHoldEndedNotice,
  PayOsInvoiceVoidNotice,
} from "./PayOsHoldEndedNotice";
import { PayOsInvoiceCard } from "./PayOsInvoiceCard";
import { PayOsPaidCard } from "./PayOsPaidCard";
import { countdownLabel, PayOsQrCard } from "./PayOsQrCard";
import { PayOsTransferDetails } from "./PayOsTransferDetails";

const POLL_INTERVAL_MS = 5_000;

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
  onFinish,
  paymentDeadline,
  onCancelHold,
  onBookAgain,
}: {
  appointmentId?: string;
  invoiceId?: string;
  /**
   * Có thì component chạy "nhúng" trong bước thanh toán của wizard đặt lịch: không vẽ tiêu đề
   * trang, bỏ các link rời trang và gọi `onFinish({ paid: true })` khi đã thanh toán xong.
   */
  onFinish?: (result: { paid: boolean }) => void;
  /**
   * Chế độ lịch: hạn giữ chỗ (ISO, ≤15 phút). Có thì hiện đếm ngược; tới hạn thì thay QR bằng
   * "Hết thời gian giữ lịch" và không cho tạo mã mới. Đồng hồ máy chỉ để hiển thị, BE quyết định.
   */
  paymentDeadline?: string;
  /** Chế độ lịch: có thì hiện nút "Hủy thanh toán"; xác nhận xong gọi callback (nơi ghép hủy giữ chỗ). */
  onCancelHold?: () => void | Promise<void>;
  /** Chế độ lịch: nút "Đặt lịch lại" khi hết giữ lịch; không truyền thì dùng link tới trang đặt lịch. */
  onBookAgain?: () => void;
}) {
  const embedded = onFinish !== undefined;
  const appointmentMode =
    invoiceId === undefined && appointmentId !== undefined;
  const deadline = useHoldDeadline(
    appointmentMode ? paymentDeadline : undefined,
  );
  const [invoice, setInvoice] = useState<BillingInvoice | null>(null);
  const [attempt, setAttempt] = useState<PayOsPaymentAttempt | null>(null);
  // BE đã từ chối thanh toán (hóa đơn VOID / INVOICE_NOT_PAYABLE): không còn tạo mã được nữa.
  const [notPayable, setNotPayable] = useState(false);
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

  // BE xác nhận không còn thanh toán được: bỏ attempt, xóa khóa idempotency để không dính attempt cũ.
  const markNotPayable = useCallback(
    (resolvedInvoiceId?: string) => {
      setAttempt(null);
      setNotPayable(true);
      if (resolvedInvoiceId) clearPayOsIdempotencyKey(resolvedInvoiceId);
      if (appointmentMode && appointmentId)
        clearPayOsIdempotencyKeyForAppointment(appointmentId);
    },
    [appointmentMode, appointmentId],
  );

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
      if (currentInvoice.status === "VOID") {
        markNotPayable(currentInvoice.id);
        return;
      }
      if (currentInvoice.outstandingBalance <= 0) {
        setAttempt(null);
        return;
      }
      const key = payOsIdempotencyKey(currentInvoice.id, renewKey);
      let currentAttempt: PayOsPaymentAttempt;
      try {
        currentAttempt = await createPayOsAttempt(
          currentInvoice.id,
          key,
          signal,
        );
      } catch (cause) {
        if (!(cause instanceof InvoiceNotPayableError)) throw cause;
        // INVOICE_NOT_PAYABLE cũng gặp khi hóa đơn vừa được trả đủ: đọc lại để phân biệt với đã hủy.
        const fresh = await getBillingInvoice(currentInvoice.id, signal).catch(
          () => null,
        );
        if (signal.aborted) return;
        if (fresh) setInvoice(fresh);
        if (fresh && fresh.status !== "VOID" && fresh.outstandingBalance <= 0) {
          setAttempt(null);
          return;
        }
        markNotPayable(currentInvoice.id);
        return;
      }
      if (!signal.aborted) {
        setRemainingMs(msUntilExpiry(currentAttempt.expiresAt));
        setAttempt(currentAttempt);
      }
    },
    [appointmentId, invoiceId, markNotPayable],
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
          if (cause instanceof InvoiceNotPayableError) markNotPayable();
          else
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
    [startPayment, markNotPayable],
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
        const shouldReconcile =
          isExpired(pendingAttemptExpiresAt) &&
          reconciledAttemptRef.current !== pendingAttemptId;
        if (shouldReconcile) reconciledAttemptRef.current = pendingAttemptId;
        const latest = shouldReconcile
          ? await reconcilePayOsAttempt(pendingAttemptId, controller.signal)
          : await getPayOsAttempt(pendingAttemptId, controller.signal);
        if (stopped || controller.signal.aborted) return;
        setRemainingMs(msUntilExpiry(latest.expiresAt));
        setAttempt(latest);
        if (latest.status === "CONFIRMED") {
          const refreshedInvoice = await getBillingInvoice(
            latest.invoiceId,
            controller.signal,
          );
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
      if (isExpired(attempt.expiresAt))
        reconciledAttemptRef.current = attempt.id;
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
        {embedded ? (
          <p className="sr-only" role="status">
            Đang tạo mã thanh toán
          </p>
        ) : (
          <PortalPageHeader title="Đang tạo mã thanh toán" />
        )}
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-72 w-full" />
      </div>
    );
  }

  // Đã trả đủ: hóa đơn hết dư nợ hoặc lần thanh toán vừa được xác nhận. Chỉ một card, một nút.
  const paid =
    (invoice !== null &&
      invoice.status !== "VOID" &&
      invoice.outstandingBalance <= 0) ||
    attempt?.status === "CONFIRMED";
  const expired =
    attempt !== null &&
    ["EXPIRED", "CANCELLED", "FAILED"].includes(attempt.status);
  // Chế độ lịch: hết giữ lịch khi BE từ chối (VOID/INVOICE_NOT_PAYABLE) hoặc đồng hồ tới hạn.
  // Đã trả thì luôn thắng: thanh toán tới sát hạn vẫn được hiển thị là đã trả.
  const holdOver = !paid && appointmentMode && (notPayable || deadline.ended);
  const invoiceVoid = !paid && !appointmentMode && notPayable;
  // "Tạo mã mới" chỉ khi còn trong hạn giữ lịch (hoặc không có hạn: BE quyết định khi gọi).
  const canRenew = !notPayable && !deadline.ended;
  // Trong wizard đặt lịch thanh toán là bắt buộc (lịch chỉ được xác nhận sau khi trả): không có lối
  // "trả sau tại quầy", chỉ có nút Hủy (khi nơi ghép truyền `onCancelHold`).
  const cancelButton =
    appointmentMode && onCancelHold && !paid && !holdOver ? (
      <CancelPaymentHoldButton onConfirm={onCancelHold} />
    ) : null;
  return (
    <div className="space-y-5">
      {embedded ? (
        <p className="text-base text-muted-foreground">
          Quét mã QR để thanh toán phí khám. Lịch khám được xác nhận sau khi
          giao dịch thành công.
        </p>
      ) : (
        <PortalPageHeader
          description="Hóa đơn được lấy từ backend. Trạng thái thanh toán chỉ được xác nhận sau khi backend nhận và xác minh thông báo từ PayOS."
          title="Thanh toán hóa đơn"
        />
      )}

      {error ? (
        <div className="space-y-4 rounded-xl border bg-card p-5">
          <p className="text-base text-destructive" role="alert">
            {error}
          </p>
          <div className="flex flex-wrap gap-3">
            <button
              className={publicButtonClass("secondary")}
              onClick={() => void load()}
              type="button"
            >
              Thử lại
            </button>
            {cancelButton}
          </div>
        </div>
      ) : null}

      {!paid &&
      !holdOver &&
      appointmentMode &&
      deadline.remainingMs !== null ? (
        <p
          className="w-fit rounded-full bg-secondary px-3 py-1 text-sm tabular-nums"
          role="timer"
        >
          Giữ lịch còn{" "}
          <span className="font-semibold">
            {countdownLabel(deadline.remainingMs)}
          </span>
        </p>
      ) : null}

      {paid ? (
        <PayOsPaidCard
          action={
            embedded ? (
              <button
                className={publicButtonClass("primary")}
                onClick={() => onFinish({ paid: true })}
                type="button"
              >
                Hoàn tất
              </button>
            ) : (
              <Link
                className={publicButtonClass("primary")}
                href={
                  invoice ? PATIENT_ROUTES.payInvoice(invoice.id) : returnHref
                }
              >
                Xem hóa đơn
              </Link>
            )
          }
          amount={invoice?.paidAmount || attempt?.amount || 0}
          invoiceNumber={invoice?.invoiceNumber ?? null}
        />
      ) : null}

      {holdOver ? <PayOsHoldEndedNotice onBookAgain={onBookAgain} /> : null}
      {invoiceVoid ? <PayOsInvoiceVoidNotice /> : null}

      {invoice && !holdOver ? <PayOsInvoiceCard invoice={invoice} /> : null}

      {!paid && !holdOver && attempt?.status === "PENDING" ? (
        <>
          <div className="grid items-start gap-4 md:grid-cols-2">
            <PayOsQrCard
              attempt={attempt}
              remainingMs={remainingMs}
              syncError={syncError}
            />
            {remainingMs > 0 ? (
              <PayOsTransferDetails amount={attempt.amount} attempt={attempt} />
            ) : null}
          </div>
          <div className="flex flex-wrap justify-end gap-3">
            <button
              className={publicButtonClass("secondary")}
              onClick={() => void checkNow()}
              type="button"
            >
              Kiểm tra trạng thái
            </button>
            {cancelButton}
            {embedded ? null : (
              <Link
                className={publicButtonClass("secondary")}
                href={returnHref}
              >
                {appointmentId ? "Quay lại lịch hẹn" : "Về hóa đơn"}
              </Link>
            )}
          </div>
        </>
      ) : null}

      {!paid && expired && canRenew ? (
        <section
          className="space-y-4 rounded-xl border bg-card p-5"
          role="status"
        >
          <div className="space-y-2">
            <h3 className="text-lg font-semibold">
              Mã thanh toán đã hết hiệu lực
            </h3>
            <p className="text-sm text-muted-foreground">
              Backend đã đối soát trạng thái với PayOS. Hóa đơn vẫn còn dư nợ;
              bạn có thể tạo mã mới để thanh toán.
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <button
              className={publicButtonClass("primary")}
              disabled={loading}
              onClick={retryPayment}
              type="button"
            >
              Tạo mã thanh toán mới
            </button>
            {cancelButton}
          </div>
        </section>
      ) : null}

      {!paid && attempt?.status === "RECONCILIATION_REQUIRED" ? (
        <section
          className="space-y-3 rounded-xl border bg-card p-5"
          role="alert"
        >
          <h3 className="text-lg font-semibold">Cần kiểm tra giao dịch</h3>
          <p className="text-sm text-muted-foreground">
            Backend chưa thể xác nhận trạng thái an toàn. Không tạo mã mới; vui
            lòng liên hệ lễ tân để đối soát giao dịch.
          </p>
        </section>
      ) : null}
    </div>
  );
}
