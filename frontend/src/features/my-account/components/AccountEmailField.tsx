import { useId } from "react";
import { Label } from "@/components/ui/label";

// Email đăng nhập của bệnh nhân: ô chỉ đọc cùng cỡ với các ô nhập quanh nó. Email không đổi được
// trên web (muốn đổi phải liên hệ phòng khám), nên không có nút sửa.
export function AccountEmailField({ email }: { email: string }) {
  const id = useId();

  return (
    <div className="grid gap-2">
      <Label className="text-base" id={`${id}-label`}>
        Email đăng nhập
      </Label>
      <div className="flex h-12 items-center rounded-lg border border-input bg-muted/40 px-3">
        <p
          aria-labelledby={`${id}-label`}
          className="min-w-0 truncate text-base"
          title={email}
        >
          {email}
        </p>
      </div>
    </div>
  );
}
