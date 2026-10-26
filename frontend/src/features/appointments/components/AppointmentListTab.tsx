"use client";

import { useState } from "react";
import { TablePagination } from "@/components/admin/TablePagination";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { todayIsoDate } from "@/lib/format/formatDate";
import {
  listEligibleDoctors,
  notifyNoReplacement,
  reassignAppointmentDoctor,
} from "../api/appointmentApi";
import { useAppointmentList } from "../hooks/useAppointmentList";
import type { useAppointmentUrlState } from "../hooks/useAppointmentUrlState";
import type { useDoctorLookup } from "../hooks/useDoctorLookup";
import { useReassignDoctorFlow } from "../hooks/useReassignDoctorFlow";
import {
  rangeErrorCode,
  resolveListRange,
} from "../schema/appointmentUrlRules";
import type { Appointment } from "../types/appointment";
import type { ReassignDoctorApi } from "../types/reassignDoctorApi";
import { AppointmentDetailSheet } from "./AppointmentDetailSheet";
import { AppointmentFilters } from "./AppointmentFilters";
import { AppointmentTable } from "./AppointmentTable";
import { NotifyNoReplacementDialog } from "./NotifyNoReplacementDialog";
import { ReassignDoctorDialog } from "./ReassignDoctorDialog";

type AppointmentListTabProps = {
  url: ReturnType<typeof useAppointmentUrlState>;
  lookup: ReturnType<typeof useDoctorLookup>;
};

const EMPTY_TEXT =
  "Chưa có lịch hẹn trong khoảng ngày đã chọn. Thử mở rộng khoảng ngày hoặc bỏ lọc trạng thái.";
const INVALID_RANGE_TEXT = "Sửa khoảng ngày để xem lịch hẹn.";

/** Bộ endpoint chuyển bác sĩ của admin (/api/v1/admin/appointments/...). */
const ADMIN_REASSIGN_API: ReassignDoctorApi<Appointment> = {
  listEligibleDoctors,
  reassign: reassignAppointmentDoctor,
  notifyNoReplacement,
};

/** Tab 1: lọc, bảng, Sheet chi tiết, chuyển bác sĩ và báo bệnh nhân (AF1). */
export function AppointmentListTab({ url, lookup }: AppointmentListTabProps) {
  const { state, update } = url;
  const range = resolveListRange(state, todayIsoDate());
  const rangeError = rangeErrorCode(range.from, range.to);
  const [q, setQ] = useState("");
  const debouncedQ = useDebouncedValue(q);
  const list = useAppointmentList(
    { ...range, doctorId: state.doctorId, status: state.status },
    debouncedQ,
  );
  const flow = useReassignDoctorFlow({
    api: ADMIN_REASSIGN_API,
    replaceItem: list.replaceItem,
    reload: list.reload,
    doctorFilter: state.doctorId,
  });
  const [viewing, setViewing] = useState<Appointment | null>(null);

  return (
    <div>
      <AppointmentFilters
        from={range.from}
        to={range.to}
        doctorId={state.doctorId}
        status={state.status}
        q={q}
        rangeError={rangeError}
        doctors={lookup.doctors}
        doctorsLoading={lookup.loading}
        doctorsError={lookup.error}
        onDoctorsRetry={lookup.reload}
        onRangeChange={({ from, to }) =>
          update({ from: from || undefined, to: to || undefined })
        }
        onDoctorChange={(doctorId) => update({ doctorId })}
        onStatusChange={(status) => update({ status })}
        onQueryChange={setQ}
      />
      <AppointmentTable
        items={list.data?.items ?? []}
        loading={list.loading}
        error={list.error}
        emptyText={rangeError ? INVALID_RANGE_TEXT : EMPTY_TEXT}
        onRetry={list.reload}
        onView={setViewing}
        onReassign={flow.setReassigning}
      />
      {list.data && !list.error ? (
        <TablePagination
          page={list.page}
          totalPages={list.data.totalPages}
          totalItems={list.data.totalItems}
          onPageChange={list.setPage}
        />
      ) : null}
      <AppointmentDetailSheet
        appointment={viewing}
        onClose={() => setViewing(null)}
      />
      <ReassignDoctorDialog<Appointment>
        appointment={flow.reassigning}
        api={ADMIN_REASSIGN_API}
        patientNameOf={(appointment) => appointment.patientName}
        onClose={() => flow.setReassigning(null)}
        onChanged={flow.handleChanged}
        onStale={flow.handleStale}
        onNotifyRequest={flow.requestNotify}
      />
      <NotifyNoReplacementDialog
        open={flow.notifying !== null}
        onOpenChange={(open) => !open && flow.setNotifying(null)}
        onConfirm={flow.confirmNotify}
      />
    </div>
  );
}
