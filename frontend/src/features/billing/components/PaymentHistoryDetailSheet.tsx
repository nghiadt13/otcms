"use client";

import { DetailList } from "@/components/admin/DetailList";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDateTime } from "@/lib/format/formatDate";
import { usePaymentDetail } from "../hooks/usePaymentDetail";
import { PAYMENT_METHOD_LABELS, type PaymentDetail } from "../types/payment";
import { BillingDetailSheetFrame } from "./BillingDetailSheetFrame";

type PaymentHistoryDetailSheetProps = {
  paymentId: string;
  /** Số phiếu thu của dòng đã chọn, để có tiêu đề ngay khi chi tiết còn đang tải. */
  receiptNumber: string;
  onClose: () => void;
};

function detailItems(payment: PaymentDetail) {
  const items = [
    { label: "Số phiếu thu", value: payment.receiptNumber },
    { label: "Ngày giờ", value: <span className="tabular-nums">{formatDateTime(payment.paidAt)}</span> },
    { label: "Bệnh nhân", value: payment.patientName },
    { label: "Phương thức", value: PAYMENT_METHOD_LABELS[payment.method] },
  ];
  if (payment.cashierName)
    items.push({ label: "Thu ngân", value: payment.cashierName });
  if (payment.vnpayTransactionCode)
    items.push({
      label: "Mã giao dịch VNPay",
      value: <span className="tabular-nums">{payment.vnpayTransactionCode}</span>,
    });
  return items;
}

export function PaymentHistoryDetailSheet({
  paymentId,
  receiptNumber,
  onClose,
}: PaymentHistoryDetailSheetProps) {
  const { detail, loading, error, retryable, reload } =
    usePaymentDetail(paymentId);
  return (
    <BillingDetailSheetFrame
      title={`Phiếu thu ${receiptNumber}`}
      description="Chi tiết khoản thanh toán, chỉ để xem."
      loading={loading}
      error={error}
      onRetry={retryable ? reload : undefined}
      onClose={onClose}
    >
      {detail ? (
        <div className="grid gap-6">
          <DetailList items={detailItems(detail)} />
          <section aria-label="Khoản đã trả">
            <h3 className="mb-2 text-sm font-medium text-muted-foreground">
              Khoản đã trả
            </h3>
            <ul className="divide-y rounded-lg border text-sm">
              {detail.lines.map((line, index) => (
                <li
                  key={`${line.name}-${index}`}
                  className="flex items-start justify-between gap-4 px-3 py-2"
                >
                  <span className="min-w-0 break-words">{line.name}</span>
                  <span className="shrink-0 text-right tabular-nums">
                    {formatCurrencyVnd(line.price)}
                  </span>
                </li>
              ))}
              <li className="flex items-start justify-between gap-4 bg-muted/50 px-3 py-2 font-semibold">
                <span>Tổng</span>
                <span className="text-right tabular-nums">
                  {formatCurrencyVnd(detail.amount)}
                </span>
              </li>
            </ul>
          </section>
        </div>
      ) : null}
    </BillingDetailSheetFrame>
  );
}
