"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import type { BillingInvoice } from "@/lib/api/billingApi";
import {
  getReceptionPatientBilling,
  type ReceptionPatientBilling,
} from "@/lib/api/invoiceItemsApi";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { INVOICE_ITEM_STATUS_LABELS } from "@/lib/domain/billingLabels";
import { messageFromError } from "@/lib/messages/appMessages";

/**
 * Hóa đơn của một bệnh nhân, khoản chưa trả đứng trước (GET /reception/patients/{id}/billing).
 * Mỗi hóa đơn còn khoản PENDING có nút thu. Dựng lại bằng `key` khi cần tải lại.
 */
export function PatientBillingPanel({
  profileId,
  onCollect,
}: {
  profileId: string;
  onCollect: (invoice: BillingInvoice) => void;
}) {
  const [state, setState] = useState<{
    billing: ReceptionPatientBilling | null;
    error: string;
  }>({ billing: null, error: "" });

  useEffect(() => {
    let cancelled = false;
    getReceptionPatientBilling(profileId)
      .then((billing) => {
        if (!cancelled) setState({ billing, error: "" });
      })
      .catch((cause: unknown) => {
        if (!cancelled) setState({ billing: null, error: messageFromError(cause) });
      });
    return () => {
      cancelled = true;
    };
  }, [profileId]);

  const { billing, error } = state;
  if (error) return <p role="alert" className="text-sm text-destructive">{error}</p>;
  if (billing === null) return <p role="status" className="text-sm">Đang tải hóa đơn…</p>;
  if (billing.invoices.items.length === 0)
    return <p className="text-sm text-muted-foreground">Bệnh nhân chưa có hóa đơn nào.</p>;

  return (
    <section className="space-y-4" aria-label="Hóa đơn của bệnh nhân">
      {billing.invoices.items.map((invoice) => {
        const payable = invoice.items.some((item) => item.status === "PENDING");
        return (
          <article className="space-y-3 rounded-lg border bg-card p-4" key={invoice.id}>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="font-medium tabular-nums">{invoice.invoiceNumber}</p>
              <p className="text-sm">
                Còn phải thu{" "}
                <span className="font-semibold tabular-nums">
                  {formatCurrencyVnd(invoice.outstandingBalance)}
                </span>
              </p>
            </div>
            <ul className="divide-y">
              {invoice.items.map((item) => (
                <li className="flex flex-wrap justify-between gap-2 py-2" key={item.id}>
                  <span className="min-w-0 break-words">{item.description}</span>
                  <span className="flex shrink-0 gap-3 text-sm tabular-nums">
                    {formatCurrencyVnd(item.lineTotal)}
                    <span className="text-muted-foreground">
                      {INVOICE_ITEM_STATUS_LABELS[item.status]}
                    </span>
                  </span>
                </li>
              ))}
            </ul>
            {payable ? (
              <Button onClick={() => onCollect(invoice)}>Thu tiền</Button>
            ) : null}
          </article>
        );
      })}
    </section>
  );
}
