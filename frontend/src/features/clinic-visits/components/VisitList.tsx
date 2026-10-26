"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { ProfileScopeSelect } from "@/components/patient/ProfileScopeSelect";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { toQueryString } from "@/lib/api/pagination";
import type { VisitWhen } from "../types/visitProgress";
import { VisitListBody, VisitListSkeleton } from "./VisitListBody";
import { VisitLoadError } from "./VisitLoadError";

// Danh sách lượt khám (UC34). URL là nguồn sự thật: `?tab=recent` cho tab Gần đây, `?profileId=`
// cho người khám. Mã trên URL chỉ được dùng khi thuộc hồ sơ đã gắn; còn lại coi như "Tất cả".
// Đổi tab đẩy lịch sử (Back quay về tab trước); đổi người khám thay thế URL.

export function VisitList() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { profiles, loading, error, reload } = useLinkedProfiles();

  const when: VisitWhen =
    searchParams.get("tab") === "recent" ? "recent" : "active";
  const profileParam = searchParams.get("profileId") ?? undefined;
  const patientId = profiles.some((profile) => profile.id === profileParam)
    ? profileParam
    : undefined;

  function navigate(
    next: { when: VisitWhen; patientId?: string },
    mode: "push" | "replace",
  ) {
    const url = `${pathname}${toQueryString({
      tab: next.when === "recent" ? "recent" : undefined,
      profileId: next.patientId,
    })}`;
    if (mode === "push") router.push(url);
    else router.replace(url);
  }

  if (loading) return <VisitListSkeleton />;
  if (error)
    return (
      <VisitLoadError
        message={`Không tải được danh sách người khám. ${error}`}
        onRetry={reload}
      />
    );

  return (
    <div className="space-y-6">
      <ProfileScopeSelect
        onChange={(next) => navigate({ when, patientId: next }, "replace")}
        profiles={profiles}
        value={patientId}
      />
      <Tabs
        onValueChange={(value) =>
          navigate(
            { when: value === "recent" ? "recent" : "active", patientId },
            "push",
          )
        }
        value={when}
      >
        <TabsList className="grid h-14! w-full grid-cols-2">
          <TabsTrigger className="text-base" value="active">
            Đang diễn ra
          </TabsTrigger>
          <TabsTrigger className="text-base" value="recent">
            Gần đây
          </TabsTrigger>
        </TabsList>
        <TabsContent className="mt-4" value={when}>
          {/* `key` theo bộ lọc: đổi tab hoặc người khám thì tải lại từ trang đầu. */}
          <VisitListBody
            key={`${when}:${patientId ?? ""}`}
            patientId={patientId}
            when={when}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
}
