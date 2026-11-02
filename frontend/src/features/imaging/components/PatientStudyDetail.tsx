import { ExternalLink } from "lucide-react";
import { useId, type ReactNode } from "react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Button } from "@/components/ui/button";
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

function Field({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="grid gap-1 sm:contents">
      <dt className="text-base text-muted-foreground">{label}</dt>
      <dd className="min-w-0 text-base break-words">{value}</dd>
    </div>
  );
}

/**
 * Thông tin study, kết luận (chỉ khi lượt đã hoàn tất) và nút mở trình xem ảnh.
 * Trình xem mở ở tab mới bằng `viewerUrl` do BE cấp; FE không tự ghép URL.
 */
export function PatientStudyDetail({ study }: { study: PatientImagingStudy }) {
  const hintId = useId();
  const { viewerUrl } = study;
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
        <Button
          className={publicButtonClass("primary")}
          disabled={!viewerUrl}
          aria-describedby={hintId}
          onClick={() => {
            if (viewerUrl) window.open(viewerUrl, "_blank", "noopener");
          }}
        >
          <ExternalLink aria-hidden />
          Mở trình xem ảnh
        </Button>
        <p id={hintId} className="text-base text-muted-foreground">
          {viewerUrl
            ? "Ảnh mở ở tab mới."
            : "Trình xem ảnh tạm thời chưa mở được. Vui lòng thử lại sau."}
        </p>
      </section>
    </div>
  );
}
