"use client";

import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { useState } from "react";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { formatDate } from "@/lib/format/formatDate";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { isHiddenRecordError } from "../api/patientProfileErrors";
import { useMedicalHistory } from "../hooks/useMedicalHistory";
import { usePatientProfile } from "../hooks/usePatientProfile";
import type { MedicalHistory } from "../types/patientProfile";
import { MedicalHistoryEditor } from "./MedicalHistoryEditor";
import { PortalLoadError } from "./PortalLoadError";

// Tiền sử bệnh của một hồ sơ đã gắn. Người dùng và bác sĩ cùng ghi; dòng "Cập nhật lần cuối"
// cho biết ai sửa gần nhất. Nút sửa hiện theo `allowedActions` (editHistory).
export function PatientProfileHistoryScreen({
  profileId,
}: {
  profileId: string;
}) {
  const profile = usePatientProfile(profileId);
  const history = useMedicalHistory(profileId);
  const [editorKey, setEditorKey] = useState(0);

  if (isHiddenRecordError(profile.error) || isHiddenRecordError(history.error))
    notFound();

  const failure = profile.error ?? history.error;
  if (failure)
    return (
      <PortalLoadError
        message={messageFromError(failure)}
        onRetry={() => {
          profile.reload();
          void history.refresh();
        }}
        title="Không tải được tiền sử bệnh."
      />
    );

  if (profile.loading || history.loading || !profile.detail || !history.history)
    return <HistorySkeleton />;

  const detail = profile.detail;
  const canEdit = detail.allowedActions.includes("editHistory");

  return (
    <div className="space-y-8">
      <Link
        className="inline-flex min-h-11 items-center gap-1 text-base font-medium text-primary underline-offset-4 hover:underline focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        href={PATIENT_ROUTES.profileDetail(detail.id)}
      >
        <ChevronLeft aria-hidden />
        Quay lại hồ sơ
      </Link>

      <PortalPageHeader
        description={`${detail.fullName} · ${CARE_RELATIONSHIP_LABELS[detail.relationship]}`}
        title="Tiền sử bệnh"
      />

      <p className="text-base text-muted-foreground">
        {lastUpdatedText(history.history)}
      </p>

      <MedicalHistoryEditor
        canEdit={canEdit}
        history={history.history}
        key={editorKey}
        onReload={async () => {
          await history.refresh();
          setEditorKey((key) => key + 1);
        }}
        onSave={history.save}
      />
    </div>
  );
}

function lastUpdatedText(history: MedicalHistory): string {
  if (!history.updatedAt || !history.updatedBy)
    return "Chưa có tiền sử nào được ghi.";
  return `Cập nhật lần cuối ${formatDate(history.updatedAt)} bởi ${history.updatedBy}`;
}

function HistorySkeleton() {
  return (
    <div aria-busy="true" className="space-y-6" role="status">
      <span className="sr-only">Đang tải tiền sử</span>
      <Skeleton className="h-6 w-40 max-w-full" />
      <div className="space-y-2">
        <Skeleton className="h-8 w-56 max-w-full" />
        <Skeleton className="h-5 w-72 max-w-full" />
      </div>
      <Skeleton className="h-28 w-full" />
      <Skeleton className="h-28 w-full" />
    </div>
  );
}
