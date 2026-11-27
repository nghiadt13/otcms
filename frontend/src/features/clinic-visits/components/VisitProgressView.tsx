import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import type { VisitProgress, VisitSummary } from "../types/visitProgress";
import { VisitNextAction } from "./VisitNextAction";
import { VisitSummaryInfo } from "./VisitSummaryInfo";
import { VisitTimeline } from "./VisitTimeline";
import { buildVisitTimeline } from "./visitTimelineModel";

// Nội dung tiến độ gọn một mạch: lượt của ai, việc cần làm bây giờ, rồi MỘT lộ trình (khám bệnh, từng chỉ
// định, bác sĩ kết luận). Mỗi chỉ định tự mang số thứ tự của nó và luôn hiện từ danh sách chỉ định, kể cả
// khi thiếu dữ liệu hàng chờ. Không hiện kết quả khám ở đây (BR-35). Nút chỉ là gợi ý giao diện suy từ chỉ
// định; trang đích tự kiểm tra quyền và luật ở BE. `summary` là thông tin thêm từ danh sách lượt (tên, ngày,
// trạng thái); thiếu thì bỏ phần đó.

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
  const { steps, next } = buildVisitTimeline(progress, summary);
  const canPay = orders.some(
    (order) =>
      order.status !== "CANCELLED" && order.paymentStatus === "PENDING_PAYMENT",
  );
  const canViewResults = orders.some(
    (order) => order.status === "RESULTS_AVAILABLE",
  );

  return (
    <div className="space-y-8">
      <VisitSummaryInfo progress={progress} summary={summary} />

      {next ? <VisitNextAction action={next} /> : null}

      {steps.length > 0 ? (
        <VisitTimeline steps={steps} />
      ) : (
        <p className="text-base text-muted-foreground">
          Hiện bạn không chờ ở phòng nào.
        </p>
      )}

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
