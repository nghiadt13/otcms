import { StatusBadge } from "@/components/admin/StatusBadge";
import {
  CLINICAL_ORDER_PAYMENT_LABELS,
  CLINICAL_ORDER_PAYMENT_TONE,
  CLINICAL_ORDER_STATUS_LABELS,
  CLINICAL_ORDER_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import type { VisitProgressOrder } from "../types/visitProgress";
import { orderRoomText } from "./visitPresentation";

// Chỉ định của lượt khám: tên, mã, phòng (có hay chưa), trạng thái và thanh toán bằng chữ. Nằm trong thẻ
// của màn nên chỉ có đường kẻ giữa các dòng, không thêm viền. Không hiện kết quả ở đây: kết quả đi qua
// khu Kết quả khám (BR-35).

export function OrderProgressList({
  orders,
}: {
  orders: VisitProgressOrder[];
}) {
  return (
    <ul className="divide-y border-y">
      {orders.map((order) => (
        <li
          className="flex flex-col gap-3 py-4 sm:flex-row sm:items-start sm:justify-between"
          key={order.orderId}
        >
          <div className="min-w-0 space-y-1 text-base">
            <p className="font-medium break-words">{order.serviceName}</p>
            <p className="text-sm break-words text-muted-foreground">
              {orderRoomText(order)}
              <span aria-hidden> · </span>
              <span className="tabular-nums">Mã {order.orderCode}</span>
            </p>
          </div>
          <div className="flex flex-wrap gap-2 self-start">
            <StatusBadge
              className="h-7 px-3 text-sm"
              tone={CLINICAL_ORDER_STATUS_TONE[order.status]}
            >
              {CLINICAL_ORDER_STATUS_LABELS[order.status]}
            </StatusBadge>
            <StatusBadge
              className="h-7 px-3 text-sm"
              tone={CLINICAL_ORDER_PAYMENT_TONE[order.paymentStatus]}
            >
              {CLINICAL_ORDER_PAYMENT_LABELS[order.paymentStatus]}
            </StatusBadge>
          </div>
        </li>
      ))}
    </ul>
  );
}
