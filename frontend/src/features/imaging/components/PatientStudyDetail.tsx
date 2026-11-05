import { type ReactNode } from "react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import {
  VISIT_STATUS_LABELS,
  VISIT_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { formatDate } from "@/lib/format/formatDate";
import {
  IMAGING_MODALITY_LABELS,
  type PatientImagingStudy,
} from "../types/imagingStudy";
import { ImagingViewerLauncher } from "./ImagingViewerLauncher";

function Field({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="grid gap-1 sm:contents">
      <dt className="text-base text-muted-foreground">{label}</dt>
      <dd className="min-w-0 text-base break-words">{value}</dd>
    </div>
  );
}

/**
 * Thông tin study, kết luận (chỉ khi lượt đã hoàn tất) và luồng cấp quyền mở ảnh.
 * Viewer grant do BE giữ trong cookie HttpOnly; UI chỉ ghép đường dẫn cùng origin cho Study UID.
 */
export function PatientStudyDetail({ study }: { study: PatientImagingStudy }) {
  return (
    <div className="space-y-8">
      <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-[minmax(0,11rem)_minmax(0,1fr)]">
        <Field
          label="Loại ảnh"
          value={IMAGING_MODALITY_LABELS[study.modality]}
        />
        <Field label="Ngày chụp" value={formatDate(study.studyDate)} />
        <Field label="Số series" value={`${study.seriesCount} series`} />
        <Field
          label="Người bệnh"
          value={`${study.patient.fullName} (${CARE_RELATIONSHIP_LABELS[study.patient.relationship]})`}
        />
        <Field
          label="Trạng thái lượt khám"
          value={
            <StatusBadge
              tone={VISIT_STATUS_TONE[study.visitStatus]}
              className="h-6 text-sm"
            >
              {VISIT_STATUS_LABELS[study.visitStatus]}
            </StatusBadge>
          }
        />
      </dl>

      <section
        aria-labelledby="study-finding-title"
        className="space-y-3 border-t pt-6"
      >
        <h2 id="study-finding-title" className="text-xl font-semibold">
          Kết luận
        </h2>
        {study.finding ? (
          <p className="text-base">{study.finding}</p>
        ) : (
          <p className="text-base text-muted-foreground">
            {study.visitStatus === "COMPLETED"
              ? "Bác sĩ chưa ghi kết luận cho ảnh này."
              : "Lượt khám chưa được bác sĩ kết luận nên chưa có kết luận cho ảnh này."}
          </p>
        )}
      </section>

      <section aria-label="Trình xem ảnh" className="space-y-3 border-t pt-6">
        <ImagingViewerLauncher
          studyRef={study.id}
          studyInstanceUid={study.studyInstanceUid}
          purpose="PATIENT_VIEW"
        />
      </section>
    </div>
  );
}
