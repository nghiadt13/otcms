"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import {
  getPayOsAttempt,
  reconcilePayOsAttempt,
  type PayOsPaymentAttempt,
} from "../../api/payOsPaymentApi";

const POLL_INTERVAL_MS = 5_000;

export function PayOsAttemptResultView({
  attemptId,
  cancelled = false,
}: {
  attemptId: string;
  cancelled?: boolean;
}) {
  const [attempt, setAttempt] = useState<PayOsPaymentAttempt | null>(null);
  const [error, setError] = useState(false);
  const [checking, setChecking] = useState(true);
  const attemptStatus = attempt?.status;

  const refresh = useCallback(async () => {
    setChecking(true);
    try {
      const next =
        cancelled || attemptStatus === "PENDING"
          ? await reconcilePayOsAttempt(attemptId)
          : await getPayOsAttempt(attemptId);
      setAttempt(next);
      setError(false);
    } catch {
      setError(true);
    } finally {
      setChecking(false);
    }
  }, [attemptStatus, attemptId, cancelled]);

  useEffect(() => {
    let stopped = false;
    let timeout: number | undefined;
    const run = async () => {
      setChecking(true);
      try {
        let next = await getPayOsAttempt(attemptId);
        if (next.status === "PENDING")
          next = await reconcilePayOsAttempt(attemptId);
        if (stopped) return;
        setAttempt(next);
        setError(false);
        if (next.status === "PENDING") {
          const poll = async () => {
            try {
              const latest = await getPayOsAttempt(attemptId);
              if (stopped) return;
              setAttempt(latest);
              setError(false);
              if (latest.status === "PENDING")
                timeout = window.setTimeout(poll, POLL_INTERVAL_MS);
            } catch {
              if (stopped) return;
              setError(true);
              timeout = window.setTimeout(poll, POLL_INTERVAL_MS * 2);
            }
          };
          timeout = window.setTimeout(poll, POLL_INTERVAL_MS);
        }
      } catch {
        if (stopped) return;
        setError(true);
        timeout = window.setTimeout(run, POLL_INTERVAL_MS * 2);
      } finally {
        if (!stopped) setChecking(false);
      }
    };
    void run();
    return () => {
      stopped = true;
      if (timeout !== undefined) window.clearTimeout(timeout);
    };
  }, [attemptId, cancelled]);

  const title =
    attempt?.status === "CONFIRMED"
      ? "Thanh toán thành công"
      : attempt?.status === "EXPIRED"
        ? "Mã thanh toán đã hết hạn"
        : attempt?.status === "CANCELLED" || attempt?.status === "FAILED"
          ? "Thanh toán chưa hoàn tất"
          : attempt?.status === "RECONCILIATION_REQUIRED"
            ? "Giao dịch cần đối soát"
            : "Đang kiểm tra thanh toán";

  return (
    <div className="space-y-6">
      <PortalPageHeader title={title} />
      {!attempt && !error ? (
        <div aria-busy="true" className="space-y-3">
          <Skeleton className="h-6 w-2/3" />
          <Skeleton className="h-24 w-full" />
        </div>
      ) : null}
      {error ? (
        <p
          className="rounded-lg border bg-card p-5 text-base text-destructive"
          role="alert"
        >
          Chưa lấy được trạng thái từ backend. Hãy thử kiểm tra lại.
        </p>
      ) : null}
      {attempt ? (
        <section className="space-y-4 rounded-lg border bg-card p-5">
          <dl className="grid gap-3 sm:grid-cols-2">
            <div>
              <dt className="text-sm text-muted-foreground">Số tiền</dt>
              <dd className="font-semibold tabular-nums">
                {formatCurrencyVnd(attempt.amount)}
              </dd>
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">
                Mã payment attempt
              </dt>
              <dd className="break-all font-mono text-sm">{attempt.id}</dd>
            </div>
          </dl>
          {attempt.status === "CONFIRMED" ? (
            <p className="text-base" role="status">
              Backend đã xác nhận giao dịch từ webhook hoặc đối soát PayOS.
            </p>
          ) : attempt.status === "PENDING" ? (
            <p className="text-base" role="status">
              {cancelled
                ? "Đang cập nhật kết quả hủy với PayOS."
                : "PayOS chưa xác nhận giao dịch. Trang sẽ tiếp tục kiểm tra backend."}
            </p>
          ) : attempt.status === "RECONCILIATION_REQUIRED" ? (
            <p className="text-base text-destructive" role="alert">
              Backend cần nhân viên đối soát giao dịch này. Không thanh toán lại
              cho đến khi được xác nhận.
            </p>
          ) : (
            <p className="text-base" role="status">
              Giao dịch chưa hoàn tất. Hóa đơn vẫn có thể còn dư nợ.
            </p>
          )}
        </section>
      ) : null}
      <div className="flex flex-wrap gap-3">
        {attempt &&
        ["EXPIRED", "CANCELLED", "FAILED"].includes(attempt.status) ? (
          <Link
            className={publicButtonClass("primary")}
            href={PATIENT_ROUTES.payInvoice(attempt.invoiceId)}
          >
            Tạo mã thanh toán mới
          </Link>
        ) : null}
        {attempt?.status === "PENDING" || error ? (
          <button
            className={publicButtonClass("primary")}
            disabled={checking}
            onClick={() => void refresh()}
            type="button"
          >
            {checking ? "Đang kiểm tra…" : "Kiểm tra lại"}
          </button>
        ) : null}
        <Link
          className={publicButtonClass("secondary")}
          href={PATIENT_ROUTES.payments}
        >
          Về trang thanh toán
        </Link>
      </div>
    </div>
  );
}
