import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import type { PatientAppointment } from "../../types/patientAppointment";
import { visitHrefOf } from "./patientAppointmentPresentation";

// Nút của chi tiết lịch. Mỗi nút chỉ hiện khi `allowedActions` của BE có việc tương ứng (hoặc
// lượt khám đã có): lịch chưa trả phí chỉ được hủy (>= 2 giờ trước giờ hẹn) hoặc trả online;
// lịch đã trả phí chỉ được đổi lịch (>= 2 giờ trước giờ hẹn, tối đa 2 lần).

export function PatientAppointmentActions({
  appointment,
  onRequestCancel,
  onRequestReschedule,
}: {
  appointment: PatientAppointment;
  onRequestCancel: () => void;
  onRequestReschedule: () => void;
}) {
  const canPay = appointment.allowedActions.includes("payExamFee");
  const canCancel = appointment.allowedActions.includes("cancel");
  const canReschedule = appointment.allowedActions.includes("reschedule");
  const visitHref = visitHrefOf(appointment.visitId);
  if (!canPay && !canCancel && !canReschedule && !visitHref) return null;

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
        {visitHref ? (
          <Link
            className="inline-flex min-h-12 items-center text-base font-medium text-primary underline-offset-4 hover:underline"
            href={visitHref}
          >
            Xem tiến độ lượt khám
          </Link>
        ) : null}
        {canPay ? (
          <Link
            className={cn(publicButtonClass("secondary"), "w-full sm:w-auto")}
            href={PATIENT_ROUTES.payAppointment(appointment.id)}
          >
            Thanh toán trực tuyến
          </Link>
        ) : null}
        {canReschedule ? (
          <button
            className={cn(publicButtonClass("secondary"), "w-full sm:w-auto")}
            onClick={onRequestReschedule}
            type="button"
          >
            Đổi lịch hẹn
          </button>
        ) : null}
        {canCancel ? (
          <button
            className={cn(
              publicButtonClass("secondary"),
              "w-full text-destructive hover:text-destructive sm:w-auto",
            )}
            onClick={onRequestCancel}
            type="button"
          >
            Hủy lịch hẹn
          </button>
        ) : null}
      </div>
      {canCancel ? (
        <p className="text-base text-muted-foreground">
          Muốn đổi giờ? Hủy lịch này rồi đặt lịch mới.
        </p>
      ) : null}
    </div>
  );
}
