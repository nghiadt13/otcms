"use client";

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
import { AppointmentStatusBadge } from "./AppointmentStatusBadge";

type AppointmentDetailSheetProps = {
  appointment: Appointment | null;
  onClose: () => void;
};

/** Chi tiết chỉ đọc. Không có nội dung y khoa và không có nút hành động. */
export function AppointmentDetailSheet({
  appointment,
  onClose,
}: AppointmentDetailSheetProps) {
  return (
    <Sheet
      open={appointment !== null}
      onOpenChange={(open) => !open && onClose()}
    >
      <SheetContent className="data-[side=right]:w-full data-[side=right]:sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Chi tiết lịch hẹn</SheetTitle>
          <SheetDescription>Thông tin chỉ để xem.</SheetDescription>
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
                  value: <AppointmentStatusBadge status={appointment.status} />,
                },
                {
                  label: "Phí khám",
                  value: (
                    <StatusBadge
                      tone={appointment.examFeePaid ? "success" : "warning"}
                    >
                      {appointment.examFeePaid
                        ? "Đã thanh toán"
                        : "Chưa thanh toán"}
                    </StatusBadge>
                  ),
                },
              ]}
            />
          </div>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}
