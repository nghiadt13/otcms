"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type ReactNode, useState } from "react";
import { TablePagination } from "@/components/admin/TablePagination";
import { PageHeader } from "@/components/PageHeader";
import { Button, buttonVariants } from "@/components/ui/button";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { formatDate, todayIsoDate } from "@/lib/format/formatDate";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";
import {
  listStaffEligibleDoctors,
  notifyStaffNoReplacement,
  reassignStaffAppointmentDoctor,
} from "../../api/staffAppointmentApi";
import { useReassignDoctorFlow } from "../../hooks/useReassignDoctorFlow";
import { useStaffAppointmentList } from "../../hooks/useStaffAppointmentList";
import { useStaffAppointmentUrlState } from "../../hooks/useStaffAppointmentUrlState";
import { useStaffDoctorLookup } from "../../hooks/useStaffDoctorLookup";
import { STAFF_NO_CANDIDATE_LINES } from "../../schema/staffAppointmentText";
import { checkInHref } from "../../schema/staffAppointmentUrlRules";
import type { ReassignDoctorApi } from "../../types/reassignDoctorApi";
import type { StaffAppointment } from "../../types/staffAppointment";
import { NotifyNoReplacementDialog } from "../NotifyNoReplacementDialog";
import { ReassignDoctorDialog } from "../ReassignDoctorDialog";
import { StaffAppointmentDetailSheet } from "./StaffAppointmentDetailSheet";
import { StaffAppointmentFilters } from "./StaffAppointmentFilters";
import { StaffAppointmentTable } from "./StaffAppointmentTable";

/** Bộ endpoint chuyển bác sĩ của khu Lễ tân (/api/v1/appointments/...). */
const STAFF_REASSIGN_API: ReassignDoctorApi<StaffAppointment> = {
  listEligibleDoctors: listStaffEligibleDoctors,
  reassign: reassignStaffAppointmentDoctor,
  notifyNoReplacement: notifyStaffNoReplacement,
};

/** Trống không có bộ lọc: dạy cách dùng. Có bộ lọc: nói rõ và cho xóa bộ lọc. */
function emptyText(date: string, filtered: boolean, onClear: () => void): ReactNode {
  if (filtered)
    return (
      <span>
        Không có lịch hẹn khớp bộ lọc. Xóa bộ lọc để xem tất cả.{" "}
        <Button variant="link" onClick={onClear}>
          Xóa bộ lọc
        </Button>
      </span>
    );
  return `Ngày ${formatDate(date)} chưa có lịch hẹn nào. Bấm “Đặt lịch” để đặt cho bệnh nhân.`;
}

/**
 * Lịch hẹn của Lễ tân theo ngày (mặc định hôm nay), sắp theo giờ. Lễ tân không hủy và không đổi
 * giờ; việc duy nhất trên lịch có sẵn là chuyển bác sĩ cùng giờ, hoặc báo bệnh nhân khi không có ai.
 */
export function StaffAppointmentList() {
  const router = useRouter();
  const url = useStaffAppointmentUrlState();
  const { date, doctorId, status } = url.state;
  const [q, setQ] = useState("");
  const debouncedQ = useDebouncedValue(q);
  const list = useStaffAppointmentList({ date, doctorId, status }, debouncedQ);
  const lookup = useStaffDoctorLookup();
  const flow = useReassignDoctorFlow({
    api: STAFF_REASSIGN_API,
    replaceItem: list.replaceItem,
    reload: list.reload,
    doctorFilter: doctorId,
  });
  const [viewing, setViewing] = useState<StaffAppointment | null>(null);
  const filtered = Boolean(doctorId || status || q.trim());

  function clearFilters() {
    url.update({ doctorId: undefined, status: undefined });
    setQ("");
  }

  function openCheckIn(appointment: StaffAppointment) {
    router.push(checkInHref(appointment.id));
  }

  // Từ Sheet: đóng Sheet rồi mở Dialog, không chồng hai hộp thoại.
  function reassignFromSheet(appointment: StaffAppointment) {
    setViewing(null);
    flow.setReassigning(appointment);
  }

  return (
    <div>
      <PageHeader
        title="Lịch hẹn"
        description="Xem lịch hẹn theo ngày. Khi bác sĩ vắng, chuyển lịch sang bác sĩ khác có ca cùng giờ."
        actions={
          <Link
            href={STAFF_ROUTES.newAppointment}
            className={buttonVariants({ variant: "default" })}
          >
            Đặt lịch
          </Link>
        }
      />
      <StaffAppointmentFilters
        date={date}
        today={todayIsoDate()}
        doctorId={doctorId}
        status={status}
        q={q}
        doctors={lookup.doctors}
        doctorsLoading={lookup.loading}
        doctorsError={lookup.error}
        onDoctorsRetry={lookup.reload}
        onDateChange={(next) => url.update({ date: next })}
        onDoctorChange={(next) => url.update({ doctorId: next })}
        onStatusChange={(next) => url.update({ status: next })}
        onQueryChange={setQ}
      />
      <StaffAppointmentTable
        items={list.data?.items ?? []}
        loading={list.loading}
        error={list.error}
        emptyText={emptyText(date, filtered, clearFilters)}
        onRetry={list.reload}
        onView={setViewing}
        onCheckIn={openCheckIn}
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
      <StaffAppointmentDetailSheet
        appointment={viewing}
        onClose={() => setViewing(null)}
        onCheckIn={openCheckIn}
        onReassign={reassignFromSheet}
      />
      <ReassignDoctorDialog<StaffAppointment>
        appointment={flow.reassigning}
        api={STAFF_REASSIGN_API}
        patientNameOf={(appointment) => appointment.patient.fullName}
        noCandidateLines={STAFF_NO_CANDIDATE_LINES}
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
