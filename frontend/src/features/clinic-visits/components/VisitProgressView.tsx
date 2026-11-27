import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import type { VisitProgress, VisitSummary } from "../types/visitProgress";
import { OrderProgressList } from "./OrderProgressList";
import { VisitQueueStatus } from "./VisitQueueStatus";
import { VisitStepTracker } from "./VisitStepTracker";
import { VisitSummaryInfo } from "./VisitSummaryInfo";
import { visitStepIndex } from "./visitPresentation";

// Nội dung tiến độ: lượt của ai, các chặng, vị trí trong hàng đang chờ, các chỉ định của lượt (phòng,
// trạng thái, thanh toán) và lối đi tiếp. Không hiện kết quả khám ở đây (BR-35). Nút chỉ là gợi ý giao
// diện suy từ chỉ định; trang đích tự kiểm tra quyền và luật ở BE. `summary` là thông tin thêm từ danh
// sách lượt (tên, ngày, trạng thái); thiếu thì bỏ phần đó.

const LINK_CLASS =
  "inline-flex min-h-12 items-center text-base font-medium text-primary underline-offset-4 hover:underline";

export function VisitProgressView({
  progress,
  summary = null,
}: {
  progress: VisitProgress;
  summary?: VisitSummary | null;
}) {
  const { serviceOrders: orders } = progress;
  const inQueue = progress.queueNumber !== null;
  const stepIndex = summary ? visitStepIndex(summary.status) : null;
  const unpaidCount = orders.filter(
    (order) =>
      order.status !== "CANCELLED" && order.paymentStatus === "PENDING_PAYMENT",
  ).length;
  const canPay = unpaidCount > 0;
  const canViewResults = orders.some(
    (order) => order.status === "RESULTS_AVAILABLE",
  );

  return (
    <div className="space-y-8">
      <VisitSummaryInfo progress={progress} summary={summary} />

      {stepIndex !== null ? <VisitStepTracker current={stepIndex} /> : null}

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
            <span className="ml-2 text-base font-normal text-muted-foreground tabular-nums">
              {orders.length} dịch vụ
              {canPay ? `, ${unpaidCount} chưa thanh toán` : ""}
            </span>
          </h2>
          <OrderProgressList orders={orders} />
        </section>
      ) : null}

      {!inQueue && orders.length === 0 ? (
        <p className="text-base text-muted-foreground">
          Hiện bạn không chờ ở phòng nào.
        </p>
      ) : null}

      <div className="flex flex-col gap-x-6 gap-y-3 sm:flex-row sm:flex-wrap sm:items-center">
        {canPay ? (
          <Link
            className={cn(publicButtonClass("primary"), "w-full sm:w-auto")}
            href={PATIENT_ROUTES.payments}
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
