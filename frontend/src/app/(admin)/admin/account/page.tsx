import type { Metadata } from "next";
import { MyAccountProfile } from "@/features/my-account/components/MyAccountProfile";

export const metadata: Metadata = { title: "Hồ sơ tài khoản" };

export default function AdminAccountPage() {
  return <MyAccountProfile />;
}
