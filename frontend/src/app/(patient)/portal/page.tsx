"use client";

import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { useCurrentPatientAccount } from "@/hooks/useCurrentPatientAccount";
import { PortalHomeBody } from "./_home/PortalHomeBody";

// Trang chủ khu bệnh nhân (màn #15). Trang chỉ ghép khung và thân trang; các khối nghiệp vụ được
// ghép trong `_home/` vì ESLint cấm feature import feature (xem AGENTS.md, mục Kiến trúc frontend).
export default function PatientPortalHomePage() {
  const { account } = useCurrentPatientAccount();
  return (
    <>
      <PortalPageHeader
        description="Đặt lịch, xem kết quả và thanh toán cho bản thân và người thân đã gắn."
        title={account?.displayName ? `Xin chào, ${account.displayName}` : "Xin chào"}
      />
      <PortalHomeBody />
    </>
  );
}

