"use client";

import { useState } from "react";
import { DetailList } from "@/components/admin/DetailList";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import { useMyAccount } from "../hooks/useMyAccount";
import { AvatarEditor } from "./AvatarEditor";
import { ChangePasswordDialog } from "./ChangePasswordDialog";

const STATUS_VIEW = {
  ACTIVE: { tone: "success", label: "Đang hoạt động" },
  INACTIVE: { tone: "neutral", label: "Đã vô hiệu hóa" },
} as const;

/** Trang /admin/account: tên đăng nhập, vai trò, trạng thái và ảnh đại diện. */
export function MyAccountProfile() {
  const { account, loading, error, reload } = useMyAccount();
  const [passwordOpen, setPasswordOpen] = useState(false);

  const canChangePassword = account?.allowedActions.includes("changePassword");

  return (
    <div className="max-w-2xl">
      <PageHeader
        title="Hồ sơ tài khoản"
        description="Tên đăng nhập, vai trò và ảnh đại diện của bạn."
        actions={
          canChangePassword ? (
            <Button variant="outline" onClick={() => setPasswordOpen(true)}>
              Đổi mật khẩu
            </Button>
          ) : null
        }
      />

      {error ? (
        <div role="alert" className="mb-4 text-sm text-destructive">
          <p>{error}</p>
          <Button variant="outline" size="sm" className="mt-2" onClick={reload}>
            Thử lại
          </Button>
        </div>
      ) : null}

      {account ? (
        <div className="grid gap-6">
          <DetailList
            items={[
              { label: "Tên đăng nhập", value: account.username },
              { label: "Vai trò", value: ROLE_LABELS[account.role] },
              {
                label: "Trạng thái",
                value: (
                  <StatusBadge tone={STATUS_VIEW[account.status].tone}>
                    {STATUS_VIEW[account.status].label}
                  </StatusBadge>
                ),
              },
            ]}
          />
          <Separator />
          <AvatarEditor account={account} />
        </div>
      ) : loading ? (
        <div className="grid gap-6" aria-hidden>
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-20 w-full" />
        </div>
      ) : null}

      <ChangePasswordDialog
        open={passwordOpen}
        onOpenChange={setPasswordOpen}
      />
    </div>
  );
}
