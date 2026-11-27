"use client";

import type { ComponentType } from "react";
import type { ReceptionAppointment } from "../api/receptionLiveApi";
import {
  isReceptionSectionAvailable,
  type ReceptionSection,
} from "../navigation";
import { IntakeScreen } from "./IntakeScreen";
import { PatientSearchScreen } from "./PatientScreens";
import { CounterCareLinksScreen } from "./CounterCareLinksScreen";
import { AppointmentScreen } from "./AppointmentScreen";
import { QueueScreen } from "./QueueScreen";
import { PaymentHistoryScreen } from "./PaymentHistoryScreen";
import { PaymentItemsScreen } from "./PaymentItemsScreen";
import { VisitScreen } from "./LiveVisitScreens";
import { LiveResultScreen } from "./LiveResultScreen";
import { ReferenceScreen } from "./ReferenceScreens";
import { ClinicalRoundOrdersScreen } from "./ClinicalRoundOrdersScreen";
import { StaffRoomsScreen } from "@/components/rooms/StaffRoomsScreen";

export function ReceptionScreen({
  section,
  appointmentActions,
  accountComponent: AccountComponent,
  notificationsComponent: NotificationsComponent,
}: {
  section: ReceptionSection;
  appointmentActions?: ComponentType<{
    appointment: ReceptionAppointment;
    onChanged: () => void;
  }>;
  accountComponent?: ComponentType;
  notificationsComponent?: ComponentType;
}) {
  if (!isReceptionSectionAvailable(section)) return <ReceptionSectionLocked />;
  switch (section) {
    case "":
      return <IntakeScreen />;
    case "patients":
      return <PatientSearchScreen />;
    case "care-links":
      return <CounterCareLinksScreen />;
    case "appointments":
      return <AppointmentScreen Actions={appointmentActions} />;
    case "queues":
      return <QueueScreen />;
    case "visits":
      return <VisitScreen />;
    case "orders":
      return <ClinicalRoundOrdersScreen />;
    case "payments":
      return <PaymentItemsScreen />;
    case "payment-history":
      return <PaymentHistoryScreen />;
    case "results":
      return <LiveResultScreen />;
    case "rooms":
      return <StaffRoomsScreen />;
    case "services":
      return <ReferenceScreen />;
    case "notifications":
      return NotificationsComponent ? <NotificationsComponent /> : null;
    case "account":
      return AccountComponent ? <AccountComponent /> : null;
    default:
      return null;
  }
}

/** Màn còn dữ liệu giả: ở production khoá cho tới khi nối BE hoặc bật lại cờ demo. */
function ReceptionSectionLocked() {
  return (
    <section className="mx-auto max-w-xl space-y-4 p-6">
      <h1 className="text-2xl font-semibold">
        Khu vực lễ tân chưa được kích hoạt
      </h1>
      <p>
        Giao diện đang trong giai đoạn thử nghiệm. Cần kết nối API và phân quyền
        trước khi sử dụng vận hành.
      </p>
    </section>
  );
}
