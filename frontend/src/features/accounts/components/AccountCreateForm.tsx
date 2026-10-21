"use client";

import { Shuffle } from "lucide-react";
import { useState, type FormEvent } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import {
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import { ROLE_LABELS, STAFF_ROLES } from "@/lib/domain/clinicRoles";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifyError } from "@/lib/messages/notify";
import { createAccount } from "../api/accountApi";
import {
  PASSWORD_HINT,
  USERNAME_HINT,
  generateInitialPassword,
  validateInitialPassword,
  validateUsername,
} from "../schema/accountFieldRules";
import type { IssuedCredential, StaffRole } from "../types/account";

type FieldName = "role" | "username" | "initialPassword";
type FieldErrors = Partial<Record<FieldName, string>>;

type AccountCreateFormProps = {
  onCancel: () => void;
  onCreated: (credential: IssuedCredential, role: StaffRole) => void;
  /** Đóng form khi lỗi không thể sửa tại chỗ (403, 500); `reload` = nên tải lại danh sách. */
  onAbort: (reload: boolean) => void;
};

/** Bước 1: vai trò, tên đăng nhập, mật khẩu khởi tạo. Chỉ tạo nhân viên, không có email. */
export function AccountCreateForm({
  onCancel,
  onCreated,
  onAbort,
}: AccountCreateFormProps) {
  const [role, setRole] = useState<StaffRole>("RECEPTIONIST");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [pending, setPending] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const local: FieldErrors = {
      username: validateUsername(username) ?? undefined,
      initialPassword: validateInitialPassword(password) ?? undefined,
    };
    setErrors(local);
    if (local.username || local.initialPassword) return;

    setPending(true);
    try {
      const credential = await createAccount({
        role,
        username: username.trim(),
        initialPassword: password,
      });
      onCreated(credential, role);
    } catch (error) {
      handleError(error);
      setPending(false);
    }
  }

  function handleError(error: unknown) {
    const status = isApiError(error) ? error.status : 500;
    if (status === 400 || status === 409) {
      const map = toFieldErrorMap(
        isApiError(error) ? error.fieldErrors : undefined,
      );
      const next: FieldErrors = {};
      for (const [field, code] of Object.entries(map))
        next[field as FieldName] = formatAppMessage(code);
      setErrors(next);
      // 409 trùng tên: giữ form để đổi tên; có toast kèm lỗi đỏ dưới ô.
      if (status === 409) notifyError(error);
      return;
    }
    // 403: đóng. 500/mạng: kết quả không chắc, đóng và tải lại, không tự gửi lại.
    notifyError(error);
    onAbort(status !== 403);
  }

  return (
    <form
      onSubmit={(event) => void submit(event)}
      noValidate
      className="grid gap-4"
    >
      <DialogHeader>
        <DialogTitle>Tạo tài khoản nhân viên</DialogTitle>
        <DialogDescription>
          Nhân viên đăng nhập bằng tên đăng nhập và mật khẩu, không dùng email.
        </DialogDescription>
      </DialogHeader>

      <div
        className="grid gap-2"
        role="group"
        aria-labelledby="account-role-label"
      >
        <Label id="account-role-label">Vai trò *</Label>
        <RadioGroup
          value={role}
          onValueChange={(value) => setRole(value as StaffRole)}
          disabled={pending}
          className="gap-2.5"
        >
          {STAFF_ROLES.map((staffRole) => (
            <Label key={staffRole} className="font-normal">
              <RadioGroupItem value={staffRole} />
              {ROLE_LABELS[staffRole]}
            </Label>
          ))}
        </RadioGroup>
        <FieldError message={errors.role} />
      </div>

      <div className="grid gap-1.5">
        <Label htmlFor="account-username">Tên đăng nhập *</Label>
        <Input
          id="account-username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          disabled={pending}
          autoComplete="off"
          autoCapitalize="none"
          spellCheck={false}
          aria-invalid={Boolean(errors.username)}
          aria-describedby="account-username-hint"
        />
        <p id="account-username-hint" className="text-xs text-muted-foreground">
          {USERNAME_HINT}
        </p>
        <FieldError message={errors.username} />
      </div>

      <div className="grid gap-1.5">
        <Label htmlFor="account-password">Mật khẩu khởi tạo *</Label>
        <div className="flex gap-2">
          <Input
            id="account-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={pending}
            autoComplete="off"
            spellCheck={false}
            aria-invalid={Boolean(errors.initialPassword)}
            aria-describedby="account-password-hint"
            className="font-mono"
          />
          <Button
            type="button"
            variant="ghost"
            disabled={pending}
            onClick={() => setPassword(generateInitialPassword())}
          >
            <Shuffle data-icon="inline-start" />
            Tạo ngẫu nhiên
          </Button>
        </div>
        <p id="account-password-hint" className="text-xs text-muted-foreground">
          {PASSWORD_HINT}
        </p>
        <FieldError message={errors.initialPassword} />
      </div>

      <DialogFooter>
        <Button
          type="button"
          variant="outline"
          disabled={pending}
          onClick={onCancel}
        >
          Hủy
        </Button>
        <Button type="submit" disabled={pending}>
          {pending ? "Đang tạo..." : "Tạo tài khoản"}
        </Button>
      </DialogFooter>
    </form>
  );
}
