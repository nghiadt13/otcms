"use client";

import { TableFrame } from "@/components/admin/TableFrame";
import { TableStateRows } from "@/components/admin/TableStateRows";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDate, formatDateTime } from "@/lib/format/formatDate";
import { formatInteger } from "@/lib/format/formatNumber";
import {
  UNPAID_ITEM_REFERENCE_LABELS,
  type UnpaidItem,
  type UnpaidItemSummary,
} from "../types/unpaidItem";

type UnpaidItemsTableProps = {
  items: UnpaidItem[];
  totalItems: number;
  summary: UnpaidItemSummary | null;
  loading: boolean;
  error: string | null;
  hasFilter: boolean;
  onRetry: () => void;
  onView: (item: UnpaidItem) => void;
};

const EMPTY_NO_FILTER = (
  <>
    <span className="block font-medium text-foreground">
      Hiện không có khoản nào chờ thanh toán.
    </span>
    <span className="mt-1 block">
      Khoản mới sẽ hiện ở đây khi có phí khám hoặc chỉ định chưa thanh toán. Thu
      tiền do Lễ tân thực hiện.
    </span>
  </>
);
const EMPTY_FILTERED = (
  <>
    <span className="block font-medium text-foreground">
      Không có khoản nào khớp bộ lọc.
    </span>
    <span className="mt-1 block">
      Thử đổi khoảng ngày, từ khóa hoặc bỏ bớt bộ lọc.
    </span>
  </>
);

export function UnpaidItemsTable({
  items,
  totalItems,
  summary,
  loading,
  error,
  hasFilter,
  onRetry,
  onView,
}: UnpaidItemsTableProps) {
  const showData = !loading && !error;
  return (
    <TableFrame>
      <Table className="min-w-[720px]">
        <TableHeader>
          <TableRow>
            <TableHead>Bệnh nhân</TableHead>
            <TableHead>Lượt khám / Lịch hẹn</TableHead>
            <TableHead>Khoản</TableHead>
            <TableHead className="text-right">Giá</TableHead>
            <TableHead className="text-right">Thời gian chỉ định</TableHead>
            <TableHead>
              <span className="sr-only">Thao tác</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableStateRows
            colSpan={6}
            loading={loading}
            error={error}
            empty={showData && items.length === 0}
            onRetry={onRetry}
            emptyText={hasFilter ? EMPTY_FILTERED : EMPTY_NO_FILTER}
          />
          {showData
            ? items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="whitespace-normal">
                    <span className="block break-words">
                      {item.patient.fullName}
                    </span>
                    {item.patient.phone ? (
                      <span className="block text-xs text-muted-foreground tabular-nums">
                        {item.patient.phone}
                      </span>
                    ) : null}
                  </TableCell>
                  <TableCell>
                    <span className="block tabular-nums">
                      {item.reference.code} · {formatDate(item.reference.date)}
                    </span>
                    <span className="block text-xs text-muted-foreground">
                      {UNPAID_ITEM_REFERENCE_LABELS[item.reference.type]}
                    </span>
                  </TableCell>
                  <TableCell className="whitespace-normal">
                    {item.itemName}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCurrencyVnd(item.priceVnd)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatDateTime(item.orderedAt)}
                  </TableCell>
                  <TableCell className="text-right">
                    {item.allowedActions.includes("view") ? (
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label={`Xem khoản ${item.itemName} của ${item.patient.fullName}`}
                        onClick={() => onView(item)}
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
          <TableFooter>
            <TableRow>
              <TableCell colSpan={3}>
                Tổng {formatInteger(totalItems)} khoản chờ thanh toán
              </TableCell>
              <TableCell className="text-right font-semibold tabular-nums">
                {formatCurrencyVnd(summary.totalPriceVnd)}
              </TableCell>
              <TableCell colSpan={2} />
            </TableRow>
          </TableFooter>
        ) : null}
      </Table>
    </TableFrame>
  );
}
