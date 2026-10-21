"use client";

import { useRouter } from "next/navigation";
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
import { ACCOUNT_TYPE_LABELS, ROLE_LABELS } from "@/lib/domain/clinicRoles";
import { formatDate } from "@/lib/format/formatDate";
import { notifyError } from "@/lib/messages/notify";
import { ADMIN_ROUTES } from "@/lib/routes/adminRoutes";
import {
  STATUS_LABELS,
  STATUS_TONES,
  accountFullName,
  accountIdentifier,
} from "../accountDisplay";
import { useAccountDetail } from "../hooks/useAccountDetail";
import { useAccountStatusChange } from "../hooks/useAccountStatusChange";
import type { AccountDetail } from "../types/account";
import { AccountReissueDialog } from "./AccountReissueDialog";
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
  const isStaff = account.accountType !== "PATIENT";
  const items = [
    isStaff
      ? { label: "Tên đăng nhập", value: account.username }
      : { label: "Email", value: account.email },
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
  ];
  if (!isStaff) return [...items, { label: "Họ tên", value: account.fullName }];
  if (!account.staffProfile)
    return [
      ...items,
      { label: "Hồ sơ nhân viên", value: "Chưa có hồ sơ nhân viên." },
    ];
  const { fullName, role, workContact } = account.staffProfile;
  return [
    ...items,
    { label: "Họ tên", value: fullName },
    { label: "Vai trò", value: ROLE_LABELS[role] },
    { label: "Liên hệ công việc", value: workContact },
  ];
}

export function AccountDetailSheet({
  accountId,
  onClose,
  onChanged,
  onStale,
}: AccountDetailSheetProps) {
  const router = useRouter();
  const { detail, loading, error, notFound, rawError, reload, setDetail } =
    useAccountDetail(accountId);
  const [reissueOpen, setReissueOpen] = useState(false);

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
            {can("reissuePassword") ? (
              <Button variant="outline" onClick={() => setReissueOpen(true)}>
                Cấp lại mật khẩu
              </Button>
            ) : null}
            {can("createStaffProfile") && detail ? (
              <Button
                variant="outline"
                onClick={() =>
                  // Chỉ đưa UUID lên URL, không đưa tên đăng nhập hay họ tên.
                  router.push(
                    `${ADMIN_ROUTES.staffProfiles}?accountId=${encodeURIComponent(detail.id)}`,
                  )
                }
              >
                Tạo hồ sơ nhân viên
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
      {reissueOpen && detail ? (
        <AccountReissueDialog
          accountId={detail.id}
          label={label}
          onClose={(shouldReload) => {
            setReissueOpen(false);
            if (shouldReload) {
              reload();
              onStale();
            }
          }}
        />
      ) : null}
    </>
  );
}
