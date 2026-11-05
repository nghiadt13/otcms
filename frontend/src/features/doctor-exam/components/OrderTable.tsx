"use client";

import { Ban, ScanSearch } from "lucide-react";
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
import type { ParaclinicalOrder } from "../types/doctorExam";
import { OrderStatusBadge } from "./doctorStatus";

// Danh sách chỉ định của lượt khám (UC45/UC52): trạng thái từng chỉ định đến khi có kết quả,
// mở ca chụp để đọc phim (UC49/UC50) hoặc hủy chỉ định chưa thực hiện.

const KIND_LABEL = { IMAGING: "CĐHA", LABORATORY: "Xét nghiệm" } as const;

/** Chỉ hiện nút hủy khi BE hủy được ngay (chưa có ảnh/kết quả, UC45 E4). */
export function canCancel(order: ParaclinicalOrder): boolean {
  return order.cancellable;
}

export function OrderTable({
  orders,
  readOnly,
  onOpenFindings,
  onCancel,
}: {
  orders: readonly ParaclinicalOrder[];
  readOnly: boolean;
  onOpenFindings: (order: ParaclinicalOrder) => void;
  onCancel: (order: ParaclinicalOrder) => void;
}) {
  return (
    <div className="overflow-x-auto rounded-lg border bg-card">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Chỉ định</TableHead>
            <TableHead className="hidden lg:table-cell">
              Lý do / vùng chụp
            </TableHead>
            <TableHead className="hidden xl:table-cell text-right">
              Giá
            </TableHead>
            <TableHead>Trạng thái</TableHead>
            <TableHead className="text-right">
              <span className="sr-only">Thao tác</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {orders.map((order) => (
            <TableRow key={order.id}>
              <TableCell>
                <p className="font-medium">{order.serviceName}</p>
                <p className="text-xs text-muted-foreground">
                  {[
                    KIND_LABEL[order.kind],
                    order.round ? `Đợt ${order.round}` : null,
                    order.code,
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
              </TableCell>
              <TableCell className="hidden max-w-64 lg:table-cell">
                <p className="truncate text-sm">
                  {order.clinicalReason || "—"}
                </p>
                {order.bodyArea ? (
                  <p className="truncate text-xs text-muted-foreground">
                    {order.bodyArea}
                  </p>
                ) : null}
              </TableCell>
              <TableCell className="hidden text-right tabular-nums xl:table-cell">
                {order.price === null ? "—" : formatCurrencyVnd(order.price)}
              </TableCell>
              <TableCell>
                <div className="space-y-1">
                  <OrderStatusBadge order={order} />
                  {order.statusNote ? (
                    <p className="text-xs text-muted-foreground">
                      {order.statusNote}
                    </p>
                  ) : null}
                </div>
              </TableCell>
              <TableCell className="text-right">
                <div className="flex justify-end gap-2">
                  {order.kind === "IMAGING" && order.imaging?.studyRef ? (
                    <Button size="sm" onClick={() => onOpenFindings(order)}>
                      <ScanSearch aria-hidden />
                      {order.imaging.findingsCompleted
                        ? "Xem kết luận"
                        : "Xem ảnh, kết luận"}
                    </Button>
                  ) : null}
                  {!readOnly && canCancel(order) ? (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => onCancel(order)}
                    >
                      <Ban aria-hidden />
                      Hủy
                    </Button>
                  ) : null}
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
