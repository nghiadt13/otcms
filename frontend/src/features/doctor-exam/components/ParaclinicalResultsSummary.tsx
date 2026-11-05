"use client";

import Link from "next/link";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Skeleton } from "@/components/ui/skeleton";
import { doctorVisitHref } from "@/lib/routes/doctorRoutes";
import { useLabResults, useOrders } from "../hooks/useDoctorData";
import { OrderStatusBadge } from "./doctorStatus";

// Kết quả cận lâm sàng đã có, hiện cạnh form khám để bác sĩ đối chiếu khi chẩn đoán (R3 UC37
// bước 2): chỉ định kèm trạng thái, kết luận hình ảnh và giá trị xét nghiệm bất thường.

export function ParaclinicalResultsSummary({ visitId }: { visitId: string }) {
  const orders = useOrders(visitId);
  const labs = useLabResults(visitId);
  const abnormal = (labs.data ?? []).flatMap((lab) =>
    lab.lines.filter((line) => line.abnormal).map((line) => ({ lab, line })),
  );

  return (
    <section
      aria-labelledby="results-title"
      className="rounded-lg border bg-card p-5"
    >
      <div className="flex items-center justify-between gap-3">
        <h2 id="results-title" className="text-lg font-semibold">
          Kết quả cận lâm sàng
        </h2>
        <Link
          href={doctorVisitHref(visitId, "paraclinical")}
          className="text-sm text-primary underline-offset-4 hover:underline"
        >
          Mở tab Cận lâm sàng
        </Link>
      </div>
      {orders.data === undefined ? (
        <Skeleton className="mt-4 h-16 w-full" />
      ) : orders.data.length === 0 ? (
        <p className="mt-3 text-sm text-muted-foreground">
          Chưa có chỉ định nào cho lượt khám này.
        </p>
      ) : (
        <ul className="mt-3 divide-y">
          {orders.data.map((order) => (
            <li key={order.id} className="space-y-1 py-2.5">
              <div className="flex items-center justify-between gap-3">
                <span className="min-w-0 truncate text-sm font-medium">
                  {order.serviceName}
                </span>
                <OrderStatusBadge order={order} />
              </div>
              {order.imaging?.findingsText ? (
                <p className="text-sm text-muted-foreground">
                  Kết luận: {order.imaging.findingsText}
                </p>
              ) : order.statusNote ? (
                <p className="text-xs text-muted-foreground">
                  {order.statusNote}
                </p>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {abnormal.length ? (
        <div className="mt-3 border-t pt-3">
          <p className="text-xs font-medium text-muted-foreground">
            Xét nghiệm bất thường
          </p>
          <ul className="mt-1.5 space-y-1">
            {abnormal.map(({ lab, line }) => (
              <li
                key={`${lab.orderId}-${line.indicator}`}
                className="flex items-center justify-between gap-3 text-sm"
              >
                <span>{line.indicator}</span>
                <StatusBadge tone="danger">
                  {line.value} {line.unit} (chuẩn {line.reference})
                </StatusBadge>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  );
}
