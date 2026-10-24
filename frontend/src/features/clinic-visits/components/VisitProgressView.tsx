import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { formatDate } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import type { VisitProgress } from "../types/visitProgress";
import { OrderProgressList } from "./OrderProgressList";
import { QueuePositionList } from "./QueuePositionList";

// Nội dung tiến độ: hạn quay lại (khi chờ kết luận), hàng đợi đang chờ, chỉ định và các nút theo
// `allowedActions` của BE. Không hiện kết quả khám ở đây (BR-35).

const LINK_CLASS =
  "inline-flex min-h-12 items-center text-base font-medium text-primary underline-offset-4 hover:underline";

export function VisitProgressView({ progress }: { progress: VisitProgress }) {
  const { allowedActions, queues, orders, status, returnDeadline } = progress;
  const canPay = allowedActions.includes("payOrders");
  const canViewResults = allowedActions.includes("viewResults");
  const canReview = allowedActions.includes("review");
  const isLeft = status === "CANCELLED_LEFT";

  return (
    <div className="space-y-8">
      {status === "AWAITING_CONCLUSION" && returnDeadline ? (
        // role="status": thông tin nhắc, không phải lỗi; không cần báo gấp.
        <Alert role="status">
          <AlertDescription className="text-base">
            Bạn có thể quay lại trong thời hạn đến hết ngày{" "}
            {formatDate(returnDeadline)}, không mất phí khám lại.
          </AlertDescription>
        </Alert>
      ) : null}

      {isLeft ? (
        <p className="text-base">
          Lượt khám đã đóng vì chưa được khám trong ngày.
        </p>
      ) : null}

      {queues.length > 0 ? (
        <section aria-labelledby="visit-queues" className="space-y-3">
          <h2 className="text-lg font-semibold" id="visit-queues">
            Bạn đang chờ
          </h2>
          <QueuePositionList queues={queues} />
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

      <div className="space-y-4">
        {canPay ? (
          <Link
            className={cn(publicButtonClass("primary"), "w-full sm:w-auto")}
            href={PATIENT_ROUTES.payVisit(progress.id)}
          >
            Thanh toán các chỉ định chưa thanh toán
          </Link>
        ) : null}
        {canViewResults || canReview ? (
          <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
            {canViewResults ? (
              <Link
                className={LINK_CLASS}
                href={PATIENT_ROUTES.resultDetail(progress.id)}
              >
                Xem kết quả
              </Link>
            ) : null}
            {canReview ? (
              <Link
                className={LINK_CLASS}
                href={PATIENT_ROUTES.newReview(progress.id)}
              >
                Đánh giá lượt khám
              </Link>
            ) : null}
          </div>
        ) : null}
        {!canViewResults && !isLeft ? (
          <p className="text-base text-muted-foreground">
            Kết quả khám sẽ hiện ở mục Kết quả khám khi bác sĩ kết luận.
          </p>
        ) : null}
      </div>
    </div>
  );
}
