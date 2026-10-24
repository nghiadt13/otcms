"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import Link from "next/link";
import { notFound, useRouter } from "next/navigation";
import { useState } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { isHiddenRecordError } from "../api/patientProfileErrors";
import { usePatientProfile } from "../hooks/usePatientProfile";
import type { PatientProfileDetail } from "../types/patientProfile";
import { PortalLoadError } from "./PortalLoadError";
import { ProfileEditForm } from "./ProfileEditForm";
import { ProfileInfoList } from "./ProfileInfoList";
import { UnlinkProfileDialog } from "./UnlinkProfileDialog";

// Chi tiết một hồ sơ đã gắn. Nút hiện theo `allowedActions` của BE. 403/404 đi tới trang không
// tìm thấy của khu; lỗi khác hiện câu và nút thử lại.
export function PatientProfileDetailScreen({
  profileId,
}: {
  profileId: string;
}) {
  const router = useRouter();
  const { detail, loading, error, reload } = usePatientProfile(profileId);
  const [editing, setEditing] = useState(false);
  const [unlinkOpen, setUnlinkOpen] = useState(false);

  if (isHiddenRecordError(error)) notFound();
  if (error)
    return (
      <PortalLoadError
        message={messageFromError(error)}
        onRetry={reload}
        title="Không tải được hồ sơ."
      />
    );
  if (loading || !detail) return <DetailSkeleton />;

  const canUpdate = detail.allowedActions.includes("update");
  const canEditHistory = detail.allowedActions.includes("editHistory");
  const canUnlink = detail.allowedActions.includes("unlink");

  return (
    <div className="space-y-8">
      <Link
        className="inline-flex min-h-11 items-center gap-1 text-base font-medium text-primary underline-offset-4 hover:underline focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        href={PATIENT_ROUTES.profiles}
      >
        <ChevronLeft aria-hidden />
        Hồ sơ bệnh nhân
      </Link>

      <PortalPageHeader
        actions={
          canUpdate && !editing ? (
            <button
              className={publicButtonClass("secondary")}
              onClick={() => setEditing(true)}
              type="button"
            >
              Sửa thông tin
            </button>
          ) : null
        }
        description={`${CARE_RELATIONSHIP_LABELS[detail.relationship]} · Mã bệnh nhân ${detail.patientCode}`}
        title={detail.fullName}
      />

      {editing ? (
        <ProfileEditForm
          detail={detail}
          onCancel={() => setEditing(false)}
          onSaved={() => {
            setEditing(false);
            reload();
          }}
        />
      ) : (
        <ProfileInfoList detail={detail} />
      )}

      {!editing ? (
        <ProfileActions
          canEditHistory={canEditHistory}
          canUnlink={canUnlink}
          detail={detail}
          onUnlinkClick={() => setUnlinkOpen(true)}
        />
      ) : null}

      <UnlinkProfileDialog
        onOpenChange={setUnlinkOpen}
        onUnlinked={() => router.replace(PATIENT_ROUTES.profiles)}
        open={unlinkOpen}
        profile={detail}
      />
    </div>
  );
}

function ProfileActions({
  detail,
  canEditHistory,
  canUnlink,
  onUnlinkClick,
}: {
  detail: PatientProfileDetail;
  canEditHistory: boolean;
  canUnlink: boolean;
  onUnlinkClick: () => void;
}) {
  if (!canEditHistory && !canUnlink) return null;
  return (
    <div className="space-y-6">
      {canEditHistory ? (
        <ul className="divide-y overflow-hidden rounded-lg border bg-card">
          <li>
            <Link
              className="flex min-h-14 items-center justify-between gap-3 px-4 py-3 text-base font-medium transition-colors hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none motion-reduce:transition-none"
              href={PATIENT_ROUTES.profileHistory(detail.id)}
            >
              Tiền sử bệnh
              <ChevronRight aria-hidden className="size-5 text-muted-foreground" />
            </Link>
          </li>
        </ul>
      ) : null}
      {canUnlink ? (
        <div className="border-t pt-6">
          <button
            className={cn(
              publicButtonClass("secondary"),
              "text-destructive hover:text-destructive",
            )}
            onClick={onUnlinkClick}
            type="button"
          >
            Bỏ gắn hồ sơ
          </button>
        </div>
      ) : null}
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div aria-busy="true" className="space-y-6" role="status">
      <span className="sr-only">Đang tải hồ sơ</span>
      <Skeleton className="h-6 w-40 max-w-full" />
      <div className="space-y-3">
        <Skeleton className="h-8 w-56 max-w-full" />
        <Skeleton className="h-5 w-80 max-w-full" />
      </div>
      <Skeleton className="h-64 w-full" />
    </div>
  );
}
