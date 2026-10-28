import { StatusBadge } from "@/components/admin/StatusBadge";
import {
  SERVICE_ORDER_STATUS_LABELS,
  SERVICE_ORDER_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import type { VisitOrderProgress } from "../types/visitProgress";
import { orderPlaceLabel } from "./visitPresentation";

// Chỉ định của lượt khám: tên, loại và phòng (nếu đã có), trạng thái bằng chữ. Chỉ định đã hủy ghi
// lý do. Không hiện kết quả ở đây: kết quả đi qua khu Kết quả khám (BR-35).

export function OrderProgressList({
  orders,
}: {
  orders: VisitOrderProgress[];
}) {
  return (
    <ul className="divide-y rounded-lg border bg-card">
      {orders.map((order) => (
        <li
          className="flex flex-col gap-2 px-4 py-4 sm:flex-row sm:items-start sm:justify-between"
          key={order.id}
        >
          <div className="min-w-0 space-y-1 text-base">
            <p className="font-medium break-words">{order.serviceName}</p>
            <p className="text-sm break-words text-muted-foreground">
              {orderPlaceLabel(order)}
            </p>
            {order.status === "CANCELLED" && order.cancellationReason ? (
              <p className="text-sm break-words">
                Lý do hủy: {order.cancellationReason}
              </p>
            ) : null}
          </div>
          <StatusBadge
            className="self-start"
            tone={SERVICE_ORDER_STATUS_TONE[order.status]}
          >
            {SERVICE_ORDER_STATUS_LABELS[order.status]}
          </StatusBadge>
        </li>
      ))}
    </ul>
  );
}
