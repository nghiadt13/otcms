import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import type { VisitProgress } from "../types/visitProgress";
import { OrderProgressList } from "./OrderProgressList";
import { VisitQueueStatus } from "./VisitQueueStatus";

// Nội dung tiến độ: vị trí trong hàng đang chờ, các chỉ định của lượt (phòng, trạng thái, thanh toán)
// và lối đi tiếp. Không hiện kết quả khám ở đây (BR-35). Nút chỉ là gợi ý giao diện suy từ chỉ định;
// trang đích tự kiểm tra quyền và luật ở BE.

const LINK_CLASS =
  "inline-flex min-h-12 items-center text-base font-medium text-primary underline-offset-4 hover:underline";

export function VisitProgressView({ progress }: { progress: VisitProgress }) {
  const { serviceOrders: orders } = progress;
  const inQueue = progress.queueNumber !== null;
  const canPay = orders.some(
    (order) =>
      order.status !== "CANCELLED" && order.paymentStatus === "PENDING_PAYMENT",
  );
  const canViewResults = orders.some(
    (order) => order.status === "RESULTS_AVAILABLE",
  );

  return (
    <div className="space-y-8">
      {inQueue ? (
        <section aria-labelledby="visit-queue" className="space-y-3">
          <h2 className="text-lg font-semibold" id="visit-queue">
            Bạn đang chờ
          </h2>
          <VisitQueueStatus progress={progress} />
        </section>
      ) : null}

      {orders.length > 0 ? (
        <section aria-labelledby="visit-orders" className="space-y-3">
          <h2 className="text-lg font-semibold" id="visit-orders">
            Chỉ định
          </h2>
          <OrderProgressList orders={orders} />
        </section>
      ) : null}

      {!inQueue && orders.length === 0 ? (
        <p className="text-base text-muted-foreground">
          Hiện bạn không chờ ở phòng nào.
        </p>
      ) : null}

      <div className="space-y-4">
        {canPay ? (
          <Link
            className={cn(publicButtonClass("primary"), "w-full sm:w-auto")}
            href={PATIENT_ROUTES.payVisit(progress.visitId)}
          >
            Thanh toán các chỉ định chưa thanh toán
          </Link>
        ) : null}
        {canViewResults ? (
          <Link
            className={LINK_CLASS}
            href={PATIENT_ROUTES.resultDetail(progress.visitId)}
          >
            Xem kết quả
          </Link>
        ) : (
          <p className="text-base text-muted-foreground">
            Kết quả khám sẽ hiện ở mục Kết quả khám khi bác sĩ kết luận.
          </p>
        )}
      </div>
    </div>
  );
}
