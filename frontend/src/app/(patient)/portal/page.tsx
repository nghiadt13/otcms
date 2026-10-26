import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { getMockCurrentActor } from "@/lib/session/mockCurrentActor";
import { PortalHomeBody } from "./_home/PortalHomeBody";

// Trang chủ khu bệnh nhân (màn #15). Trang chỉ ghép khung và thân trang; các khối nghiệp vụ được
// ghép trong `_home/` vì ESLint cấm feature import feature (xem AGENTS.md, mục Kiến trúc frontend).
export default function PatientPortalHomePage() {
  const actor = getMockCurrentActor("patient");
  return (
    <>
      <PortalPageHeader
        description="Đặt lịch, xem kết quả và thanh toán cho bản thân và người thân đã gắn."
        title={`Xin chào, ${actor.displayName}`}
      />
      <PortalHomeBody />
    </>
  );
}
