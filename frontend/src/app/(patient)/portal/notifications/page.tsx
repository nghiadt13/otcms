import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { PatientNotificationsClient } from "./PatientNotificationsClient";

export const metadata = {
  title: "Thông báo | OTCMS",
};

// Thông báo của tài khoản (UC63). Nội dung là client vì map bản ghi → màn là hàm, không đi qua ranh
// giới server → client.
export default function PatientNotificationsPage() {
  return (
    <>
      <PortalPageHeader
        description="Thông báo về lịch khám, kết quả và thanh toán của bạn và người thân."
        title="Thông báo"
      />
      <PatientNotificationsClient />
    </>
  );
}
