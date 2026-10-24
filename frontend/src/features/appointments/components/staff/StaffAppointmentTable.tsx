"use client";

import type { ReactNode } from "react";
import { Eye, UserCheck, UserRoundCog } from "lucide-react";
import { RowActionsMenu, type RowAction } from "@/components/admin/RowActionsMenu";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { TableFrame } from "@/components/admin/TableFrame";
import { TableStateRows } from "@/components/admin/TableStateRows";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  EXAM_FEE_STATUS_LABELS,
  EXAM_FEE_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatTime } from "@/lib/format/formatDate";
import { formatPhoneForDisplay } from "../../schema/staffAppointmentText";
import type { StaffAppointment } from "../../types/staffAppointment";
import { AppointmentStatusBadge } from "../AppointmentStatusBadge";

type StaffAppointmentTableProps = {
  items: StaffAppointment[];
  loading: boolean;
  error: string | null;
  emptyText: ReactNode;
  onRetry: () => void;
  onView: (appointment: StaffAppointment) => void;
  onCheckIn: (appointment: StaffAppointment) => void;
  onReassign: (appointment: StaffAppointment) => void;
};

/** Bảng lịch hẹn trong ngày. Menu ⋯ chỉ có các thao tác BE trả trong `allowedActions`. */
export function StaffAppointmentTable({
  items,
  loading,
  error,
  emptyText,
  onRetry,
  onView,
  onCheckIn,
  onReassign,
}: StaffAppointmentTableProps) {
  function actionsFor(appointment: StaffAppointment): RowAction[] {
    const actions: RowAction[] = [
      { key: "view", label: "Xem chi tiết", icon: Eye, onSelect: () => onView(appointment) },
    ];
    if (appointment.allowedActions.includes("checkIn"))
      actions.push({
        key: "checkIn",
        label: "Tiếp nhận",
        icon: UserCheck,
        onSelect: () => onCheckIn(appointment),
      });
    if (appointment.allowedActions.includes("reassign"))
      actions.push({
        key: "reassign",
        label: "Chuyển bác sĩ",
        icon: UserRoundCog,
        onSelect: () => onReassign(appointment),
      });
    return actions;
  }

  return (
    <TableFrame>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Giờ</TableHead>
            <TableHead>Bệnh nhân</TableHead>
            <TableHead>Dịch vụ</TableHead>
            <TableHead>Bác sĩ</TableHead>
            <TableHead>Phí khám</TableHead>
            <TableHead>Trạng thái</TableHead>
            <TableHead className="w-12">
              <span className="sr-only">Thao tác</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableStateRows
            colSpan={7}
            loading={loading}
            error={error}
            empty={items.length === 0}
            onRetry={onRetry}
            emptyText={emptyText}
          />
          {!loading && !error
            ? items.map((appointment) => (
                <TableRow key={appointment.id} className="h-11">
                  <TableCell className="font-medium whitespace-nowrap tabular-nums">
                    {formatTime(appointment.startAt)}
                  </TableCell>
                  <TableCell>
                    <div>{appointment.patient.fullName}</div>
                    <div className="text-xs text-muted-foreground tabular-nums">
                      {formatPhoneForDisplay(appointment.patient.phone)}
                      {appointment.bookedFor.kind === "DEPENDENT"
                        ? " · Đặt cho người phụ thuộc"
                        : null}
                    </div>
                  </TableCell>
                  <TableCell>
                    <div>{appointment.service.name}</div>
                    {appointment.isFollowUp ? (
                      <div className="text-xs text-muted-foreground">Tái khám</div>
                    ) : null}
                  </TableCell>
                  <TableCell>{appointment.doctor.fullName}</TableCell>
                  <TableCell>
                    <div className="tabular-nums">
                      {formatCurrencyVnd(appointment.examFee.amount)}
                    </div>
                    <StatusBadge tone={EXAM_FEE_STATUS_TONE[appointment.examFee.status]}>
                      {EXAM_FEE_STATUS_LABELS[appointment.examFee.status]}
                    </StatusBadge>
                  </TableCell>
                  <TableCell>
                    <AppointmentStatusBadge status={appointment.status} />
                  </TableCell>
                  <TableCell className="text-right">
                    <RowActionsMenu
                      label={`${appointment.patient.fullName} lúc ${formatTime(appointment.startAt)}`}
                      actions={actionsFor(appointment)}
                    />
                  </TableCell>
                </TableRow>
              ))
            : null}
        </TableBody>
      </Table>
    </TableFrame>
  );
}
