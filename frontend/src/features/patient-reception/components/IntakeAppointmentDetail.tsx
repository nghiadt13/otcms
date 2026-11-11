"use client";

import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import type { ReceptionAppointment } from "../api/receptionLiveApi";
import { receptionStatusValue } from "../schema/intakeAppointmentStatus";
import { FormModal, Status, timeText } from "./ReceptionUi";

/** Chi tiết lịch hẹn trên màn tiếp nhận, kèm nút mở bước tiếp nhận khi lịch còn có thể check-in. */
export function IntakeAppointmentDetail({
  appointment,
  onClose,
  onCheckIn,
}: {
  appointment: ReceptionAppointment;
  onClose: () => void;
  onCheckIn: (appointment: ReceptionAppointment) => void;
}) {
  return (
    <FormModal
      title="Chi tiết lịch hẹn"
      description="Thông tin lịch hẹn và hồ sơ bệnh nhân từ hệ thống."
      onClose={onClose}
      wide
    >
      <dl className="divide-y rounded-md border px-4">
        <DetailRow label="Mã lịch hẹn">{appointment.id}</DetailRow>
        <DetailRow label="Bệnh nhân">
          {appointment.patientName ?? "Chưa có tên bệnh nhân"}
        </DetailRow>
        <DetailRow label="Mã hồ sơ">{appointment.patientProfileId}</DetailRow>
        <DetailRow label="Ngày / giờ">
          {timeText(appointment.startTime)}
        </DetailRow>
        <DetailRow label="Dịch vụ">
          {appointment.serviceName ?? "Chưa có thông tin dịch vụ"}
        </DetailRow>
        <DetailRow label="Bác sĩ">
          {appointment.doctorDisplayName ?? "Chưa phân bác sĩ"}
        </DetailRow>
        <DetailRow label="Phí khám">
          {appointment.examFeePaid === true
            ? "Đã thanh toán"
            : appointment.examFeePaid === false
              ? "Chưa thanh toán"
              : "Chưa rõ"}
        </DetailRow>
        <DetailRow label="Trạng thái">
          <Status value={receptionStatusValue(appointment.status)} />
        </DetailRow>
        {appointment.purpose && (
          <DetailRow label="Lý do khám">{appointment.purpose}</DetailRow>
        )}
      </dl>
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onClose}>
          Đóng
        </Button>
        {["CONFIRMED", "NO_SHOW"].includes(appointment.status) && (
          <Button
            onClick={() => {
              onCheckIn(appointment);
            }}
          >
            Hỗ trợ tiếp nhận
          </Button>
        )}
      </div>
    </FormModal>
  );
}

function DetailRow({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="grid gap-1 py-3 sm:grid-cols-[10rem_1fr] sm:gap-4">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="min-w-0 break-words text-sm font-medium">{children}</dd>
    </div>
  );
}
