"use client";

import { PageHeader } from "@/components/PageHeader";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useAppointmentUrlState } from "../hooks/useAppointmentUrlState";
import { useDoctorLookup } from "../hooks/useDoctorLookup";
import { addDays } from "@/lib/format/formatDate";
import { MAX_AVAILABILITY_DAYS } from "../schema/appointmentUrlRules";
import type { AppointmentTab } from "../types/appointment";
import { AppointmentListTab } from "./AppointmentListTab";
import { AvailabilityTab } from "./AvailabilityTab";

export function AppointmentsManagement() {
  const url = useAppointmentUrlState();
  // Một lần tải bác sĩ dùng chung cho hai tab (tab giờ trống tự lọc bác sĩ nhận đặt lịch).
  const lookup = useDoctorLookup();
  const { state, update } = url;

  function changeTab(tab: AppointmentTab) {
    if (tab === state.tab) return;
    // Sang tab giờ trống mà khoảng dài hơn 7 ngày thì cắt còn 7 ngày.
    const last = state.from
      ? addDays(state.from, MAX_AVAILABILITY_DAYS - 1)
      : undefined;
    const to =
      tab === "availability" && state.to && last && state.to > last
        ? last
        : state.to;
    update({ tab, to }, "push");
  }

  return (
    <div>
      <PageHeader
        title="Lịch hẹn"
        description="Xem lịch hẹn, kiểm tra giờ trống của bác sĩ và chuyển lịch hẹn sang bác sĩ khác khi cần."
      />
      <Tabs
        value={state.tab}
        onValueChange={(value) => changeTab(value as AppointmentTab)}
        className="gap-4"
      >
        <TabsList className="w-full sm:w-fit">
          <TabsTrigger value="list">Danh sách lịch hẹn</TabsTrigger>
          <TabsTrigger value="availability">Giờ trống của bác sĩ</TabsTrigger>
        </TabsList>
        <TabsContent value="list">
          <AppointmentListTab url={url} lookup={lookup} />
        </TabsContent>
        <TabsContent value="availability">
          <AvailabilityTab url={url} lookup={lookup} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
