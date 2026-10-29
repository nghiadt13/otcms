"use client";

import { useEffect, useState } from "react";
import { DetailList } from "@/components/admin/DetailList";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { ACCOUNT_TYPE_LABELS } from "@/lib/domain/clinicRoles";
import { formatDate } from "@/lib/format/formatDate";
import { notifyError } from "@/lib/messages/notify";
import {
  STATUS_LABELS,
  STATUS_TONES,
  accountFullName,
  accountIdentifier,
} from "../accountDisplay";
import { useAccountDetail } from "../hooks/useAccountDetail";
import { useAccountStatusChange } from "../hooks/useAccountStatusChange";
import type { AccountDetail } from "../types/account";
import { AccountStatusDialog } from "./AccountStatusDialog";

type AccountDetailSheetProps = {
  accountId: string;
  onClose: () => void;
  /** Bản ghi mới sau khi đổi trạng thái, để danh sách cập nhật dòng. */
  onChanged: (account: AccountDetail) => void;
  /** Dữ liệu trong danh sách có thể đã cũ (lỗi 404/409/500): tải lại danh sách. */
  onStale: () => void;
};

function detailItems(account: AccountDetail) {
  return [
    { label: "Email", value: account.email },
    {
      label: "Loại tài khoản",
      value: ACCOUNT_TYPE_LABELS[account.accountType],
    },
    {
      label: "Trạng thái",
      value: (
        <StatusBadge tone={STATUS_TONES[account.status]}>
          {STATUS_LABELS[account.status]}
        </StatusBadge>
      ),
    },
    {
      label: "Ngày tạo",
      value: (
        <span className="tabular-nums">{formatDate(account.createdAt)}</span>
      ),
    },
    { label: "Họ tên", value: account.fullName },
  ];
}

export function AccountDetailSheet({
  accountId,
  onClose,
  onChanged,
  onStale,
}: AccountDetailSheetProps) {
  const { detail, loading, error, notFound, rawError, reload, setDetail } =
    useAccountDetail(accountId);

  const status = useAccountStatusChange({
    onChanged: (updated) => {
      setDetail(updated);
      onChanged(updated);
    },
    onStale: () => {
      reload();
      onStale();
    },
  });

  // 404: tài khoản không còn. Báo, đóng Sheet và tải lại danh sách.
  useEffect(() => {
    if (!notFound) return;
    notifyError(rawError);
    onStale();
    onClose();
  }, [notFound, rawError, onStale, onClose]);

  const can = (action: AccountDetail["allowedActions"][number]) =>
    detail?.allowedActions.includes(action) ?? false;
  const label = detail ? (detail.fullName ?? accountIdentifier(detail)) : "";

  return (
    <>
      <Sheet open onOpenChange={(open) => !open && onClose()}>
        <SheetContent className="w-full data-[side=right]:w-full data-[side=right]:sm:max-w-md">
          <SheetHeader>
            <SheetTitle>
              {detail ? accountFullName(detail) : "Chi tiết tài khoản"}
            </SheetTitle>
            <SheetDescription>
              {detail
                ? `${ACCOUNT_TYPE_LABELS[detail.accountType]} · ${STATUS_LABELS[detail.status]}`
                : "Thông tin tài khoản, chỉ đọc."}
            </SheetDescription>
          </SheetHeader>

          <div className="flex-1 overflow-y-auto px-4">
            {loading ? (
              <div className="grid gap-4" aria-busy="true">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            ) : error ? (
              <div className="grid justify-items-start gap-3">
                <p role="alert" className="text-sm text-destructive">
                  {error}
                </p>
                <Button variant="outline" size="sm" onClick={reload}>
                  Thử lại
                </Button>
              </div>
            ) : detail ? (
              <DetailList items={detailItems(detail)} />
            ) : null}
          </div>

          <SheetFooter className="sm:flex-row sm:flex-wrap sm:justify-end">
            {can("deactivate") && detail ? (
              <Button
                variant="outline"
                onClick={() =>
                  status.request({ id: detail.id, label, action: "deactivate" })
                }
              >
                Vô hiệu hóa
              </Button>
            ) : null}
            {can("activate") && detail ? (
              <Button
                variant="outline"
                onClick={() =>
                  status.request({ id: detail.id, label, action: "activate" })
                }
              >
                Kích hoạt
              </Button>
            ) : null}
            <Button variant="outline" onClick={onClose}>
              Đóng
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>

      <AccountStatusDialog
        target={status.target}
        onClose={status.close}
        onConfirm={status.confirm}
      />
    </>
  );
}
