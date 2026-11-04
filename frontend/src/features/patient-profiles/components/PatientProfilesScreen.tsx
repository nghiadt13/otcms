"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalNoProfileState } from "@/components/patient/PortalEmptyState";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { MedicalHistoryProfiles } from "./MedicalHistoryProfiles";
import { PatientProfileList } from "./PatientProfileList";
import { PortalLoadError } from "./PortalLoadError";

// Trang "Hồ sơ bệnh nhân": hồ sơ đã gắn của tài khoản. Đủ bốn trạng thái: tải, lỗi, trống, có dữ liệu.
export function PatientProfilesScreen() {
  const { profiles, loading, error, reload } = useLinkedProfiles();
  const showAddButton = !loading && !error && profiles.length > 0;

  let body: ReactNode;
  if (loading) {
    body = (
      <div
        aria-busy="true"
        className="overflow-hidden rounded-lg border bg-card"
        role="status"
      >
        <span className="sr-only">Đang tải hồ sơ</span>
        <div className="space-y-3 p-4">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      </div>
    );
  } else if (error) {
    body = (
      <PortalLoadError
        message={error}
        onRetry={reload}
        title="Không tải được hồ sơ."
      />
    );
  } else if (profiles.length === 0) {
    body = <PortalNoProfileState />;
  } else {
    body = <PatientProfileList profiles={profiles} />;
  }

  return (
    <>
      <PortalPageHeader
        actions={
          showAddButton ? (
            <Link
              className={publicButtonClass("primary")}
              href={PATIENT_ROUTES.newProfile}
            >
              Thêm thành viên
            </Link>
          ) : null
        }
        description="Hồ sơ của bạn và người thân đã gắn với tài khoản này."
        title="Hồ sơ bệnh nhân"
      />
      <div className="space-y-6">
        <MedicalHistoryProfiles />
        {body}
      </div>
    </>
  );
}
