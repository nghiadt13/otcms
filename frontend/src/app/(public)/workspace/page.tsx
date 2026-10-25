import type { Metadata } from "next";
import { RoleHomeRedirect } from "@/features/my-account/components/RoleHomeRedirect";

export const metadata: Metadata = { title: "Khu làm việc | OTCMS" };

// Lối vào chung cũ `/workspace` (thay màn chọn khu tạm): chuyển tới khu đúng vai trò của tài
// khoản đang đăng nhập, bệnh nhân vào /portal, nhân viên vào /reception, quản trị vào /admin.
export default function WorkspacePage() {
  return <RoleHomeRedirect />;
}
