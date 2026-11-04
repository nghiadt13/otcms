import { CircleCheck } from "lucide-react";
import type { ReactNode } from "react";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";

// Card kết quả khi đã thanh toán: một thông báo và đúng một nút hành động (`action`).

export function PayOsPaidCard({
  amount,
  invoiceNumber,
  action,
}: {
  amount: number;
  invoiceNumber: string | null;
  action: ReactNode;
}) {
  return (
    <section
      className="flex flex-col items-center gap-3 rounded-xl border border-primary/30 bg-primary/5 px-5 py-8 text-center"
      role="status"
    >
      <CircleCheck aria-hidden className="size-12 text-primary" />
      <h3 className="text-xl font-semibold">Thanh toán thành công</h3>
      <p className="text-base text-muted-foreground">
        Đã thanh toán{" "}
        <span className="font-semibold tabular-nums text-foreground">
          {formatCurrencyVnd(amount)}
        </span>
        {invoiceNumber ? (
          <>
            {" "}
            cho hóa đơn <span className="tabular-nums">{invoiceNumber}</span>
          </>
        ) : null}
        .
      </p>
      <div className="mt-2">{action}</div>
    </section>
  );
}
