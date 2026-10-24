"use client";

import Link from "next/link";
import { useState } from "react";
import { PortalPageHeader } from "@/components/patient/PortalPageHeader";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { useMyAccount } from "../hooks/useMyAccount";
import { AvatarEditor } from "./AvatarEditor";
import { DisplayNameRow } from "./DisplayNameRow";
import { EmailChangeDialog } from "./EmailChangeDialog";

// Tài khoản của bệnh nhân (/portal/account, UC03, UC04): tên hiển thị, email (đổi bằng mã OTP),
// ảnh đại diện và liên kết đổi mật khẩu. Thông tin hành chính nằm ở hồ sơ bệnh nhân (UC14).
export function PatientAccountProfile() {
  const { account, loading, error, reload } = useMyAccount();
  const [emailOpen, setEmailOpen] = useState(false);

  return (
    <>
      <PortalPageHeader
        title="Tài khoản"
        description="Tên hiển thị, email và mật khẩu dùng để đăng nhập."
      />

      {error ? (
        <div role="alert" className="mb-6 grid gap-3 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3">
          <p className="text-base text-foreground">
            Không tải được thông tin tài khoản.
          </p>
          <Button
            className="h-12 w-fit px-5 text-base"
            onClick={reload}
            variant="outline"
          >
            Thử lại
          </Button>
        </div>
      ) : null}

      {account?.kind === "PATIENT" ? (
        <div className="grid gap-8">
          <AvatarEditor account={account} large />

          <ul
            aria-label="Thông tin đăng nhập"
            className="divide-y divide-border rounded-lg border border-border"
          >
            <li>
              <DisplayNameRow displayName={account.displayName} />
            </li>
            <li className="flex items-center justify-between gap-4 px-4 py-3">
              <div className="min-w-0">
                <p className="text-sm text-muted-foreground">Email</p>
                <p className="truncate text-base font-medium">{account.email}</p>
              </div>
              <Button
                aria-label="Đổi email"
                className="h-11 shrink-0 px-2 text-base font-semibold"
                onClick={() => setEmailOpen(true)}
                variant="link"
              >
                Đổi
              </Button>
            </li>
            <li className="flex items-center justify-between gap-4 px-4 py-3">
              <div className="min-w-0">
                <p className="text-sm text-muted-foreground">Mật khẩu</p>
                <p className="text-base font-medium">••••••••</p>
              </div>
              <Link
                aria-label="Đổi mật khẩu"
                className="inline-flex h-11 shrink-0 items-center rounded-sm px-2 text-base font-semibold text-primary underline-offset-4 hover:underline"
                href={PATIENT_ROUTES.accountPassword}
              >
                Đổi
              </Link>
            </li>
          </ul>

          <p className="text-sm text-muted-foreground">
            Ngày sinh, số điện thoại và địa chỉ nằm trong hồ sơ bệnh nhân.
          </p>

          <EmailChangeDialog
            currentEmail={account.email}
            onOpenChange={setEmailOpen}
            open={emailOpen}
          />
        </div>
      ) : account?.kind === "STAFF" ? (
        <div className="grid gap-4 text-base">
          <p>Trang này dành cho tài khoản bệnh nhân.</p>
          <Link
            className="w-fit rounded-sm font-semibold text-primary underline-offset-4 hover:underline"
            href="/login"
          >
            Đăng nhập bằng tài khoản bệnh nhân
          </Link>
        </div>
      ) : loading ? (
        <div aria-hidden className="grid gap-3">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : null}
    </>
  );
}
