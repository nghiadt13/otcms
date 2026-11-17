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
import { useUnpaidInvoiceItemList } from "../hooks/useUnpaidInvoiceItemList";
import { INVOICE_ITEM_STATUS_LABELS } from "@/lib/domain/billingLabels";

/** Màn Khoản chưa thanh toán (admin, owner): khoản PENDING hoặc đang thanh toán, chỉ để xem. */
export function UnpaidItemsManagement() {
  const unpaid = useUnpaidInvoiceItemList();
  const rangeInvalid = unpaid.rangeError !== null;
  const showData = !unpaid.loading && !unpaid.error && !rangeInvalid;

  return (
    <>
      <PageHeader
        title="Khoản chưa thanh toán"
        description="Các khoản chỉ định và phí khám còn phải thu, theo ngày phát sinh."
      />
      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor="unpaid-from">Từ ngày</Label>
          <Input
            id="unpaid-from"
            type="date"
            value={unpaid.range.from}
            onChange={(event) =>
              unpaid.setDateRange({
                from: event.target.value,
                to: unpaid.range.to,
              })
            }
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="unpaid-to">Đến ngày</Label>
          <Input
            id="unpaid-to"
            type="date"
            value={unpaid.range.to}
            onChange={(event) =>
              unpaid.setDateRange({
                from: unpaid.range.from,
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
        <Table className="min-w-[860px]">
          <TableHeader>
            <TableRow>
              <TableHead>Bệnh nhân</TableHead>
              <TableHead>Khoản</TableHead>
              <TableHead>Hóa đơn</TableHead>
              <TableHead>Ngày phát sinh</TableHead>
              <TableHead className="text-right">Số tiền</TableHead>
              <TableHead>Trạng thái</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableStateRows
              colSpan={6}
              loading={unpaid.loading && !rangeInvalid}
              error={rangeInvalid ? null : unpaid.error}
              empty={!rangeInvalid && showData && unpaid.items.length === 0}
              onRetry={unpaid.reload}
              emptyText={
                rangeInvalid
                  ? "Chọn lại khoảng ngày để xem khoản chưa thanh toán."
                  : "Không có khoản nào còn phải thu trong khoảng ngày này."
              }
            />
            {showData
              ? unpaid.items.map((item) => (
                  <TableRow key={item.itemId}>
                    <TableCell className="break-words whitespace-normal">
                      {item.patientName}
                    </TableCell>
                    <TableCell className="break-words whitespace-normal">
                      {item.description}
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {item.invoiceNumber}
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {formatDateTime(item.orderedAt)}
                    </TableCell>
                    <TableCell className="text-right whitespace-nowrap tabular-nums">
                      {formatCurrencyVnd(item.lineTotal)}
                    </TableCell>
                    <TableCell>{INVOICE_ITEM_STATUS_LABELS[item.status]}</TableCell>
                  </TableRow>
                ))
              : null}
          </TableBody>
        </Table>
      </TableFrame>
      {showData ? (
        <TablePagination
          page={unpaid.page}
          totalPages={unpaid.totalPages}
          totalItems={unpaid.totalItems}
          onPageChange={unpaid.setPage}
        />
      ) : null}
    </>
  );
}
