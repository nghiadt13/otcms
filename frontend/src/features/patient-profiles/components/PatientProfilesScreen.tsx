"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { PortalNoProfileState } from "@/components/patient/PortalEmptyState";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { getMockCurrentActor } from "@/lib/session/mockCurrentActor";
import { useProfileSuggestions } from "../hooks/useProfileSuggestions";
import { PatientProfileList } from "./PatientProfileList";
import { PortalLoadError } from "./PortalLoadError";
import { SuggestionBanner } from "./SuggestionBanner";

// Trang "Hồ sơ bệnh nhân": hồ sơ đã gắn của tài khoản, kèm băng gợi ý khi có hồ sơ khám tại
// phòng khám dùng cùng email đã xác minh. Đủ bốn trạng thái: tải, lỗi, trống, có dữ liệu.
export function PatientProfilesScreen() {
  const { profiles, loading, error, reload } = useLinkedProfiles();
  const suggestions = useProfileSuggestions();
  const email = getMockCurrentActor("patient").email ?? "";
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
              Thêm hồ sơ bệnh nhân
            </Link>
          ) : null
        }
        description="Hồ sơ của bạn và người thân đã gắn với tài khoản này."
        title="Hồ sơ bệnh nhân"
      />
      <div className="space-y-6">
        {suggestions.suggestions.length > 0 ? (
          <SuggestionBanner
            count={suggestions.suggestions.length}
            email={email}
          />
        ) : null}
        {suggestions.error ? (
          <PortalLoadError
            message={messageFromError(suggestions.error)}
            onRetry={suggestions.reload}
            title="Chưa tải được hồ sơ gợi ý."
          />
        ) : null}
        {body}
      </div>
    </>
  );
}
