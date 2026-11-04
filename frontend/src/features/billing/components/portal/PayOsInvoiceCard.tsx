import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import type { BillingInvoice } from "../../api/payOsPaymentApi";

// Card tóm tắt hóa đơn của bước thanh toán: mã hóa đơn, trạng thái, các dòng phí và tổng tiền.

const issuedFormat = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "medium",
  timeZone: "Asia/Ho_Chi_Minh",
});

function statusLabel(invoice: BillingInvoice): string {
  if (invoice.status === "PAID" || invoice.outstandingBalance <= 0)
    return "Đã thanh toán";
  return invoice.paidAmount > 0 ? "Đã thanh toán một phần" : "Chờ thanh toán";
}

export function PayOsInvoiceCard({ invoice }: { invoice: BillingInvoice }) {
  const paid = invoice.status === "PAID" || invoice.outstandingBalance <= 0;
  const issuedAt = new Date(invoice.issuedAt);
  return (
    <section
      aria-label="Hóa đơn"
      className="overflow-hidden rounded-xl border bg-card"
    >
      <header className="flex flex-wrap items-start justify-between gap-3 border-b bg-secondary/40 px-5 py-4">
        <div>
          <p className="text-sm text-muted-foreground">
            {invoice.appointmentId
              ? "Hóa đơn phí khám"
              : "Hóa đơn dịch vụ y tế"}
          </p>
          <h3 className="text-lg font-semibold tabular-nums">
            {invoice.invoiceNumber}
          </h3>
          {Number.isNaN(issuedAt.getTime()) ||
          issuedAt.getFullYear() < 2000 ? null : (
            <p className="text-sm text-muted-foreground">
              Ngày lập {issuedFormat.format(issuedAt)}
            </p>
          )}
        </div>
        <span
          className={`rounded-full border px-3 py-1 text-sm font-medium ${
            paid
              ? "border-primary/30 bg-primary/10 text-primary"
              : "bg-card text-foreground"
          }`}
        >
          {statusLabel(invoice)}
        </span>
      </header>

      <ul className="divide-y px-5">
        {invoice.items.map((item) => (
          <li
            className="flex items-start justify-between gap-4 py-3"
            key={item.lineNumber}
          >
            <div className="min-w-0">
              <p className="font-medium">
                {invoice.appointmentId ? "Phí khám" : "Dịch vụ y tế"}
              </p>
              <p className="text-sm text-muted-foreground break-words">
                {item.description}
                {item.quantity > 1 ? ` · SL ${item.quantity}` : ""}
              </p>
            </div>
            <p className="shrink-0 font-medium tabular-nums">
              {formatCurrencyVnd(item.lineTotal)}
            </p>
          </li>
        ))}
      </ul>

      <dl className="grid gap-1.5 border-t bg-secondary/40 px-5 py-4 text-sm">
        <div className="flex justify-between gap-4">
          <dt className="text-muted-foreground">Tổng tiền</dt>
          <dd className="tabular-nums">
            {formatCurrencyVnd(invoice.totalAmount)}
          </dd>
        </div>
        <div className="flex justify-between gap-4">
          <dt className="text-muted-foreground">Đã thanh toán</dt>
          <dd className="tabular-nums">
            {formatCurrencyVnd(invoice.paidAmount)}
          </dd>
        </div>
        <div className="mt-1 flex items-baseline justify-between gap-4 border-t pt-2">
          <dt className="text-base font-semibold">Còn phải thanh toán</dt>
          <dd className="text-xl font-semibold tabular-nums">
            {formatCurrencyVnd(invoice.outstandingBalance)}
          </dd>
        </div>
      </dl>
    </section>
  );
}
