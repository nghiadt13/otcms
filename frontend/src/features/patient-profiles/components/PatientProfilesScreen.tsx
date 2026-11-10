"use client";

import { Plus } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalNoProfileState } from "@/components/patient/PortalEmptyState";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";
import { PatientProfileList } from "./PatientProfileList";
import { PortalLoadError } from "./PortalLoadError";

// Trang "Quản lý gia đình": hồ sơ đã gắn của tài khoản (bản thân và người thân), dạng lưới thẻ. Tiền sử
// bệnh nằm trong trang chi tiết từng hồ sơ, không hiện ở đây. Đủ bốn trạng thái: tải, lỗi, trống, có dữ liệu.
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
    body = <PatientProfileList onChanged={reload} profiles={profiles} />;
  }

  return (
    <>
      <PortalPageHeader
        actions={
          showAddButton ? (
            <Link
              className={cn(publicButtonClass("primary"), "gap-2")}
              href={PATIENT_ROUTES.newProfile}
            >
              <Plus aria-hidden className="size-5" />
              Thêm thành viên
            </Link>
          ) : null
        }
        title="Quản lý gia đình"
      />
      {body}
    </>
  );
}
