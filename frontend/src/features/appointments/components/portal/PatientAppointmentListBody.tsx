"use client";

import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalEmptyState } from "@/components/patient/PortalEmptyState";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { usePatientAppointmentList } from "../../hooks/usePatientAppointmentList";
import type { AppointmentWhen } from "../../types/patientAppointment";
import { PatientAppointmentRow } from "./PatientAppointmentRow";
import { PatientLoadError } from "./PatientLoadError";

// Các trạng thái của danh sách: đang tải (khung chờ đúng hình dòng), lỗi, trống (câu dạy cách
// dùng), có dòng (list có vạch chia, cả dòng là link) và "Xem thêm" khi còn trang.

const EMPTY_TEXT: Record<AppointmentWhen, string> = {
  upcoming:
    "Bạn chưa có lịch hẹn sắp tới. Bấm Đặt lịch khám ở trên để chọn giờ phù hợp.",
  past: "Chưa có lịch hẹn nào đã qua.",
};

export function PatientAppointmentListSkeleton() {
  return (
    <div aria-busy="true" role="status" className="rounded-lg border bg-card divide-y">
      <span className="sr-only">Đang tải lịch hẹn</span>
      {[0, 1, 2].map((row) => (
        <div className="space-y-2 px-4 py-4" key={row}>
          <Skeleton className="h-5 w-3/4" />
          <Skeleton className="h-5 w-1/2" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      ))}
    </div>
  );
}

export function PatientAppointmentListBody({
  when,
  patientId,
}: {
  when: AppointmentWhen;
  patientId?: string;
}) {
  const list = usePatientAppointmentList(when, patientId);

  if (list.loading) return <PatientAppointmentListSkeleton />;
  if (list.error)
    return (
      <PatientLoadError
        message={`Không tải được lịch hẹn. ${list.error}`}
        onRetry={list.reload}
      />
    );
  if (list.items.length === 0)
    return <PortalEmptyState>{EMPTY_TEXT[when]}</PortalEmptyState>;

  return (
    <div className="space-y-4">
      <ul className="divide-y rounded-lg border bg-card">
        {list.items.map((appointment) => (
          <PatientAppointmentRow appointment={appointment} key={appointment.id} />
        ))}
      </ul>
      {list.hasMore ? (
        <button
          className={cn(publicButtonClass("secondary"), "w-full sm:w-auto")}
          disabled={list.loadingMore}
          onClick={list.loadMore}
          type="button"
        >
          {list.loadingMore ? "Đang tải..." : "Xem thêm"}
        </button>
      ) : null}
    </div>
  );
}
