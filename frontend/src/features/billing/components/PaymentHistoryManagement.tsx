"use client";

import { TableFrame } from "@/components/admin/TableFrame";
import { TablePagination } from "@/components/admin/TablePagination";
import { TableStateRows } from "@/components/admin/TableStateRows";
import { PageHeader } from "@/components/PageHeader";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
import { formatAppMessage } from "@/lib/messages/appMessages";
import { usePaymentRecordList } from "@/hooks/usePaymentRecordList";
import { paymentChannelLabel } from "@/lib/domain/billingLabels";

/** Màn Lịch sử thanh toán (admin, owner): các giao dịch đã xác nhận, chỉ để xem. */
export function PaymentHistoryManagement() {
  const history = usePaymentRecordList();
  const rangeInvalid = history.rangeError !== null;
  const showData = !history.loading && !history.error && !rangeInvalid;

  return (
    <>
      <PageHeader
        title="Lịch sử thanh toán"
        description="Các giao dịch đã xác nhận, theo thời điểm thu. Chỉ để xem."
      />
      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor="payment-from">Từ ngày</Label>
          <Input
            id="payment-from"
            type="date"
            value={history.range.from}
            onChange={(event) =>
              history.setDateRange({
                from: event.target.value,
                to: history.range.to,
              })
            }
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="payment-to">Đến ngày</Label>
          <Input
            id="payment-to"
            type="date"
            value={history.range.to}
            onChange={(event) =>
              history.setDateRange({
                from: history.range.from,
                to: event.target.value,
              })
            }
          />
          {rangeInvalid && (
            <p role="alert" className="text-sm text-destructive">
              {formatAppMessage("MSG-54")}
            </p>
          )}
        </div>
      </div>
      <TableFrame>
        <Table className="min-w-[760px]">
          <TableHeader>
            <TableRow>
              <TableHead>Hóa đơn</TableHead>
              <TableHead>Thời điểm xác nhận</TableHead>
              <TableHead>Bệnh nhân (mã hồ sơ)</TableHead>
              <TableHead>Hình thức</TableHead>
              <TableHead className="text-right">Số tiền</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableStateRows
              colSpan={5}
              loading={history.loading && !rangeInvalid}
              error={rangeInvalid ? null : history.error}
              empty={!rangeInvalid && showData && history.items.length === 0}
              onRetry={history.reload}
              emptyText={
                rangeInvalid
                  ? "Chọn lại khoảng ngày để xem thanh toán."
                  : "Chưa có giao dịch nào trong khoảng ngày này."
              }
            />
            {showData
              ? history.items.map((payment) => (
                  <TableRow key={payment.id}>
                    <TableCell className="font-medium tabular-nums">
                      {payment.invoiceNumber}
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {formatDateTime(payment.confirmedAt)}
                    </TableCell>
                    <TableCell className="break-all">
                      {payment.patientId}
                    </TableCell>
                    <TableCell>{paymentChannelLabel(payment.channel)}</TableCell>
                    <TableCell className="text-right whitespace-nowrap tabular-nums">
                      {formatCurrencyVnd(payment.amount)}
                    </TableCell>
                  </TableRow>
                ))
              : null}
          </TableBody>
        </Table>
      </TableFrame>
      {showData ? (
        <TablePagination
          page={history.page}
          totalPages={history.totalPages}
          totalItems={history.totalItems}
          onPageChange={history.setPage}
        />
      ) : null}
    </>
  );
}
