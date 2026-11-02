import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import type { PatientAppointment } from "../../types/patientAppointment";
import { visitHrefOf } from "./patientAppointmentPresentation";

// Nút của chi tiết lịch. Mỗi nút chỉ hiện khi `allowedActions` của BE có việc tương ứng (hoặc
// lượt khám đã có). Không có nút đổi giờ (BR-22): muốn đổi thì hủy rồi đặt lịch mới.

export function PatientAppointmentActions({
  appointment,
  onRequestCancel,
}: {
  appointment: PatientAppointment;
  onRequestCancel: () => void;
}) {
  const canPay = appointment.allowedActions.includes("payExamFee");
  const canCancel = appointment.allowedActions.includes("cancel");
  const visitHref = visitHrefOf(appointment.visitId);
  if (!canPay && !canCancel && !visitHref) return null;

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
