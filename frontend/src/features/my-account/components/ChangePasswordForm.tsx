"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { changeMyPassword } from "../api/myAccountApi";
import {
  passwordMessages,
  validateConfirmPassword,
  validateCurrentPassword,
  validateNewPassword,
} from "../schema/passwordFieldRules";
import { PasswordInputField } from "./PasswordInputField";
import { PasswordRequirementList } from "./PasswordRequirementList";

type Field = "current" | "next" | "confirm";
type Errors = Partial<Record<Field, string>>;

type ChangePasswordFormProps = {
  /** voluntary: có nút Hủy. forced: đổi lần đầu, không có Hủy. */
  mode: "voluntary" | "forced";
  onDone: () => void;
  onCancel?: () => void;
  /** Cỡ lớn cho khu bệnh nhân: ô và nút cao 48px, chữ 16px. */
  large?: boolean;
};

/** Form đổi mật khẩu dùng chung cho Dialog, trang đổi lần đầu và trang đổi của bệnh nhân. */
export function ChangePasswordForm({
  mode,
  onDone,
  onCancel,
  large = false,
}: ChangePasswordFormProps) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [pending, setPending] = useState(false);

  const validators: Record<Field, () => string | null> = {
    current: () => validateCurrentPassword(current),
    next: () => validateNewPassword(next),
    confirm: () => validateConfirmPassword(next, confirm),
  };

  // Báo lỗi khi rời ô; xóa lỗi cũ của ô khi người dùng sửa lại.
  const touch = (field: Field) =>
    setErrors((prev) => ({ ...prev, [field]: validators[field]() ?? undefined }));
  const clear = (field: Field) =>
    setErrors((prev) => ({ ...prev, [field]: undefined }));

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const found: Errors = {
      current: validators.current() ?? undefined,
      next: validators.next() ?? undefined,
      confirm: validators.confirm() ?? undefined,
    };
    setErrors(found);
    if (Object.values(found).some(Boolean)) return;

    setPending(true);
    try {
      await changeMyPassword({ currentPassword: current, newPassword: next });
      notifySuccess("Mật khẩu đã được thay đổi.");
      // Xóa mật khẩu khỏi state ngay khi xong.
      setCurrent("");
      setNext("");
      setConfirm("");
      onDone();
    } catch (err) {
      const fields = isApiError(err) ? toFieldErrorMap(err.fieldErrors) : {};
      if (fields.currentPassword || fields.newPassword) {
        setErrors({
          current: fields.currentPassword
            ? passwordMessages.currentWrong
            : undefined,
          next: fields.newPassword ? passwordMessages.newInvalid : undefined,
        });
      } else {
        notifyError(err);
      }
    } finally {
      setPending(false);
    }
  }

  const buttonClass = large ? "h-12 px-5 text-base" : undefined;

  return (
    <form onSubmit={handleSubmit} noValidate className="grid gap-4">
      <PasswordInputField
        label="Mật khẩu hiện tại"
        autoComplete="current-password"
        value={current}
        disabled={pending}
        error={errors.current}
        large={large}
        onChange={(value) => {
          setCurrent(value);
          clear("current");
        }}
        onBlur={() => touch("current")}
      />
      <PasswordInputField
        label="Mật khẩu mới"
        autoComplete="new-password"
        value={next}
        disabled={pending}
        error={errors.next}
        large={large}
        hint={<PasswordRequirementList value={next} />}
        onChange={(value) => {
          setNext(value);
          clear("next");
        }}
        onBlur={() => touch("next")}
      />
      <PasswordInputField
        label="Nhập lại mật khẩu mới"
        autoComplete="new-password"
        value={confirm}
        disabled={pending}
        error={errors.confirm}
        large={large}
        onChange={(value) => {
          setConfirm(value);
          clear("confirm");
        }}
        onBlur={() => touch("confirm")}
      />
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        {mode === "voluntary" ? (
          <Button
            type="button"
            variant="outline"
            className={buttonClass}
            disabled={pending}
            onClick={onCancel}
          >
            Hủy
          </Button>
        ) : null}
        <Button type="submit" className={buttonClass} disabled={pending}>
          {pending ? "Đang lưu..." : "Đổi mật khẩu"}
        </Button>
      </div>
    </form>
  );
}
