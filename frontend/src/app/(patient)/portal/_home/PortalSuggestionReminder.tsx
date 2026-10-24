"use client";

import { SuggestionBanner } from "@/features/patient-profiles/components/SuggestionBanner";
import { useProfileSuggestions } from "@/features/patient-profiles/hooks/useProfileSuggestions";
import { messageFromError } from "@/lib/messages/appMessages";
import { getMockCurrentActor } from "@/lib/session/mockCurrentActor";
import { HomeSectionError } from "./PortalHomeSections";

// Nhắc hồ sơ gợi ý (BR-17) trên trang chủ: hồ sơ tại phòng khám dùng email đã xác minh nhưng chưa
// gắn. Chưa có gợi ý thì không hiện; tải lỗi thì báo để thử lại (không chặn các khối khác).
export function PortalSuggestionReminder() {
  const { suggestions, loading, error, reload } = useProfileSuggestions();
  const email = getMockCurrentActor("patient").email;

  if (loading) return null;
  if (error)
    return (
      <HomeSectionError
        message={`Không tải được hồ sơ gợi ý. ${messageFromError(error)}`}
        onRetry={reload}
      />
    );
  if (suggestions.length === 0 || !email) return null;
  return <SuggestionBanner count={suggestions.length} email={email} />;
}
