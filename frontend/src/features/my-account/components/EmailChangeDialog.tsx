"use client";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useState } from "react";
import { EmailAddressStep } from "./EmailAddressStep";
import { EmailCodeStep } from "./EmailCodeStep";

type EmailChangeDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  currentEmail: string;
};

export function EmailChangeDialog({
  open,
  onOpenChange,
  currentEmail,
}: EmailChangeDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Đổi email</DialogTitle>
          <DialogDescription>
            Email hiện tại vẫn dùng được đến khi xác minh thành công email mới.
          </DialogDescription>
        </DialogHeader>
        {open ? (
          <EmailChangeFlow
            currentEmail={currentEmail}
            onDone={() => onOpenChange(false)}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

/** Đóng dialog unmount toàn bộ flow, xóa email dự kiến và mã OTP trong state. */
function EmailChangeFlow({
  currentEmail,
  onDone,
}: {
  currentEmail: string;
  onDone: () => void;
}) {
  const [newEmail, setNewEmail] = useState<string | null>(null);
  return newEmail === null ? (
    <EmailAddressStep
      currentEmail={currentEmail}
      onSent={setNewEmail}
      onCancel={onDone}
    />
  ) : (
    <EmailCodeStep
      email={newEmail}
      onBack={() => setNewEmail(null)}
      onVerified={onDone}
    />
  );
}
