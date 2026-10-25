"use client";

import { useCallback, useState } from "react";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { activateAccount, deactivateAccount } from "../api/accountApi";
import type { AccountDetail } from "../types/account";

export type StatusAction = "activate" | "deactivate";

export type StatusTarget = {
  id: string;
  /** Tên hiển thị trong hộp xác nhận (họ tên hoặc định danh). */
  label: string;
  action: StatusAction;
};

const STATUS_TEXT: Record<StatusAction, string> = {
  deactivate: "Đã vô hiệu hóa",
  activate: "Đang hoạt động",
};

/**
 * Vô hiệu hóa / kích hoạt dùng chung cho dòng bảng và Sheet. Thành công: toast + `onChanged`
 * với bản ghi mới. Thất bại: toast theo mã lỗi rồi `onStale` để tải lại bản ghi (dữ liệu có
 * thể đã đổi). Dialog đóng cả khi lỗi vì tải lại mới là bước tiếp theo đúng.
 */
export function useAccountStatusChange({
  onChanged,
  onStale,
}: {
  onChanged: (account: AccountDetail) => void;
  onStale: (id: string) => void;
}) {
  const [target, setTarget] = useState<StatusTarget | null>(null);

  const close = useCallback(() => setTarget(null), []);

  const confirm = useCallback(async () => {
    if (!target) return;
    const call = target.action === "activate" ? activateAccount : deactivateAccount;
    try {
      const updated = await call(target.id);
      notifySuccess(
        `Trạng thái tài khoản đã được cập nhật thành ${STATUS_TEXT[target.action]}.`,
      );
      onChanged(updated);
    } catch (error) {
      notifyError(error);
      onStale(target.id);
    }
  }, [target, onChanged, onStale]);

  return { target, request: setTarget, close, confirm };
}
