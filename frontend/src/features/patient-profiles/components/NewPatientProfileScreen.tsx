"use client";

import { useRouter } from "next/navigation";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Skeleton } from "@/components/ui/skeleton";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { messageFromError } from "@/lib/messages/appMessages";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { getMockCurrentActor } from "@/lib/session/mockCurrentActor";
import { useProfileSuggestions } from "../hooks/useProfileSuggestions";
import { CreateProfileForm } from "./CreateProfileForm";
import { PortalLoadError } from "./PortalLoadError";
import { ProfileSuggestionList } from "./ProfileSuggestionList";

// Trang "Thêm hồ sơ bệnh nhân", ba khối theo thứ tự: hồ sơ đã có tại phòng khám (gợi ý theo
// email, chỉ hiện khi có), tạo hồ sơ mới cho người chưa từng khám, và hướng dẫn ra quầy cho
// hồ sơ cũ không có email hoặc khác email (màn gắn tại quầy thuộc plan Lễ tân).
export function NewPatientProfileScreen() {
  const router = useRouter();
  const linked = useLinkedProfiles();
  const suggestions = useProfileSuggestions();
  const hasSelf = linked.profiles.some(
    (profile) => profile.relationship === "SELF",
  );
  const email = getMockCurrentActor("patient").email ?? "";
  const showSuggestions =
    suggestions.loading ||
    suggestions.error !== null ||
    suggestions.suggestions.length > 0;

  return (
    <>
      <PortalPageHeader
        description="Gắn hồ sơ đã có tại phòng khám hoặc tạo hồ sơ mới cho người chưa từng khám."
        title="Thêm hồ sơ bệnh nhân"
      />
      <div className="space-y-10">
        {showSuggestions ? (
          <section
            aria-labelledby="suggested-heading"
            className="space-y-4"
            id="suggested"
          >
            <div className="space-y-2">
              <h2 className="text-lg font-semibold" id="suggested-heading">
                Hồ sơ đã có tại phòng khám
              </h2>
              <p className="text-base text-muted-foreground">
                Các hồ sơ dùng email {email} của bạn. Chọn quan hệ rồi bấm Gắn
                hồ sơ.
              </p>
            </div>
            {suggestions.loading ? (
              <div
                aria-busy="true"
                className="space-y-3"
                role="status"
              >
                <span className="sr-only">Đang tải hồ sơ gợi ý</span>
                <Skeleton className="h-28 w-full" />
              </div>
            ) : suggestions.error ? (
              <PortalLoadError
                message={messageFromError(suggestions.error)}
                onRetry={suggestions.reload}
                title="Chưa tải được hồ sơ gợi ý."
              />
            ) : (
              <ProfileSuggestionList
                hasSelf={hasSelf}
                onLinked={() => router.push(PATIENT_ROUTES.profiles)}
                onStale={suggestions.reload}
                suggestions={suggestions.suggestions}
              />
            )}
          </section>
        ) : null}

        <section aria-labelledby="create-heading" className="space-y-4">
          <div className="space-y-2">
            <h2 className="text-lg font-semibold" id="create-heading">
              Tạo hồ sơ mới
            </h2>
            <p className="text-base text-muted-foreground">
              Dành cho người chưa từng khám tại phòng khám.
            </p>
          </div>
          <CreateProfileForm
            hasSelf={hasSelf}
            onCreated={(profileId) =>
              router.push(PATIENT_ROUTES.profileDetail(profileId))
            }
          />
        </section>

        <p className="text-base text-muted-foreground">
          Đã từng khám nhưng không thấy hồ sơ ở trên? Mang giấy tờ tùy thân đến
          quầy lễ tân để được gắn hồ sơ.
        </p>
      </div>
    </>
  );
}
