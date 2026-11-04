"use client";

import { PageHeader } from "@/components/PageHeader";
import { DoctorLoadState } from "./DoctorLoadState";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  VISIT_STATUS_LABELS,
  VISIT_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { formatDate, formatDateTime } from "@/lib/format/formatDate";
import {
  useDoctorExamApi,
  useMedicalHistory,
  usePastVisits,
  usePatientAppointments,
} from "../hooks/useDoctorData";
import { BackendGapNotice } from "./BackendGapNotice";
import { MedicalHistoryForm } from "./MedicalHistoryForm";
import { useVisitWorkspace } from "./VisitWorkspaceProvider";

// Tab Hồ sơ bệnh nhân: tiền sử (UC12), hồ sơ các lượt khám trước (UC40) và lịch hẹn (UC24).
// Hồ sơ cũ gồm cả lượt chưa kết luận, đánh dấu "chưa kết luận" (BR-65).

function PastVisits({ patientProfileId }: { patientProfileId: string }) {
  const api = useDoctorExamApi();
  const { data, loading } = usePastVisits(patientProfileId);
  return (
    <section aria-labelledby="past-title" className="space-y-3">
      <h2 id="past-title" className="text-lg font-semibold">
        Hồ sơ bệnh án cũ
      </h2>
      {!api.capabilities.pastRecords ? (
        <BackendGapNotice title="Chưa có API hồ sơ lượt khám cũ">
          Chẩn đoán, lời dặn, đơn thuốc, kết quả hình ảnh và xét nghiệm của các
          lượt trước sẽ hiện ở đây.
        </BackendGapNotice>
      ) : loading ? (
        <Skeleton className="h-28 w-full" />
      ) : !data || data.length === 0 ? (
        <p className="rounded-lg border bg-card px-4 py-6 text-center text-sm text-muted-foreground">
          Bệnh nhân chưa có lượt khám nào trước đây.
        </p>
      ) : (
        <ul className="divide-y rounded-lg border bg-card">
          {data.map((visit) => (
            <li key={visit.visitId} className="space-y-1.5 px-4 py-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-medium">
                  {formatDate(visit.visitDate)}
                </span>
                <StatusBadge tone={VISIT_STATUS_TONE[visit.status]}>
                  {VISIT_STATUS_LABELS[visit.status]}
                </StatusBadge>
                {visit.status === "INCOMPLETE" ? (
                  <span className="text-xs text-muted-foreground">
                    Chưa kết luận
                  </span>
                ) : null}
              </div>
              <p className="text-sm">
                {visit.primaryDiagnosis ?? "Không có chẩn đoán"}
                {visit.prescriptions
                  ? ` · ${visit.prescriptions} đơn thuốc`
                  : ""}
              </p>
              {visit.advice ? (
                <p className="text-sm text-muted-foreground">
                  Lời dặn: {visit.advice}
                </p>
              ) : null}
              {[...visit.imaging, ...visit.laboratory].map((line) => (
                <p key={line} className="text-sm text-muted-foreground">
                  {line}
                </p>
              ))}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

const APPOINTMENT_STATUS: Record<string, string> = {
  CONFIRMED: "Đã đặt",
  CHECKED_IN: "Đã tiếp nhận",
  IN_PROGRESS: "Đang khám",
  COMPLETED: "Đã hoàn tất",
  CANCELLED: "Đã hủy",
  NO_SHOW: "Không đến",
};

function Appointments({ patientProfileId }: { patientProfileId: string }) {
  const { data, loading, error, refresh } =
    usePatientAppointments(patientProfileId);
  return (
    <section aria-labelledby="appt-title" className="space-y-3">
      <h2 id="appt-title" className="text-lg font-semibold">
        Lịch hẹn của bệnh nhân
      </h2>
      {loading || error ? (
        <DoctorLoadState loading={loading} error={error} retry={refresh} />
      ) : !data || data.length === 0 ? (
        <p className="rounded-lg border bg-card px-4 py-6 text-center text-sm text-muted-foreground">
          Chưa có lịch hẹn nào.
        </p>
      ) : (
        <ul className="divide-y rounded-lg border bg-card">
          {data.map((appointment) => (
            <li
              key={appointment.id}
              className="flex items-center justify-between gap-3 px-4 py-2.5"
            >
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">
                  {appointment.serviceName}
                </p>
                <p className="text-xs text-muted-foreground">
                  {formatDateTime(appointment.startTime)}
                </p>
              </div>
              <StatusBadge tone="neutral">
                {APPOINTMENT_STATUS[appointment.status] ?? appointment.status}
              </StatusBadge>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function History({ patientProfileId }: { patientProfileId: string }) {
  const { data, error, loading, refresh } = useMedicalHistory(patientProfileId);
  const workspace = useVisitWorkspace();
  if (loading || error || !data)
    return <DoctorLoadState loading={loading} error={error} retry={refresh} />;
  return (
    <MedicalHistoryForm
      key={data.version}
      patientProfileId={patientProfileId}
      initial={data}
      onSaved={() => {
        refresh();
        workspace.refresh(); // header hiện dị ứng, phải cập nhật theo
      }}
    />
  );
}

export function RecordsScreen() {
  const { context, loading } = useVisitWorkspace();
  const patientProfileId = context?.patientProfileId ?? null;

  return (
    <>
      <PageHeader
        title="Hồ sơ bệnh nhân"
        description="Tiền sử, hồ sơ các lượt khám trước và lịch hẹn để tham khảo khi khám."
      />
      {loading ? (
        <Skeleton className="h-40 w-full" />
      ) : !patientProfileId ? (
        <BackendGapNotice title="Chưa xác định được hồ sơ bệnh nhân">
          Backend chưa cho bác sĩ đọc hồ sơ bệnh nhân của lượt khám vãng lai.
        </BackendGapNotice>
      ) : (
        <div className="space-y-8">
          <History patientProfileId={patientProfileId} />
          <PastVisits patientProfileId={patientProfileId} />
          <Appointments patientProfileId={patientProfileId} />
        </div>
      )}
    </>
  );
}
