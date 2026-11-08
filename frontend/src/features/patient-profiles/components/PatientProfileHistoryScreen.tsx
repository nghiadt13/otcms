"use client";

import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { formatDate } from "@/lib/format/formatDate";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { canOpenMedicalHistory } from "../api/medicalHistoryProfilesApi";
import { medicalHistoryErrorMessage } from "../api/medicalHistoryErrors";
import { useMedicalHistory } from "../hooks/useMedicalHistory";
import { useMedicalHistoryProfiles } from "../hooks/useMedicalHistoryProfiles";
import type { MedicalHistory } from "../types/patientProfile";
import { MedicalHistoryEditor } from "./MedicalHistoryEditor";
import { PortalLoadError } from "./PortalLoadError";

export function PatientProfileHistoryScreen({
  profileId,
}: {
  profileId: string;
}) {
  return <HistoryForProfile key={profileId} profileId={profileId} />;
}

function HistoryForProfile({ profileId }: { profileId: string }) {
  const profiles = useMedicalHistoryProfiles();
  if (profiles.error)
    return (
      <PortalLoadError
        title="Không tải được hồ sơ."
        message={medicalHistoryErrorMessage(profiles.error)}
        onRetry={profiles.reload}
      />
    );
  if (!profiles.profiles) return <HistorySkeleton />;
  const profile = profiles.profiles.find(
    (item) => item.patientProfileId === profileId,
  );
  if (!profile || !canOpenMedicalHistory(profile))
    return (
      <p className="text-base text-destructive" role="alert">
        {formatAppMessage("MSG-19")}
      </p>
    );

  return (
    <div className="space-y-8">
      <Link
        className="inline-flex min-h-11 items-center gap-1 text-base font-medium text-primary underline-offset-4 hover:underline focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        href={PATIENT_ROUTES.profiles}
      >
        <ChevronLeft aria-hidden />
        Quản lý gia đình
      </Link>
      <PortalPageHeader
        title="Tiền sử bệnh"
        description={`${profile.fullName} · ${CARE_RELATIONSHIP_LABELS[profile.relationship]}`}
      />
      <HistoryContent profileId={profileId} />
    </div>
  );
}

function HistoryContent({ profileId }: { profileId: string }) {
  const history = useMedicalHistory(profileId);
  if (history.error)
    return (
      <PortalLoadError
        title="Không tải được tiền sử bệnh."
        message={medicalHistoryErrorMessage(history.error)}
        onRetry={() => void history.refresh().catch(() => undefined)}
      />
    );
  if (!history.history) return <HistorySkeleton />;
  return (
    <>
      <p className="text-base text-muted-foreground">
        {lastUpdatedText(history.history)}
      </p>
      <MedicalHistoryEditor
        canEdit
        history={history.history}
        onReload={history.refresh}
        onSave={history.save}
      />
    </>
  );
}

function lastUpdatedText(history: MedicalHistory): string {
  if (!history.updatedAt) return "Chưa có tiền sử nào được ghi.";
  const editor = history.updatedBy ? ` bởi ${history.updatedBy}` : "";
  return `Cập nhật lần cuối ${formatDate(history.updatedAt)}${editor}`;
}

function HistorySkeleton() {
  return (
    <div aria-busy="true" className="space-y-6" role="status">
      <span className="sr-only">Đang tải tiền sử</span>
      <Skeleton className="h-6 w-40 max-w-full" />
      <Skeleton className="h-8 w-56 max-w-full" />
      <Skeleton className="h-28 w-full" />
      <Skeleton className="h-28 w-full" />
    </div>
  );
}
