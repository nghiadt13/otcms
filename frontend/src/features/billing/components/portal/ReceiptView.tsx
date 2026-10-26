"use client";

import { StatusBadge } from "@/components/admin/StatusBadge";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import {
  PAYMENT_METHOD_LABELS,
  PAYMENT_STATUS_LABELS,
  PAYMENT_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDateTime } from "@/lib/format/formatDate";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { getPayment } from "../../api/patientBillingApi";
import { useBillingLoad } from "../../hooks/useBillingLoad";
import type { PatientPayment } from "../../types/patientBilling";
import { PaymentBackLink } from "./PaymentBackLink";
import { PaymentErrorBlock } from "./PaymentErrorBlock";
import { PaymentNotFoundView } from "./PaymentNotFoundView";
import {
  LIST_FRAME_CLASS,
  isMissingRecordError,
} from "./patientBillingPresentation";

/**
 * Phiếu thu của một thanh toán đã hoàn tất (UC56). Chỉ xem: không in hay tải về ở khu bệnh nhân
 * (phiếu thu in thuộc lễ tân, UC58). Thanh toán chưa hoàn tất coi như không tìm thấy.
 */
export function ReceiptView({ paymentId }: { paymentId: string }) {
  const { data, loading, error, reload } = useBillingLoad(
    (signal) => getPayment(paymentId, signal),
    paymentId,
  );

  if (error && isMissingRecordError(error)) return <PaymentNotFoundView />;
  if (data && data.status !== "SUCCESS") return <PaymentNotFoundView />;

  return (
    <div className="space-y-8">
      <PaymentBackLink
        href={PATIENT_ROUTES.paymentHistory}
        label="Lịch sử thanh toán"
      />
      <PortalPageHeader
        description={data?.receiptNo ? `Số phiếu ${data.receiptNo}` : undefined}
        title="Phiếu thu"
      />

      {loading ? (
        <div aria-busy="true" className="space-y-3">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : error ? (
        <PaymentErrorBlock message={messageFromError(error)} onRetry={reload} />
      ) : data ? (
        <ReceiptDetails payment={data} />
      ) : null}
    </div>
  );
}

function ReceiptDetails({ payment }: { payment: PatientPayment }) {
  return (
    <div className="space-y-8">
      <dl className="grid gap-4 rounded-lg border bg-card px-4 py-4 text-base sm:grid-cols-2">
        <div className="space-y-1">
          <dt className="text-sm text-muted-foreground">Người được khám</dt>
          <dd className="font-medium">{payment.patientName}</dd>
        </div>
        <div className="space-y-1">
          <dt className="text-sm text-muted-foreground">
            Thời điểm thanh toán
          </dt>
          <dd className="tabular-nums">
            {payment.paidAt ? formatDateTime(payment.paidAt) : "—"}
          </dd>
        </div>
        <div className="space-y-1">
          <dt className="text-sm text-muted-foreground">Phương thức</dt>
          <dd>{PAYMENT_METHOD_LABELS[payment.method]}</dd>
        </div>
        {payment.vnpayRef ? (
          <div className="space-y-1">
            <dt className="text-sm text-muted-foreground">
              Mã giao dịch VNPay
            </dt>
            <dd className="break-all">{payment.vnpayRef}</dd>
          </div>
        ) : null}
        <div className="space-y-1">
          <dt className="text-sm text-muted-foreground">Trạng thái</dt>
          <dd>
            <StatusBadge tone={PAYMENT_STATUS_TONE.SUCCESS}>
              {PAYMENT_STATUS_LABELS.SUCCESS}
            </StatusBadge>
          </dd>
        </div>
      </dl>

      <section aria-labelledby="receipt-items-heading" className="space-y-3">
        <h2 className="text-lg font-semibold" id="receipt-items-heading">
          Các khoản đã thanh toán
        </h2>
        <ul className={LIST_FRAME_CLASS}>
          {payment.items.map((item, index) => (
            <li
              className="flex items-center justify-between gap-4 px-4 py-3 text-base"
              key={`${item.label}-${index}`}
            >
              <span>{item.label}</span>
              <span className="shrink-0 tabular-nums">
                {formatCurrencyVnd(item.amount)}
              </span>
            </li>
          ))}
          <li className="flex items-center justify-between gap-4 px-4 py-3 text-base font-semibold">
            <span>Tổng cộng</span>
            <span className="shrink-0 tabular-nums">
              {formatCurrencyVnd(payment.total)}
            </span>
          </li>
        </ul>
      </section>
    </div>
  );
}
