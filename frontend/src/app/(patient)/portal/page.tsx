import { PortalHomeBody } from "./_home/PortalHomeBody";
import { PortalHomeHeader } from "./_home/PortalHomeHeader";

// Trang chủ khu bệnh nhân (màn #15). Trang chỉ ghép khung và thân trang; các khối nghiệp vụ được
// ghép trong `_home/` vì ESLint cấm feature import feature (xem AGENTS.md, mục Kiến trúc frontend).
export default function PatientPortalHomePage() {
  return (
    <>
      <PortalHomeHeader />
      <PortalHomeBody />
    </>
  );
}
