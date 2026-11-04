"use client";

import { useId, useState, type FormEvent } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { notifySuccess } from "@/lib/messages/notify";
import { myAccountErrorMessage } from "../api/myAccountErrors";
import { updateMyDisplayName } from "../api/myAccountProfileApi";
import type { MyAccountAction } from "../types/myAccount";
import {
  DISPLAY_NAME_MIN as NAME_MIN,
  DISPLAY_NAME_MAX as NAME_MAX,
  displayNameLength,
  trimDisplayName,
} from "../schema/displayNameRules";

// Một dòng "Tên hiển thị" của màn tài khoản bệnh nhân (UC04). "Sửa" mở ô nhập tại chỗ;
// "Lưu" gửi cập nhật rồi tải lại tài khoản. Lỗi hiện ngay dưới ô.
export function DisplayNameRow({
  displayName,
  allowedActions = [],
  mustChangePassword = false,
}: {
  displayName: string;
  allowedActions?: readonly MyAccountAction[];
  mustChangePassword?: boolean;
}) {
  const canEdit =
    allowedActions.includes("updateDisplayName") && !mustChangePassword;
  const inputId = useId();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(displayName);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function startEditing() {
    setDraft(displayName);
    setError(null);
    setEditing(true);
  }

  function cancel() {
    setError(null);
    setEditing(false);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canEdit || pending) return;
    const name = trimDisplayName(draft);
    if (displayNameLength(name) < NAME_MIN) {
      setError(`Tên hiển thị cần ít nhất ${NAME_MIN} ký tự.`);
      return;
    }
    if (displayNameLength(name) > NAME_MAX) {
      setError(`Tên hiển thị tối đa ${NAME_MAX} ký tự.`);
      return;
    }

    setPending(true);
    setError(null);
    try {
      await updateMyDisplayName(name);
      notifySuccess("MSG-05");
      setEditing(false);
    } catch (cause) {
      setError(myAccountErrorMessage(cause));
    } finally {
      setPending(false);
    }
  }

  if (!editing || !canEdit) {
    return (
      <div className="flex items-center justify-between gap-4 px-4 py-3">
        <div className="min-w-0">
          <p className="text-sm text-muted-foreground">Tên hiển thị</p>
          <p className="truncate text-base font-medium">{displayName}</p>
        </div>
        {canEdit ? (
          <Button
            aria-label="Sửa tên hiển thị"
            variant="link"
            className="h-11 shrink-0 px-2 text-base font-semibold"
            onClick={startEditing}
          >
            Sửa
          </Button>
        ) : null}
      </div>
    );
  }

  return (
    <form className="grid gap-3 px-4 py-4" noValidate onSubmit={handleSubmit}>
      <div className="grid gap-1.5">
        <Label className="text-base" htmlFor={inputId}>
          Tên hiển thị
        </Label>
        <Input
          aria-describedby={error ? `${inputId}-error` : undefined}
          aria-invalid={error ? true : undefined}
          autoComplete="name"
          className="h-12 text-base"
          disabled={pending}
          id={inputId}
          onChange={(event) => {
            setDraft(event.target.value);
            setError(null);
          }}
          value={draft}
        />
        <FieldError id={`${inputId}-error`} message={error} />
      </div>
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button
          className="h-12 text-base"
          disabled={pending}
          onClick={cancel}
          type="button"
          variant="outline"
        >
          Hủy
        </Button>
        <Button
          className="h-12 px-5 text-base"
          disabled={pending}
          type="submit"
        >
          {pending ? "Đang lưu..." : "Lưu"}
        </Button>
      </div>
    </form>
  );
}
