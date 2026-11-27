import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatInteger } from "@/lib/format/formatNumber";
import type { FinanceSummary } from "../types/ownerFinance";

function Cell({
  label,
  value,
  note,
  tone,
}: {
  label: string;
  value: number;
  note: string;
  tone?: "pending";
}) {
  return (
    <div className="bg-card p-5">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd
        className={
          tone === "pending" && value > 0
            ? "mt-1 text-3xl font-semibold tracking-tight text-coral-strong tabular-nums"
            : "mt-1 text-3xl font-semibold tracking-tight tabular-nums"
        }
      >
        {formatCurrencyVnd(value)}
      </dd>
      <dd className="mt-1 text-xs text-muted-foreground">{note}</dd>
    </div>
  );
}

/** Ba con số của kỳ: tiền đã thu, đã hoàn lại khách, và còn nợ khách (chờ hoàn, lúc này). */
export function FinanceKpiStrip({ summary }: { summary: FinanceSummary }) {
  return (
    <dl className="grid grid-cols-1 gap-px overflow-hidden rounded-xl border bg-border sm:grid-cols-3">
      <Cell
        label="Đã thu trong kỳ"
        value={summary.collected}
        note="Tiền thanh toán đã xác nhận, chưa trừ khoản hoàn"
      />
      <Cell
        label="Đã hoàn trong kỳ"
        value={summary.refunded}
        note={`${formatInteger(summary.refundedCount)} khoản đã hoàn tiền cho khách`}
      />
      <Cell
        label="Đang chờ hoàn"
        value={summary.pending}
        tone="pending"
        note={`${formatInteger(summary.pendingCount)} khoản còn nợ khách, tính đến lúc này`}
      />
    </dl>
  );
}
