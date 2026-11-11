"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { isApiError } from "@/lib/api/apiError";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { cancelMyAppointment } from "../../api/patientAppointmentSource";
import type { PatientAppointment } from "../../types/patientAppointment";
import {
  appointmentSentenceTime,
  refundNotice,
} from "./patientAppointmentPresentation";

// Hộp thoại hủy (UC24, BR-22, BR-53): nói rõ giờ được nhả và phí đã trả không hoàn lại. Nút cao
// h-12 cho người dùng lớn tuổi. Lỗi 5xx giữ hộp thoại để thử lại; 409/403/404 đóng và xử lý.
export function PatientCancelDialog({
  appointment,
  open,
  onOpenChange,
  onCancelled,
  onReload,
}: {
  appointment: PatientAppointment;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Hủy xong: nhận bản mới của lịch để màn cập nhật ngay. */
  onCancelled: (next: PatientAppointment) => void;
  /** Lịch không còn hủy được (409): tải lại chi tiết. */
  onReload: () => void;
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const paid = appointment.examFee.status === "PAID";

  async function handleConfirm() {
    setPending(true);
    try {
      const next = await cancelMyAppointment(appointment.id);
      notifySuccess("MSG-28");
      onCancelled(next);
      onOpenChange(false);
    } catch (error) {
      // MSG-32 (đã check-in, hoặc còn dưới 120 phút trước giờ hẹn): chữ từ bảng MSG chung, rồi tải lại.
      if (isApiError(error) && error.code === "MSG-32") {
        notifyError(error);
        onOpenChange(false);
        onReload();
      } else if (isApiError(error) && error.code === "MSG-29") {
        notifyError(error);
        onOpenChange(false);
        router.replace(PATIENT_ROUTES.appointments);
      } else {
        // 5xx hoặc mất mạng: báo lỗi và giữ hộp thoại để người dùng thử lại hoặc giữ lịch.
        notifyError(error);
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <AlertDialog
      onOpenChange={(next) => {
        if (!pending) onOpenChange(next);
      }}
      open={open}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle className="text-xl">Hủy lịch hẹn?</AlertDialogTitle>
          <AlertDialogDescription className="text-base">
            <span className="block">
              Lịch khám {appointmentSentenceTime(appointment.startAt)} của{" "}
              {appointment.patient.fullName} sẽ bị hủy và giờ này được mở cho
              người khác.
            </span>
            {paid ? (
              <span className="mt-2 block">
                {refundNotice(appointment.examFee)}
              </span>
            ) : null}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter className="flex-col gap-3 sm:flex-row">
          <AlertDialogCancel
            className={cn(publicButtonClass("secondary"), "w-full sm:w-auto")}
            disabled={pending}
          >
            Giữ lịch hẹn
          </AlertDialogCancel>
          <button
            className={cn(
              publicButtonClass("secondary"),
              "w-full border-destructive text-destructive hover:text-destructive sm:w-auto",
            )}
            disabled={pending}
            onClick={() => void handleConfirm()}
            type="button"
          >
            {pending ? "Đang hủy..." : "Hủy lịch hẹn"}
          </button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
