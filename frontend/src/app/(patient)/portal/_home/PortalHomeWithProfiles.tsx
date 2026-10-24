"use client";

import { usePatientAppointmentList } from "@/features/appointments/hooks/usePatientAppointmentList";
import { UnpaidSummaryLine } from "@/features/billing/components/portal/UnpaidSummaryLine";
import { useVisitList } from "@/features/clinic-visits/hooks/useVisitList";
import { PortalSuggestionReminder } from "./PortalSuggestionReminder";
import {
  ActiveVisitsSection,
  PortalShortcutList,
  UpcomingAppointmentSection,
} from "./PortalHomeSections";

// Ghép các khối của trang chủ khi tài khoản đã có hồ sơ. Mỗi khối lấy dữ liệu từ feature của nó;
// việc ghép chỉ ở đây (app/), không import chéo giữa các feature.
//
// Một nút chính (`default`) cho trang chủ: lượt đang diễn ra có "Xem tiến độ" (lượt đầu tiên); không
// có lượt nào thì "Đặt lịch khám" nằm dưới phần lịch hẹn. Nút "Đặt lịch khám" trên thanh đầu trang
// đã đổi sang outline ở trang chủ (xem PatientPortalHeader).
export function PortalHomeWithProfiles() {
  const visits = useVisitList("active");
  const upcoming = usePatientAppointmentList("upcoming");
  const showBooking = !visits.loading && visits.items.length === 0;

  return (
    <div className="space-y-8">
      <PortalSuggestionReminder />
      <ActiveVisitsSection
        error={visits.error}
        loading={visits.loading}
        reload={visits.reload}
        visits={visits.items}
      />
      <UpcomingAppointmentSection
        appointments={upcoming.items}
        error={upcoming.error}
        loading={upcoming.loading}
        reload={upcoming.reload}
        showBooking={showBooking}
      />
      <UnpaidSummaryLine />
      <PortalShortcutList />
    </div>
  );
}
