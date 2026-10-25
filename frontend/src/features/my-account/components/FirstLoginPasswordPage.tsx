"use client";

import { useRouter } from "next/navigation";
import { ADMIN_ROUTES } from "@/lib/routes/adminRoutes";
import { ChangePasswordForm } from "./ChangePasswordForm";

/** Màn đổi mật khẩu lần đầu: dùng mật khẩu khởi tạo, không có nút Hủy. */
export function FirstLoginPasswordPage() {
  const router = useRouter();
  return (
    <div className="mx-auto w-full max-w-md">
      <h1 className="text-2xl font-semibold tracking-tight">Đặt mật khẩu mới</h1>
      <p className="mt-1 mb-6 text-sm text-muted-foreground">
        Bạn đang dùng mật khẩu khởi tạo. Hãy đặt mật khẩu mới để tiếp tục.
      </p>
      <ChangePasswordForm
        mode="forced"
        onDone={() => router.replace(ADMIN_ROUTES.dashboard)}
      />
    </div>
  );
}
