"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { apiDownload } from "@/lib/api/apiDownload";
import {
  getPaymentReceipt,
  receiptPdfPath,
  type PaymentDetail,
} from "@/lib/api/paymentRecordsApi";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDateTime } from "@/lib/format/formatDate";
import { INVOICE_ITEM_STATUS_LABELS, paymentChannelLabel } from "@/lib/domain/billingLabels";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import { notifyError } from "@/lib/messages/notify";
import { FormModal, InfoRow } from "./ReceptionUi";

/**
 * Biên lai của một giao dịch đã xác nhận: tên bệnh nhân, các khoản, tổng, người thu. Lễ tân in
 * bằng trình duyệt hoặc tải PDF (kèm phiên đăng nhập qua apiDownload).
 */
export function PaymentReceiptDialog({
  paymentId,
  onClose,
}: {
  paymentId: string;
  onClose: () => void;
}) {
  const [state, setState] = useState<{
    receipt: PaymentDetail | null;
    error: string;
  }>({ receipt: null, error: "" });
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getPaymentReceipt(paymentId)
      .then((receipt) => {
        if (!cancelled) setState({ receipt, error: "" });
      })
      .catch((cause: unknown) => {
        if (!cancelled) setState({ receipt: null, error: messageFromError(cause) });
      });
    return () => {
      cancelled = true;
    };
  }, [paymentId]);

  async function downloadPdf() {
    setDownloading(true);
    try {
      await apiDownload(receiptPdfPath(paymentId));
    } catch (cause) {
      notifyError(cause);
    } finally {
      setDownloading(false);
    }
  }

  const { receipt, error } = state;
  return (
    <FormModal
      title="Biên lai thu tiền"
      description="Biên lai phản ánh một lần thu đã xác nhận. Có thể in hoặc tải PDF."
      onClose={onClose}
      wide
    >
      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : receipt === null ? (
        <p role="status" className="text-sm">
          Đang tải biên lai…
        </p>
      ) : (
        <div className="space-y-4">
          <dl>
            <InfoRow label="Số biên lai">{receipt.receiptNumber}</InfoRow>
            <InfoRow label="Bệnh nhân">{receipt.patientName}</InfoRow>
            <InfoRow label="Hóa đơn">{receipt.invoiceNumber}</InfoRow>
            <InfoRow label="Thời điểm">{formatDateTime(receipt.confirmedAt)}</InfoRow>
            <InfoRow label="Hình thức">{paymentChannelLabel(receipt.channel)}</InfoRow>
            {receipt.receivedByName ? (
              <InfoRow label="Người thu">{receipt.receivedByName}</InfoRow>
            ) : null}
            {receipt.providerTransactionReference ? (
              <InfoRow label="Mã giao dịch PayOS">
                {receipt.providerTransactionReference}
              </InfoRow>
            ) : null}
          </dl>
          <ul className="divide-y rounded-lg border">
            {receipt.items.map((line) => (
              <li className="flex justify-between gap-4 px-3 py-2" key={line.itemId}>
                <span className="min-w-0">
                  <span className="block break-words">{line.description}</span>
                  <span className="block text-xs text-muted-foreground">
                    {INVOICE_ITEM_STATUS_LABELS[line.status]}
                  </span>
                </span>
                <span className="shrink-0 tabular-nums">
                  {formatCurrencyVnd(line.lineTotal)}
                </span>
              </li>
            ))}
          </ul>
          <p className="flex justify-between font-semibold">
            <span>Tổng đã thu</span>
            <span className="tabular-nums">{formatCurrencyVnd(receipt.amount)}</span>
          </p>
          <p className="text-xs text-muted-foreground">
            {formatAppMessage("MSG-93")}
          </p>
          <div className="flex flex-wrap justify-end gap-2">
            <Button variant="outline" onClick={() => window.print()}>
              In biên lai
            </Button>
            <Button disabled={downloading} onClick={() => void downloadPdf()}>
              {downloading ? "Đang tải..." : "Tải PDF"}
            </Button>
          </div>
        </div>
      )}
    </FormModal>
  );
}
