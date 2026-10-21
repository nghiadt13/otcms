"use client";

import { TableFrame } from "@/components/admin/TableFrame";
import { TableStateRows } from "@/components/admin/TableStateRows";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDateTime } from "@/lib/format/formatDate";
import {
  PAYMENT_METHOD_LABELS,
  type PaymentListItem,
  type PaymentSummary,
} from "../types/payment";
import { PaymentHistorySummaryRow } from "./PaymentHistorySummaryRow";

type PaymentHistoryTableProps = {
  items: PaymentListItem[];
  summary: PaymentSummary | null;
  loading: boolean;
  error: string | null;
  /** Khoảng ngày sai: không có dữ liệu để hiện, bảng nhắc chọn lại. */
  rangeInvalid: boolean;
  hasFilter: boolean;
  /** Không truyền thì ẩn "Thử lại" (lỗi 403 tải lại cũng vô ích). */
  onRetry?: () => void;
  onView: (id: string) => void;
};

const EMPTY_NO_DATA =
  "Chưa có khoản thanh toán nào trong khoảng ngày này. Khoản thu ở Lễ tân hoặc qua VNPay sẽ hiện ở đây sau khi hoàn tất.";
const EMPTY_NO_MATCH =
  "Không có khoản nào khớp bộ lọc. Đổi khoảng ngày, từ khóa hoặc phương thức để xem lại.";
const RANGE_INVALID = "Chọn lại khoảng ngày để xem thanh toán.";

export function PaymentHistoryTable({
  items,
  summary,
  loading,
  error,
  rangeInvalid,
  hasFilter,
  onRetry,
  onView,
}: PaymentHistoryTableProps) {
  const showData = !loading && !error && !rangeInvalid;
  const empty = rangeInvalid || (showData && items.length === 0);
  return (
    <TableFrame>
      <Table className="min-w-[860px]">
        <TableHeader>
          <TableRow>
            <TableHead>Số phiếu thu</TableHead>
            <TableHead>Ngày giờ</TableHead>
            <TableHead>Bệnh nhân</TableHead>
            <TableHead>Khoản đã trả</TableHead>
            <TableHead className="text-right">Số tiền</TableHead>
            <TableHead>Phương thức</TableHead>
            <TableHead>Thu ngân</TableHead>
            <TableHead>
              <span className="sr-only">Thao tác</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableStateRows
            colSpan={8}
            loading={loading && !rangeInvalid}
            error={rangeInvalid ? null : error}
            empty={empty}
            onRetry={onRetry}
            emptyText={
              rangeInvalid
                ? RANGE_INVALID
                : hasFilter
                  ? EMPTY_NO_MATCH
                  : EMPTY_NO_DATA
            }
          />
          {showData
            ? items.map((payment) => (
                <TableRow key={payment.id}>
                  <TableCell className="font-medium tabular-nums">
                    {payment.receiptNumber}
                  </TableCell>
                  <TableCell className="tabular-nums">
                    {formatDateTime(payment.paidAt)}
                  </TableCell>
                  <TableCell className="break-words whitespace-normal">
                    {payment.patientName}
                  </TableCell>
                  <TableCell className="whitespace-normal">
                    {payment.itemSummary}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCurrencyVnd(payment.amount)}
                  </TableCell>
                  <TableCell>{PAYMENT_METHOD_LABELS[payment.method]}</TableCell>
                  <TableCell>
                    {payment.cashierName ?? (
                      <span className="text-muted-foreground">—</span>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    {payment.allowedActions.includes("view") ? (
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label={`Xem phiếu thu ${payment.receiptNumber}`}
                        onClick={() => onView(payment.id)}
                      >
                        Xem
                      </Button>
                    ) : null}
                  </TableCell>
                </TableRow>
              ))
            : null}
        </TableBody>
        {showData && items.length > 0 && summary ? (
          <PaymentHistorySummaryRow summary={summary} />
        ) : null}
      </Table>
    </TableFrame>
  );
}
