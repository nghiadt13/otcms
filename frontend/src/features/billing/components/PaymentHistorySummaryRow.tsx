import { TableCell, TableFooter, TableRow } from "@/components/ui/table";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import {
  PAYMENT_METHOD_LABELS,
  type PaymentSummary,
} from "../types/payment";

/** Dòng tổng kỳ đang lọc. Số liệu do BE tính trên toàn kết quả lọc, không cộng theo trang. */
export function PaymentHistorySummaryRow({ summary }: { summary: PaymentSummary }) {
  return (
    <TableFooter>
      <TableRow>
        <TableCell colSpan={4}>Tổng kỳ đang lọc</TableCell>
        <TableCell className="text-right font-semibold tabular-nums">
          {formatCurrencyVnd(summary.totalAmount)}
        </TableCell>
        <TableCell
          colSpan={3}
          className="text-xs font-normal text-muted-foreground tabular-nums"
        >
          {PAYMENT_METHOD_LABELS.CASH} {formatCurrencyVnd(summary.cashAmount)} ·{" "}
          {PAYMENT_METHOD_LABELS.VNPAY} {formatCurrencyVnd(summary.vnpayAmount)}
        </TableCell>
      </TableRow>
    </TableFooter>
  );
}
