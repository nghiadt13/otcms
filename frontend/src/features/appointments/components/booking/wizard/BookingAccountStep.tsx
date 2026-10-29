"use client";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import type { CurrentAccountState } from "@/hooks/useCurrentAccount";

// Bước 1 (đã đăng nhập): cho người dùng thấy lịch sẽ đặt dưới tài khoản nào. Wizard mở thẳng ở bước 2;
// bước này chỉ xem lại được qua thanh tiến trình. Khách chưa đăng nhập dùng BookingSignInPrompt.

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0]?.charAt(0) ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : "";
  return (first + last).toUpperCase();
}

export function BookingAccountStep({
  account,
}: {
  account: CurrentAccountState;
}) {
  return (
    <div className="grid gap-4">
      {account.status === "loading" ? (
        <Skeleton className="h-20 w-full rounded-xl" aria-hidden />
      ) : account.status === "signedIn" ? (
        <div className="flex items-center gap-4 rounded-xl border bg-secondary/60 p-4">
          <Avatar className="size-12">
            <AvatarFallback className="bg-primary text-base font-semibold text-primary-foreground">
              {initialsOf(account.account.displayName)}
            </AvatarFallback>
          </Avatar>
          <div className="min-w-0">
            <p className="truncate text-base font-semibold">
              {account.account.displayName}
            </p>
            <p className="truncate text-sm text-muted-foreground">
              {account.account.email}
            </p>
          </div>
        </div>
      ) : (
        <p role="status" className="text-base text-muted-foreground">
          Không đọc được thông tin tài khoản lúc này. Bạn vẫn đặt lịch được bình
          thường.
        </p>
      )}
      <p className="text-base text-muted-foreground">
        Lịch hẹn sẽ lưu trong tài khoản này. Bạn có thể đặt cho bản thân hoặc
        người thân đã gắn hồ sơ, ở bước tiếp theo.
      </p>
    </div>
  );
}
