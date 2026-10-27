"use client";

import { useCallback, useState } from "react";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { TablePagination } from "@/components/admin/TablePagination";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { todayIsoDate } from "@/lib/format/formatDate";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { notifyNoReplacement } from "../api/appointmentApi";
import { useAppointmentList } from "../hooks/useAppointmentList";
import type { useAppointmentUrlState } from "../hooks/useAppointmentUrlState";
import type { useDoctorLookup } from "../hooks/useDoctorLookup";
import { NOTIFY_SUCCESS_TEXT } from "../schema/appointmentMessages";
import {
  rangeErrorCode,
  resolveListRange,
} from "../schema/appointmentUrlRules";
import type { Appointment } from "../types/appointment";
import { AppointmentDetailSheet } from "./AppointmentDetailSheet";
import { AppointmentFilters } from "./AppointmentFilters";
import { AppointmentTable } from "./AppointmentTable";
import {
  ReassignDoctorDialog,
  isStaleAppointmentError,
} from "./ReassignDoctorDialog";

type AppointmentListTabProps = {
  url: ReturnType<typeof useAppointmentUrlState>;
  lookup: ReturnType<typeof useDoctorLookup>;
};

const EMPTY_TEXT =
  "Chưa có lịch hẹn trong khoảng ngày đã chọn. Thử mở rộng khoảng ngày hoặc bỏ lọc trạng thái.";
const INVALID_RANGE_TEXT = "Sửa khoảng ngày để xem lịch hẹn.";

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
  const { reload, replaceItem, filters } = list;

  const [viewing, setViewing] = useState<Appointment | null>(null);
  const [reassigning, setReassigning] = useState<Appointment | null>(null);
  const [notifying, setNotifying] = useState<Appointment | null>(null);

  const handleStale = useCallback(() => {
    setReassigning(null);
    reload();
  }, [reload]);

  function handleChanged(updated: Appointment) {
    setReassigning(null);
    replaceItem((item) => item.id === updated.id, updated);
    // Đang lọc theo bác sĩ cũ thì dòng không còn thuộc bộ lọc: tải lại.
    if (filters.doctorId && updated.doctor?.id !== filters.doctorId) reload();
  }

  async function confirmNotify() {
    if (!notifying) return;
    try {
      const updated = await notifyNoReplacement(notifying.id);
      notifySuccess(NOTIFY_SUCCESS_TEXT);
      replaceItem((item) => item.id === updated.id, updated);
    } catch (error) {
      notifyError(error);
      if (!isStaleAppointmentError(error)) throw error; // giữ Confirm để thử lại
      reload(); // lịch không còn dùng được: đóng Confirm và tải lại
    }
  }

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
        onRetry={reload}
        onView={setViewing}
        onReassign={setReassigning}
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
      <ReassignDoctorDialog
        appointment={reassigning}
        onClose={() => setReassigning(null)}
        onChanged={handleChanged}
        onStale={handleStale}
        onNotifyRequest={(appointment) => {
          setReassigning(null);
          setNotifying(appointment);
        }}
      />
      <ConfirmActionDialog
        open={notifying !== null}
        onOpenChange={(open) => !open && setNotifying(null)}
        title="Báo cho bệnh nhân"
        description="Hệ thống sẽ báo cho bệnh nhân rằng bác sĩ không thể khám vào giờ hẹn và bệnh nhân có thể tự hủy lịch hẹn. Lịch hẹn vẫn giữ trạng thái Đã đặt. Thông báo đã gửi không thể thu hồi."
        confirmLabel="Gửi thông báo"
        pendingLabel="Đang gửi..."
        onConfirm={confirmNotify}
      />
    </div>
  );
}
