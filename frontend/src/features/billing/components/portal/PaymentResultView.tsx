"use client";

import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import {
  type PaymentDisplayStatus,
  usePaymentStatusPolling,
} from "../../hooks/usePaymentStatusPolling";
import { PaymentErrorBlock } from "./PaymentErrorBlock";
import { PaymentNotFoundView } from "./PaymentNotFoundView";
import {
  COUNTER_PAYMENT_HINT,
  LIST_FRAME_CLASS,
  RECONCILE_NOTICE,
  isMissingRecordError,
  sourceDetailHref,
} from "./patientBillingPresentation";

const TITLE: Record<PaymentDisplayStatus, string> = {
  CHECKING: "Đang kiểm tra thanh toán",
  SUCCESS: "Thanh toán thành công",
  PENDING: "Đang chờ xác nhận",
  FAILED: "Thanh toán chưa thành công",
};

/**
 * Kết quả sau khi VNPay chuyển về (`?paymentId=`). Chỉ tin trạng thái BE trả về: không đọc
 * tham số của VNPay trên URL.
 */
export function PaymentResultView({ paymentId }: { paymentId: string }) {
  const { payment, status, checking, error, checkNow } =
    usePaymentStatusPolling(paymentId);

  if (error && isMissingRecordError(error)) return <PaymentNotFoundView />;

  const paidList =
    status === "SUCCESS" && payment ? payment.queueAssignments : [];

  return (
    <div className="space-y-8">
      <PortalPageHeader title={TITLE[status]} />

      {status === "CHECKING" && !error ? (
        <div aria-busy="true" className="space-y-3">
          <Skeleton className="h-6 w-2/3" />
          <Skeleton className="h-24 w-full" />
        </div>
      ) : null}

      {error ? (
        <PaymentErrorBlock
          message={messageFromError(error)}
          onRetry={checkNow}
        />
      ) : null}

      {status === "SUCCESS" ? (
        <p className="text-base" role="status">
          {formatAppMessage("MSG-51")}
        </p>
      ) : null}
      {status === "PENDING" ? (
        <div className="space-y-2">
          <p className="text-base" role="status">
            {formatAppMessage("MSG-52")}
          </p>
          <p className="text-sm text-muted-foreground">
            Trang này tự kiểm tra lại mỗi 10 giây, tối đa 15 phút.
          </p>
        </div>
      ) : null}
      {status === "FAILED" ? (
        <div className="space-y-2">
          <p className="text-base text-destructive" role="alert">
            {formatAppMessage("MSG-53")}
          </p>
          <p className="text-base">{COUNTER_PAYMENT_HINT}</p>
        </div>
      ) : null}

      {paidList.length > 0 ? (
        <section aria-labelledby="queue-heading" className="space-y-3">
          <h2 className="text-lg font-semibold" id="queue-heading">
            Số thứ tự của bạn
          </h2>
          <ul className={LIST_FRAME_CLASS}>
            {paidList.map((assignment) => (
              <li
                className="flex items-center justify-between gap-4 px-4 py-4 text-base"
                key={`${assignment.roomName}-${assignment.queueNumber}`}
              >
                <span>{assignment.roomName}</span>
                <span className="text-xl font-semibold tabular-nums">
                  Số {assignment.queueNumber}
                </span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {payment ? (
        <dl className="grid gap-4 rounded-lg border bg-card px-4 py-4 text-base sm:grid-cols-2">
          <div className="space-y-1">
            <dt className="text-sm text-muted-foreground">Người được khám</dt>
            <dd className="font-medium">{payment.patientName}</dd>
          </div>
          <div className="space-y-1">
            <dt className="text-sm text-muted-foreground">Số tiền</dt>
            <dd className="font-semibold tabular-nums">
              {formatCurrencyVnd(payment.total)}
            </dd>
          </div>
          <div className="space-y-1">
            <dt className="text-sm text-muted-foreground">Mã thanh toán</dt>
            <dd className="break-all">{payment.id}</dd>
          </div>
          {payment.vnpayRef ? (
            <div className="space-y-1">
              <dt className="text-sm text-muted-foreground">
                Mã giao dịch VNPay
              </dt>
              <dd className="break-all">{payment.vnpayRef}</dd>
            </div>
          ) : null}
        </dl>
      ) : null}

      {status !== "CHECKING" ? (
        <div className="space-y-6">
          <div className="flex flex-col gap-3 sm:flex-row">
            {status === "PENDING" ? (
              <button
                aria-busy={checking}
                className={publicButtonClass("secondary")}
                disabled={checking}
                onClick={checkNow}
                type="button"
              >
                {checking ? "Đang kiểm tra..." : "Kiểm tra lại"}
              </button>
            ) : null}
            {status === "SUCCESS" && payment?.sourceKind === "visit" ? (
              <Link
                className={publicButtonClass("primary")}
                href={PATIENT_ROUTES.visitDetail(payment.sourceId)}
              >
                Xem tiến độ lượt khám
              </Link>
            ) : null}
            {status === "SUCCESS" ? (
              <Link
                className={publicButtonClass(
                  payment?.sourceKind === "visit" ? "secondary" : "primary",
                )}
                href={PATIENT_ROUTES.payments}
              >
                Về trang Thanh toán
              </Link>
            ) : null}
            {status === "FAILED" && payment ? (
              <Link
                className={publicButtonClass("primary")}
                href={sourceDetailHref(payment.sourceKind, payment.sourceId)}
              >
                Thử lại
              </Link>
            ) : null}
            {status === "FAILED" ? (
              <Link
                className={publicButtonClass("secondary")}
                href={PATIENT_ROUTES.payments}
              >
                Về trang Thanh toán
              </Link>
            ) : null}
          </div>
          <p className="text-sm text-muted-foreground">{RECONCILE_NOTICE}</p>
        </div>
      ) : null}
    </div>
  );
}
