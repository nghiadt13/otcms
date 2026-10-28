"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { EmailAddressStep } from "./EmailAddressStep";
import { EmailCodeStep } from "./EmailCodeStep";

type EmailChangeDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Email đang dùng; vẫn hiệu lực tới khi email mới được xác minh (BR-05). */
  currentEmail: string;
};

// Đổi email (UC04) trong hộp thoại hai bước: nhập email mới, rồi nhập mã gửi tới email đó.
// Đang gửi hoặc xác minh thì không đóng được hộp thoại. Các bước chỉ mount khi hộp thoại mở,
// nên đóng lại là xóa sạch ô nhập (như đổi mật khẩu).
export function EmailChangeDialog({
  open,
  onOpenChange,
  currentEmail,
}: EmailChangeDialogProps) {
  const [busy, setBusy] = useState(false);

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!busy) onOpenChange(next);
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Đổi email</DialogTitle>
          <DialogDescription>
            Mã xác minh gửi tới email mới. Đến khi xác minh xong, email hiện tại
            vẫn dùng được.
          </DialogDescription>
        </DialogHeader>
        <EmailChangeFlow
          currentEmail={currentEmail}
          onBusyChange={setBusy}
          onDone={() => onOpenChange(false)}
        />
      </DialogContent>
    </Dialog>
  );
}

// Chọn bước: chưa có email mới thì hiện bước 1; đã gửi mã thì hiện bước 2.
function EmailChangeFlow({
  currentEmail,
  onBusyChange,
  onDone,
}: {
  currentEmail: string;
  onBusyChange: (busy: boolean) => void;
  onDone: () => void;
}) {
  const [newEmail, setNewEmail] = useState<string | null>(null);

  if (newEmail === null) {
    return (
      <EmailAddressStep
        currentEmail={currentEmail}
        onBusyChange={onBusyChange}
        onSent={setNewEmail}
        onCancel={onDone}
      />
    );
  }
  return (
    <EmailCodeStep
      email={newEmail}
      onBusyChange={onBusyChange}
      onBack={() => setNewEmail(null)}
      onVerified={onDone}
    />
  );
}
