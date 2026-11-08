"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useLinkedProfiles } from "@/hooks/useLinkedProfiles";
import { toQueryString } from "@/lib/api/pagination";
import { usePatientAppointmentSummary } from "../../hooks/usePatientAppointmentSummary";
import { cn } from "@/lib/utils";
import type { AppointmentWhen } from "../../types/patientAppointment";
import {
  PatientAppointmentListBody,
  PatientAppointmentListSkeleton,
} from "./PatientAppointmentListBody";
import { PatientAppointmentPersonFilter } from "./PatientAppointmentPersonFilter";
import { PatientAppointmentSummary } from "./PatientAppointmentSummary";
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

  const summary = usePatientAppointmentSummary(patientId);

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

  const tabLabel = (text: string, count: number | undefined) =>
    count === undefined ? text : `${text} (${count})`;

  return (
    <div className="space-y-8">
      <PatientAppointmentSummary summary={summary} />
      <section
        aria-labelledby="appointment-list-heading"
        className={cn("space-y-5", summary && "border-t pt-8")}
      >
        <h2 className="text-xl font-semibold" id="appointment-list-heading">
          Danh sách lịch khám
        </h2>
        <PatientAppointmentPersonFilter
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
              {tabLabel("Sắp tới", summary?.upcoming)}
            </TabsTrigger>
            <TabsTrigger className="text-base" value="past">
              {tabLabel("Đã qua", summary?.past)}
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
      </section>
    </div>
  );
}
