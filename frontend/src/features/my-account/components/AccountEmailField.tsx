"use client";

import { useId, useState } from "react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { EmailChangeDialog } from "./EmailChangeDialog";
import { useMyAccount } from "../hooks/useMyAccount";

/** Prop cũ vẫn được chấp nhận; dữ liệu và quyền chính thức luôn từ /me. */
export function AccountEmailField({ email }: { email: string }) {
  const id = useId();
  const [openFor, setOpenFor] = useState<string | null>(null);
  const { account } = useMyAccount();
  if (!account || account.kind !== "PATIENT") return null;
  const officialEmail = email === account.email ? email : account.email;
  const canChange =
    account.allowedActions.includes("changeEmail") &&
    !account.mustChangePassword;
  return (
    <div className="grid gap-2">
      <Label className="text-base" id={`${id}-label`}>
        Email đăng nhập
      </Label>
      <div className="flex h-12 items-center justify-between gap-2 rounded-lg border border-input bg-muted/40 pr-1 pl-3">
        <p
          aria-labelledby={`${id}-label`}
          className="min-w-0 truncate text-base"
          title={officialEmail}
        >
          {officialEmail}
        </p>
        {canChange ? (
          <Button
            aria-label="Đổi email"
            className="h-10 shrink-0 px-3 text-base font-semibold"
            onClick={() => setOpenFor(account.id)}
            type="button"
            variant="link"
          >
            Đổi
          </Button>
        ) : null}
      </div>
      <p className="text-sm text-muted-foreground">
        Email hiện tại vẫn dùng được đến khi email mới được xác minh bằng OTP.
      </p>
      {canChange ? (
        <EmailChangeDialog
          key={account.id}
          currentEmail={officialEmail}
          onOpenChange={(next) => setOpenFor(next ? account.id : null)}
          open={openFor === account.id}
        />
      ) : null}
    </div>
  );
}
