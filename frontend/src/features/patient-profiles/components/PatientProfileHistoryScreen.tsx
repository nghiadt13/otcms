"use client";

import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { useState } from "react";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { formatDate } from "@/lib/format/formatDate";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { isHiddenRecordError } from "../api/patientProfileErrors";
import { useMedicalHistory } from "../hooks/useMedicalHistory";
import type { MedicalHistory } from "../types/patientProfile";
import { MedicalHistoryEditor } from "./MedicalHistoryEditor";
import { PortalLoadError } from "./PortalLoadError";

// Backend UC12 kiểm tra quyền đọc/ghi trên chính endpoint tiền sử.
export function PatientProfileHistoryScreen({
  profileId,
}: {
  profileId: string;
}) {
  const profiles = useLinkedProfiles();
  const history = useMedicalHistory(profileId);
  const [editorKey, setEditorKey] = useState(0);

  if (isHiddenRecordError(history.error))
    notFound();

  const failure = history.error;
  if (failure)
    return (
      <PortalLoadError
        message={messageFromError(failure)}
        onRetry={() => {
          profiles.reload();
          void history.refresh();
        }}
        title="Không tải được tiền sử bệnh."
      />
    );

  if (history.loading || !history.history)
    return <HistorySkeleton />;

  const detail = profiles.profiles.find((item) => item.id === profileId);

  return (
    <div className="space-y-8">
      <Link
        className="inline-flex min-h-11 items-center gap-1 text-base font-medium text-primary underline-offset-4 hover:underline focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        href={PATIENT_ROUTES.profiles}
      >
        <ChevronLeft aria-hidden />
        Quay lại hồ sơ
      </Link>

      <PortalPageHeader
        description={detail ? `${detail.fullName} · ${CARE_RELATIONSHIP_LABELS[detail.relationship]}` : "Hồ sơ bệnh nhân"}
        title="Tiền sử bệnh"
      />

      <p className="text-base text-muted-foreground">
        {lastUpdatedText(history.history)}
      </p>

      <MedicalHistoryEditor
        canEdit
        history={history.history}
        key={editorKey}
        onReload={async () => {
          if (await history.refresh()) setEditorKey((key) => key + 1);
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
