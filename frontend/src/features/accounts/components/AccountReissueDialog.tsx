"use client";

import { useRef, useState } from "react";
import { ConfirmActionDialog } from "@/components/admin/ConfirmActionDialog";
import { notifyError } from "@/lib/messages/notify";
import { reissueAccountPassword } from "../api/accountApi";
import type { IssuedCredential } from "../types/account";
import { IssuedPasswordStep } from "./IssuedPasswordStep";

type AccountReissueDialogProps = {
  accountId: string;
  label: string;
  /** Đóng hết (kể cả khi lỗi); `reload` = true khi nên tải lại bản ghi. */
  onClose: (reload: boolean) => void;
};

/**
 * Cấp lại mật khẩu cho nhân viên: xác nhận (mật khẩu cũ mất hiệu lực) rồi hiện mật khẩu
 * mới một lần. Lỗi: toast, không tự thử lại (lần cấp thứ hai làm mật khẩu đã đưa mất hiệu lực).
 */
export function AccountReissueDialog({
  accountId,
  label,
  onClose,
}: AccountReissueDialogProps) {
  const [credential, setCredential] = useState<IssuedCredential | null>(null);

  // ConfirmActionDialog tự gọi onOpenChange(false) sau khi onConfirm xong; cờ này báo
  // rằng kết quả đã được xử lý (sang bước kết quả hoặc đã đóng vì lỗi) nên bỏ qua lần đó.
  const settled = useRef(false);

  async function reissue() {
    try {
      const issued = await reissueAccountPassword(accountId);
      settled.current = true;
      setCredential(issued);
    } catch (error) {
      settled.current = true;
      notifyError(error);
      onClose(true);
    }
  }

  if (credential)
    return (
      <IssuedPasswordStep
        title="Mật khẩu mới"
        message={`Đã cấp lại mật khẩu cho ${credential.username}. Mật khẩu cũ không còn dùng được. Nhân viên phải đổi mật khẩu ở lần đăng nhập kế.`}
        credential={credential}
        onDone={() => onClose(false)}
      />
    );

  return (
    <ConfirmActionDialog
      open
      onOpenChange={(open) => {
        if (!open && !settled.current) onClose(false);
      }}
      title="Cấp lại mật khẩu?"
      description={`Mật khẩu hiện tại của ${label} sẽ bị thay. Nhân viên phải đổi mật khẩu ở lần đăng nhập kế.`}
      confirmLabel="Cấp lại mật khẩu"
      pendingLabel="Đang cấp lại..."
      onConfirm={reissue}
    />
  );
}
