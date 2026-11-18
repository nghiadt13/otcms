"use client";

import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { getPaymentDetail } from "@/lib/api/paymentRecordsApi";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDateTime } from "@/lib/format/formatDate";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { useBillingLoad } from "../../hooks/useBillingLoad";
import {
  INVOICE_ITEM_STATUS_LABELS,
  paymentChannelLabel,
} from "@/lib/domain/billingLabels";
import { PaymentBackLink } from "./PaymentBackLink";
import { PaymentErrorBlock } from "./PaymentErrorBlock";
import { PaymentNotFoundView } from "./PaymentNotFoundView";
import {
  isMissingRecordError,
  LIST_FRAME_CLASS,
} from "./patientBillingPresentation";

/**
 * Phiếu thu của một giao dịch đã xác nhận (GET /api/v1/payments/{id}). Chỉ xem: in và tải PDF là
 * việc của lễ tân. Mã không thuộc tài khoản hoặc chưa xác nhận: coi như không tìm thấy.
 */
export function ReceiptView({ paymentId }: { paymentId: string }) {
  const { data, loading, error, reload } = useBillingLoad(
    (signal) => getPaymentDetail(paymentId, signal),
    paymentId,
  );

  if (error && isMissingRecordError(error)) return <PaymentNotFoundView />;

  return (
    <div className="space-y-8">
      <PaymentBackLink
        href={PATIENT_ROUTES.paymentHistory}
        label="Lịch sử thanh toán"
      />
      <PortalPageHeader
        description={data ? `Số phiếu ${data.receiptNumber}` : undefined}
        title="Phiếu thu"
      />

      {loading ? (
        <Skeleton className="h-48 w-full" />
      ) : error ? (
        <PaymentErrorBlock message={error.message} onRetry={reload} />
      ) : data ? (
        <div className="space-y-6">
          <dl className="grid gap-3 text-base sm:grid-cols-2">
            <div>
              <dt className="text-muted-foreground">Bệnh nhân</dt>
              <dd className="font-medium">{data.patientName}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Hóa đơn</dt>
              <dd className="font-medium tabular-nums">{data.invoiceNumber}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Thời điểm thu</dt>
              <dd>{formatDateTime(data.confirmedAt)}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Hình thức</dt>
              <dd>{paymentChannelLabel(data.channel)}</dd>
            </div>
          </dl>
          <ul className={LIST_FRAME_CLASS}>
            {data.items.map((line) => (
              <li
                className="flex items-start justify-between gap-4 px-4 py-3"
                key={line.itemId}
              >
                <span className="min-w-0">
                  <span className="block break-words">{line.description}</span>
                  <span className="block text-sm text-muted-foreground">
                    {INVOICE_ITEM_STATUS_LABELS[line.status]}
                  </span>
                </span>
                <span className="shrink-0 tabular-nums">
                  {formatCurrencyVnd(line.lineTotal)}
                </span>
              </li>
            ))}
          </ul>
          <p className="flex justify-between gap-4 text-lg font-semibold">
            <span>Tổng đã thu</span>
            <span className="tabular-nums">
              {formatCurrencyVnd(data.amount)}
            </span>
          </p>
          <p className="text-sm text-muted-foreground">
            {formatAppMessage("MSG-93")}
          </p>
        </div>
      ) : null}
    </div>
  );
}
