"use client";

import { Eye, UserRoundCog } from "lucide-react";
import { RowActionsMenu, type RowAction } from "@/components/admin/RowActionsMenu";
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
import { formatDateTime } from "@/lib/format/formatDate";
import type { Appointment } from "../types/appointment";
import { AppointmentStatusBadge } from "./AppointmentStatusBadge";

type AppointmentTableProps = {
  items: Appointment[];
  loading: boolean;
  error: string | null;
  emptyText: string;
  onRetry: () => void;
  onView: (appointment: Appointment) => void;
  onReassign: (appointment: Appointment) => void;
};

export function AppointmentTable({
  items,
  loading,
  error,
  emptyText,
  onRetry,
  onView,
  onReassign,
}: AppointmentTableProps) {
  // Nút theo allowedActions của BE; "Xem" luôn có.
  function actionsFor(appointment: Appointment): RowAction[] {
    const actions: RowAction[] = [
      { key: "view", label: "Xem", icon: Eye, onSelect: () => onView(appointment) },
    ];
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
            <TableHead>Ngày giờ</TableHead>
            <TableHead>Bệnh nhân</TableHead>
            <TableHead>Dịch vụ</TableHead>
            <TableHead>Bác sĩ</TableHead>
            <TableHead>Trạng thái</TableHead>
            <TableHead className="w-12">
              <span className="sr-only">Thao tác</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableStateRows
            colSpan={6}
            loading={loading}
            error={error}
            empty={items.length === 0}
            onRetry={onRetry}
            emptyText={emptyText}
          />
          {!loading && !error
            ? items.map((appointment) => (
                <TableRow key={appointment.id}>
                  <TableCell className="whitespace-nowrap tabular-nums">
                    {formatDateTime(appointment.startAt)}
                  </TableCell>
                  <TableCell>
                    <div>{appointment.patientName}</div>
                    {appointment.bookedForDependent ? (
                      <div className="text-xs text-muted-foreground">
                        Đặt cho người phụ thuộc
                      </div>
                    ) : null}
                  </TableCell>
                  <TableCell>{appointment.serviceName}</TableCell>
                  <TableCell>
                    {appointment.doctor?.fullName ?? (
                      <span className="text-muted-foreground">Chưa chỉ định</span>
                    )}
                  </TableCell>
                  <TableCell>
                    <AppointmentStatusBadge status={appointment.status} />
                  </TableCell>
                  <TableCell className="text-right">
                    <RowActionsMenu
                      label={`${appointment.patientName} lúc ${formatDateTime(appointment.startAt)}`}
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
