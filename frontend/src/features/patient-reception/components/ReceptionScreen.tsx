"use client";

import type { ReceptionSection } from "../navigation";
import { useReception } from "./ReceptionProvider";
import { DataLoading } from "./ReceptionUi";
import { IntakeScreen } from "./IntakeScreen";
import { PatientSearchScreen } from "./PatientScreens";
import { AppointmentScreen } from "./AppointmentScreen";
import { QueueScreen } from "./QueueScreen";
import { PaymentHistoryScreen, PaymentItemsScreen } from "./PaymentScreens";
import { OrderScreen, ResultScreen, VisitScreen } from "./VisitScreens";
import { NoticeScreen, ReferenceScreen } from "./ReferenceScreens";
import { AccountScreen } from "./AccountScreen";

export function ReceptionScreen({ section }: { section: ReceptionSection }) {
  const { data, loading, error } = useReception();
  if (!data)
    return loading ? (
      <DataLoading />
    ) : (
      <p role="status">
        {error ? "Chọn Thử lại để tải dữ liệu." : "Dữ liệu chưa sẵn sàng."}
      </p>
    );
  switch (section) {
    case "":
      return <IntakeScreen />;
    case "patients":
      return <PatientSearchScreen />;
    case "appointments":
      return <AppointmentScreen />;
    case "queues":
      return <QueueScreen />;
    case "visits":
      return <VisitScreen />;
    case "orders":
      return <OrderScreen />;
    case "payments":
      return <PaymentItemsScreen />;
    case "payment-history":
      return <PaymentHistoryScreen />;
    case "results":
      return <ResultScreen />;
    case "rooms":
    case "services":
    case "schedule":
      return <ReferenceScreen key={section} type={section} />;
    case "notifications":
      return <NoticeScreen />;
    case "account":
      return <AccountScreen />;
  }
}
