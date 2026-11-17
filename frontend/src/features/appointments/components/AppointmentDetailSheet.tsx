"use client";

import { useCallback, useState } from "react";
import { Button } from "@/components/ui/button";
import { DetailList } from "@/components/admin/DetailList";
import { StatusBadge } from "@/components/admin/StatusBadge";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { formatDateTime } from "@/lib/format/formatDate";
import type { Appointment } from "../types/appointment";
import { cancelAdminAppointment, getAdminRescheduleSlots, rescheduleAdminAppointment } from "../api/appointmentAdminLiveApi";
import { AppointmentStatusBadge } from "./AppointmentStatusBadge";
import { CancelAppointmentWithReasonDialog } from "./CancelAppointmentWithReasonDialog";
import { RescheduleAppointmentDialog } from "./RescheduleAppointmentDialog";

type AppointmentDetailSheetProps = {
  appointment: Appointment | null;
  onClose: () => void;
  onChanged: () => void;
};

/** Chi tiết và thao tác với lịch hẹn. */
export function AppointmentDetailSheet({
  appointment,
  onClose,
  onChanged,
}: AppointmentDetailSheetProps) {
  const [action, setAction] = useState<"cancel" | "reschedule" | null>(null);
  const loadSlots = useCallback((date: string, signal: AbortSignal) =>
    getAdminRescheduleSlots(appointment!.id, date, signal), [appointment]);
  return (
    <>
    <Sheet
      open={appointment !== null}
      onOpenChange={(open) => { if (!open) { setAction(null); onClose(); } }}
    >
      <SheetContent className="data-[side=right]:w-full data-[side=right]:sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Chi tiết lịch hẹn</SheetTitle>
          <SheetDescription>Thông tin lịch hẹn.</SheetDescription>
        </SheetHeader>
        {appointment ? (
          <div className="px-4 pb-4">
            <DetailList
              items={[
                {
                  label: "Ngày giờ",
                  value: (
                    <span className="tabular-nums">
                      {formatDateTime(appointment.startAt)}
                    </span>
                  ),
                },
                {
                  label: "Bệnh nhân",
                  value: (
                    <>
                      <div>{appointment.patientName}</div>
                      {appointment.bookedForDependent ? (
                        <div className="text-xs text-muted-foreground">
                          Đặt cho người phụ thuộc
                        </div>
                      ) : null}
                    </>
                  ),
                },
                { label: "Dịch vụ", value: appointment.serviceName },
                {
                  label: "Bác sĩ",
                  value: appointment.doctor?.fullName ?? "Chưa chỉ định",
                },
                {
                  label: "Trạng thái",
                  value: (
                    <AppointmentStatusBadge
                      status={appointment.status}
                      requiresReception={appointment.requiresReception}
                    />
                  ),
                },
                {
                  label: "Phí khám",
                  value: (
                    <StatusBadge
                      tone={
                        appointment.examFeePaid === null
                          ? "neutral"
                          : appointment.examFeePaid
                            ? "success"
                            : "warning"
                      }
                    >
                      {appointment.examFeePaid === null
                        ? "Không có phí"
                        : appointment.examFeePaid
                          ? "Đã thanh toán"
                          : "Còn dư nợ"}
                    </StatusBadge>
                  ),
                },
              ]}
            />
            {appointment.status === "BOOKED" && <div className="mt-4 flex gap-2"><Button onClick={() => setAction("reschedule")}>Đổi giờ</Button><Button variant="outline" onClick={() => setAction("cancel")}>Hủy lịch</Button></div>}
          </div>
        ) : null}
      </SheetContent>
    </Sheet>
    {appointment && <>
      <CancelAppointmentWithReasonDialog open={action === "cancel"} onClose={() => setAction(null)} onConfirm={async (reason) => { await cancelAdminAppointment(appointment.id, reason); onChanged(); onClose(); }} />
      <RescheduleAppointmentDialog currentDoctorId={appointment.doctor?.id} open={action === "reschedule"} onClose={() => setAction(null)} loadSlots={loadSlots} onConfirm={async (slot, reason) => { await rescheduleAdminAppointment(appointment.id, slot.doctorId, slot.startTime, reason); onChanged(); onClose(); }} />
    </>}
    </>
  );
}
