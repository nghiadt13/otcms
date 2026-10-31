"use client";

import { useCurrentAccount } from "@/hooks/useCurrentAccount";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { Button } from "@/components/ui/button";

export default function StaffHomePage() {
  const account = useCurrentAccount();
  const router = useRouter();
  useEffect(() => {
    if (account.status === "anonymous") router.replace("/staff-login");
  }, [account.status, router]);
  if (account.status === "loading" || account.status === "anonymous") return null;
  if (account.status === "error") return <main className="mx-auto max-w-2xl p-8">
    <p role="alert">Không tải được tài khoản đang đăng nhập.</p>
    <Button className="mt-4" variant="outline" onClick={account.reload}>Thử lại</Button>
  </main>;
  return <main className="mx-auto max-w-2xl p-8">
    <h1 className="text-2xl font-semibold">Khu làm việc nhân viên</h1>
    <p className="mt-3 text-muted-foreground">
      {account.account.displayName} đã đăng nhập.
      Các màn nghiệp vụ theo vai trò sẽ được mở tại đây.
    </p>
  </main>;
}
