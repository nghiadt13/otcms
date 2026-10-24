"use client";

import { DetailList } from "@/components/admin/DetailList";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  EXAM_FEE_STATUS_LABELS,
  EXAM_FEE_STATUS_TONE,
  VISIT_TYPE_LABELS,
} from "@/lib/domain/clinicVisit";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDate, formatDateTime } from "@/lib/format/formatDate";
import type { StaffAppointment } from "../../types/staffAppointment";
import { AppointmentStatusBadge } from "../AppointmentStatusBadge";

type StaffAppointmentDetailSheetProps = {
  appointment: StaffAppointment | null;
  onClose: () => void;
  onCheckIn: (appointment: StaffAppointment) => void;
  onReassign: (appointment: StaffAppointment) => void;
};

/**
 * Chi tiết một lịch cho Lễ tân. Footer chỉ vẽ nút có trong `allowedActions`. Không có nút hủy
 * hay đổi giờ (BR-22).
 */
export function StaffAppointmentDetailSheet({
  appointment,
  onClose,
  onCheckIn,
  onReassign,
}: StaffAppointmentDetailSheetProps) {
  return (
    <Sheet open={appointment !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="data-[side=right]:w-full data-[side=right]:sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Chi tiết lịch hẹn</SheetTitle>
          <SheetDescription>
            Kiểm tra thông tin trước khi tiếp nhận hoặc chuyển bác sĩ.
          </SheetDescription>
        </SheetHeader>
        {appointment ? (
          <>
            <div className="grid gap-6 overflow-y-auto px-4 pb-4">
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
                        <div>{appointment.patient.fullName}</div>
                        <div className="text-xs text-muted-foreground tabular-nums">
                          Ngày sinh {formatDate(appointment.patient.dateOfBirth)} ·{" "}
                          {appointment.patient.patientCode}
                        </div>
                      </>
                    ),
                  },
                  {
                    label: "Đặt cho",
                    value:
                      appointment.bookedFor.kind === "DEPENDENT"
                        ? `Người phụ thuộc của ${appointment.bookedFor.accountHolderName}`
                        : "Chính chủ",
                  },
                  { label: "Dịch vụ", value: appointment.service.name },
                  {
                    label: "Loại",
                    value: VISIT_TYPE_LABELS[
                      appointment.isFollowUp ? "FOLLOW_UP" : "FIRST_VISIT"
                    ],
                  },
                  { label: "Bác sĩ", value: appointment.doctor.fullName },
                  { label: "Phòng", value: appointment.roomName },
                  {
                    label: "Phí khám",
                    value: (
                      <div className="grid gap-1.5">
                        <span className="tabular-nums">
                          {formatCurrencyVnd(appointment.examFee.amount)}
                        </span>
                        <span>
                          <StatusBadge tone={EXAM_FEE_STATUS_TONE[appointment.examFee.status]}>
                            {EXAM_FEE_STATUS_LABELS[appointment.examFee.status]}
                          </StatusBadge>
                        </span>
                      </div>
                    ),
                  },
                  {
                    label: "Trạng thái",
                    value: <AppointmentStatusBadge status={appointment.status} />,
                  },
                ]}
              />
            </div>
            {appointment.allowedActions.includes("checkIn") ||
            appointment.allowedActions.includes("reassign") ? (
              <SheetFooter>
                {appointment.allowedActions.includes("checkIn") ? (
                  <Button onClick={() => onCheckIn(appointment)}>Tiếp nhận</Button>
                ) : null}
                {appointment.allowedActions.includes("reassign") ? (
                  <Button variant="outline" onClick={() => onReassign(appointment)}>
                    Chuyển bác sĩ
                  </Button>
                ) : null}
              </SheetFooter>
            ) : null}
          </>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}
