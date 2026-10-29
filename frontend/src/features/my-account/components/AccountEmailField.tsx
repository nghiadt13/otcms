"use client";

import { useId, useState } from "react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { EmailChangeDialog } from "./EmailChangeDialog";

// Email đăng nhập của bệnh nhân (UC04, BR-05): hiện như một ô chỉ đọc cùng cỡ với các ô nhập
// quanh nó; sửa bằng nút "Đổi" mở hộp thoại OTP gửi tới email mới. Email mới chỉ có hiệu lực
// sau khi xác minh nên không có ô nhập trực tiếp ở đây.
export function AccountEmailField({ email }: { email: string }) {
  const id = useId();
  const [open, setOpen] = useState(false);

  return (
    <div className="grid gap-2">
      <Label className="text-base" id={`${id}-label`}>
        Email đăng nhập
      </Label>
      <div className="flex h-12 items-center justify-between gap-2 rounded-lg border border-input bg-muted/40 pr-1 pl-3">
        <p
          aria-labelledby={`${id}-label`}
          className="min-w-0 truncate text-base"
          title={email}
        >
          {email}
        </p>
        <Button
          aria-label="Đổi email"
          className="h-10 shrink-0 px-3 text-base font-semibold"
          onClick={() => setOpen(true)}
          type="button"
          variant="link"
        >
          Đổi
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">
        Mã xác minh gửi tới email mới. Đến khi xác minh xong, email hiện tại vẫn
        dùng được.
      </p>
      <EmailChangeDialog
        currentEmail={email}
        onOpenChange={setOpen}
        open={open}
      />
    </div>
  );
}
