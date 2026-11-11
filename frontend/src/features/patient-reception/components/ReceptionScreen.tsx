"use client";

import {
  isReceptionSectionAvailable,
  type ReceptionSection,
} from "../navigation";
import { useReception } from "./ReceptionProvider";
import { DataLoading } from "./ReceptionUi";
import { IntakeScreen } from "./IntakeScreen";
import { PatientSearchScreen } from "./PatientScreens";
import { AppointmentScreen } from "./AppointmentScreen";
import { QueueScreen } from "./QueueScreen";
import { PaymentHistoryScreen, PaymentItemsScreen } from "./PaymentScreens";
import { ResultScreen, VisitScreen } from "./VisitScreens";
import { NoticeScreen, ReferenceScreen } from "./ReferenceScreens";
import { ClinicalRoundOrdersScreen } from "./ClinicalRoundOrdersScreen";
import { AccountScreen } from "./AccountScreen";

export function ReceptionScreen({ section }: { section: ReceptionSection }) {
  const { data, loading, error } = useReception();
  if (!isReceptionSectionAvailable(section)) return <ReceptionSectionLocked />;
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
      return <ClinicalRoundOrdersScreen />;
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
