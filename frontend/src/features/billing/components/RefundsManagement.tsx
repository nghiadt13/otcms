"use client";

import { useState } from "react";
import { TableFrame } from "@/components/admin/TableFrame";
import { TablePagination } from "@/components/admin/TablePagination";
import { TableStateRows } from "@/components/admin/TableStateRows";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { RefundItem, RefundTab } from "@/lib/api/refundsApi";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDateTime } from "@/lib/format/formatDate";
import { useRefundList } from "../hooks/useRefundList";
import { RefundConfirmDialog } from "./RefundConfirmDialog";

const TABS: { value: RefundTab; label: string }[] = [
  { value: "PENDING", label: "Chờ hoàn tiền" },
  { value: "REFUNDED", label: "Đã hoàn tiền" },
];

/**
 * Màn Chờ hoàn tiền (admin, owner): khoản REFUND_PENDING do lịch bị hủy bởi phòng khám, chỉ định
 * không thực hiện được hoặc webhook tới muộn. Không gọi API hoàn tiền của PayOS.
 */
export function RefundsManagement() {
  const refunds = useRefundList();
  const [target, setTarget] = useState<RefundItem | null>(null);
  const isPending = refunds.tab === "PENDING";
  const showData = !refunds.loading && !refunds.error;

  return (
    <>
      <PageHeader
        title="Chờ hoàn tiền"
        description="Khoản đã thu nhưng phòng khám phải hoàn. Hoàn tiền ngoài hệ thống, rồi bấm xác nhận."
      />
      <div
        role="group"
        aria-label="Trạng thái hoàn tiền"
        className="mb-4 flex gap-2"
      >
        {TABS.map((tab) => (
          <Button
            key={tab.value}
            variant={refunds.tab === tab.value ? "default" : "outline"}
            aria-pressed={refunds.tab === tab.value}
            onClick={() => refunds.selectTab(tab.value)}
          >
            {tab.label}
          </Button>
        ))}
      </div>
      <TableFrame>
        <Table className="min-w-[860px]">
          <TableHeader>
            <TableRow>
              <TableHead>Bệnh nhân</TableHead>
              <TableHead>Hóa đơn</TableHead>
              <TableHead>Khoản</TableHead>
              <TableHead className="text-right">Số tiền</TableHead>
              <TableHead>Lý do</TableHead>
              <TableHead>{isPending ? "Thao tác" : "Đã hoàn lúc"}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableStateRows
              colSpan={6}
              loading={refunds.loading}
              error={refunds.error}
              empty={showData && refunds.items.length === 0}
              onRetry={refunds.reload}
              emptyText={
                isPending
                  ? "Không có khoản nào chờ hoàn tiền."
                  : "Chưa có khoản nào được xác nhận đã hoàn tiền."
              }
            />
            {showData
              ? refunds.items.map((item) => (
                  <TableRow key={item.itemId}>
                    <TableCell className="break-words whitespace-normal">
                      {item.patientName}
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {item.invoiceNumber}
                    </TableCell>
                    <TableCell className="break-words whitespace-normal">
                      {item.description}
                    </TableCell>
                    <TableCell className="text-right whitespace-nowrap tabular-nums">
                      {formatCurrencyVnd(item.amount)}
                    </TableCell>
                    <TableCell className="break-words whitespace-normal">
                      {item.reason ?? "—"}
                    </TableCell>
                    <TableCell>
                      {isPending ? (
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => setTarget(item)}
                        >
                          Đã hoàn tiền
                        </Button>
                      ) : item.refundedAt ? (
                        formatDateTime(item.refundedAt)
                      ) : (
                        "—"
                      )}
                    </TableCell>
                  </TableRow>
                ))
              : null}
          </TableBody>
        </Table>
      </TableFrame>
      {showData ? (
        <TablePagination
          page={refunds.page}
          totalPages={refunds.totalPages}
          totalItems={refunds.totalItems}
          onPageChange={refunds.setPage}
        />
      ) : null}
      {target ? (
        <RefundConfirmDialog
          item={target}
          onClose={() => setTarget(null)}
          onConfirmed={() => {
            setTarget(null);
            refunds.reload();
          }}
        />
      ) : null}
    </>
  );
}
