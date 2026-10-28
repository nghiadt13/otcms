"use client";

import { PortalNoProfileState } from "@/components/patient/PortalEmptyState";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { PortalHomeWithProfiles } from "./PortalHomeWithProfiles";
import { PortalSuggestionReminder } from "./PortalSuggestionReminder";
import { HomeSectionError, PortalHomeSkeleton } from "./PortalHomeSections";

// Thân trang chủ theo tình trạng hồ sơ: đang tải thì khung chờ; lỗi thì báo và thử lại; chưa có hồ
// sơ thì nhắc gợi ý (nếu có) và dẫn tới thêm hồ sơ; có hồ sơ thì ghép các khối tổng hợp.
export function PortalHomeBody() {
  const { profiles, loading, error, reload } = useLinkedProfiles();

  if (loading) return <PortalHomeSkeleton />;
  if (error)
    return (
      <HomeSectionError
        message={`Không tải được danh sách người khám. ${error}`}
        onRetry={reload}
      />
    );
  if (profiles.length === 0)
    return (
      <div className="space-y-8">
        <PortalSuggestionReminder />
        <PortalNoProfileState />
      </div>
    );
  return <PortalHomeWithProfiles />;
}
