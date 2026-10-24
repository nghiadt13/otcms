"use client";

import { useState } from "react";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import { formatAppMessage } from "@/lib/messages/appMessages";
import type { IssuedCredential, StaffRole } from "../types/account";
import { AccountCreateForm } from "./AccountCreateForm";
import { IssuedPasswordStep } from "./IssuedPasswordStep";

type AccountFormDialogProps = {
  /** Gọi khi danh sách nên tải lại (đã tạo, hoặc kết quả không chắc). */
  onCreated: () => void;
  onClose: () => void;
};

type Issued = { credential: IssuedCredential; role: StaffRole };

/**
 * Tạo tài khoản nhân viên, hai bước. Phải mount có điều kiện (chỉ khi mở): đóng là gỡ
 * component nên mật khẩu một lần trong state biến mất cùng Dialog.
 */
export function AccountFormDialog({ onCreated, onClose }: AccountFormDialogProps) {
  const [issued, setIssued] = useState<Issued | null>(null);

  if (issued)
    return (
      <IssuedPasswordStep
        title="Tài khoản đã tạo"
        message={formatAppMessage("MSG-11", {
          accountRole: ROLE_LABELS[issued.role],
          username: issued.credential.username,
        })}
        credential={issued.credential}
        onDone={onClose}
      />
    );

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <AccountCreateForm
          onCancel={onClose}
          onCreated={(credential, role) => {
            setIssued({ credential, role });
            onCreated();
          }}
          onAbort={(reload) => {
            if (reload) onCreated();
            onClose();
          }}
        />
      </DialogContent>
    </Dialog>
  );
}
