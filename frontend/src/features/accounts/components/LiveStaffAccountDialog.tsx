"use client";

import { useState, type FormEvent } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import {
  ROLE_LABELS,
  STAFF_ROLES,
  type StaffRole,
} from "@/lib/domain/clinicRoles";
import { messageFromError } from "@/lib/messages/appMessages";
import { createLiveStaffAccount } from "../api/accountLiveApi";
import { IssuedPasswordStep } from "./IssuedPasswordStep";

export function LiveStaffAccountDialog({
  onCreated,
  onClose,
}: {
  onCreated: () => void;
  onClose: () => void;
}) {
  const [email, setEmail] = useState("");
  const [fullName, setFullName] = useState("");
  const [role, setRole] = useState<StaffRole>("RECEPTIONIST");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [issued, setIssued] = useState<{
    id: string;
    username: string;
    initialPassword: string;
  } | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      const result = await createLiveStaffAccount({
        email: email.trim(),
        fullName: fullName.trim(),
        role,
      });
      setIssued({
        id: result.accountId,
        username: result.email,
        initialPassword: result.temporaryPassword,
      });
      onCreated();
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setPending(false);
    }
  }

  if (issued)
    return (
      <IssuedPasswordStep
        title="Tài khoản đã tạo"
        message="Mật khẩu tạm chỉ hiển thị một lần. Hãy bàn giao trực tiếp cho nhân viên."
        credential={issued}
        onDone={onClose}
      />
    );

  return (
    <Dialog open onOpenChange={(open) => !open && !pending && onClose()}>
      <DialogContent className="sm:max-w-md">
        <form className="grid gap-4" onSubmit={(event) => void submit(event)}>
          <DialogHeader>
            <DialogTitle>Tạo tài khoản nhân viên</DialogTitle>
            <DialogDescription>
              Backend cấp mật khẩu tạm sau khi tạo tài khoản.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-2">
            <Label htmlFor="staff-account-email">Email *</Label>
            <Input
              id="staff-account-email"
              type="email"
              required
              maxLength={254}
              autoComplete="off"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              disabled={pending}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="staff-account-name">Họ tên *</Label>
            <Input
              id="staff-account-name"
              required
              maxLength={100}
              value={fullName}
              onChange={(event) => setFullName(event.target.value)}
              disabled={pending}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="staff-account-role">Vai trò *</Label>
            <NativeSelect
              id="staff-account-role"
              value={role}
              onChange={(event) => setRole(event.target.value as StaffRole)}
              disabled={pending}
            >
              {STAFF_ROLES.map((item) => (
                <NativeSelectOption key={item} value={item}>
                  {ROLE_LABELS[item]}
                </NativeSelectOption>
              ))}
            </NativeSelect>
          </div>
          {error ? (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          ) : null}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              disabled={pending}
            >
              Hủy
            </Button>
            <Button type="submit" disabled={pending}>
              {pending ? "Đang tạo..." : "Tạo tài khoản"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
