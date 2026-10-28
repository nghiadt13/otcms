"use client";

import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { useCurrentAccount } from "@/hooks/useCurrentAccount";

// Tiêu đề trang chủ khu bệnh nhân: lời chào theo tên từ backend (`GET /api/v1/me`). Cổng đăng
// nhập của layout đã xác nhận tài khoản trước khi vẽ, nên chữ "Xin chào" không nháy tên giả.
export function PortalHomeHeader() {
  const current = useCurrentAccount();
  const name =
    current.status === "signedIn" ? `, ${current.account.displayName}` : "";
  return (
    <PortalPageHeader
      description="Đặt lịch, xem kết quả và thanh toán cho bản thân và người thân đã gắn."
      title={`Xin chào${name}`}
    />
  );
}
