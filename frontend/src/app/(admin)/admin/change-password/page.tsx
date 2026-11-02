import type { Metadata } from "next";
import { FirstLoginPasswordPage } from "@/features/my-account/components/FirstLoginPasswordPage";

export const metadata: Metadata = { title: "Đặt mật khẩu mới" };

export default function AdminChangePasswordPage() {
  return <FirstLoginPasswordPage />;
}
