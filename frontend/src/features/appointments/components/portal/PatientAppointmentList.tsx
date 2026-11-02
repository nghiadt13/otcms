"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { ProfileScopeSelect } from "@/components/patient/ProfileScopeSelect";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { toQueryString } from "@/lib/api/pagination";
import type { AppointmentWhen } from "../../types/patientAppointment";
import {
  PatientAppointmentListBody,
  PatientAppointmentListSkeleton,
} from "./PatientAppointmentListBody";
import { PatientLoadError } from "./PatientLoadError";

// Danh sách lịch hẹn khu bệnh nhân (UC23). URL là nguồn sự thật: `?tab=past` cho tab Đã qua,
// `?profileId=` cho người khám. Mã trên URL chỉ được dùng khi thuộc hồ sơ đã gắn; còn lại thì
// coi như "Tất cả". Đổi tab đẩy lịch sử (Back quay về tab trước); đổi người khám thay thế URL.

export function PatientAppointmentList() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { profiles, loading, error, reload } = useLinkedProfiles();

  const when: AppointmentWhen =
    searchParams.get("tab") === "past" ? "past" : "upcoming";
  const profileParam = searchParams.get("profileId") ?? undefined;
  const patientId = profiles.some((profile) => profile.id === profileParam)
    ? profileParam
    : undefined;

  function navigate(
    next: { when: AppointmentWhen; patientId?: string },
    mode: "push" | "replace",
  ) {
    const url = `${pathname}${toQueryString({
      tab: next.when === "past" ? "past" : undefined,
      profileId: next.patientId,
    })}`;
    if (mode === "push") router.push(url);
    else router.replace(url);
  }

  if (loading) return <PatientAppointmentListSkeleton />;
  if (error)
    return (
      <PatientLoadError
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
            { when: value === "past" ? "past" : "upcoming", patientId },
            "push",
          )
        }
        value={when}
      >
        <TabsList className="grid h-14! w-full grid-cols-2">
          <TabsTrigger className="text-base" value="upcoming">
            Sắp tới
          </TabsTrigger>
          <TabsTrigger className="text-base" value="past">
            Đã qua
          </TabsTrigger>
        </TabsList>
        <TabsContent className="mt-4" value={when}>
          {/* `key` theo bộ lọc: đổi tab hoặc người khám thì tải lại từ trang đầu. */}
          <PatientAppointmentListBody
            key={`${when}:${patientId ?? ""}`}
            patientId={patientId}
            when={when}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
}
