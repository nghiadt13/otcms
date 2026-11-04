"use client";

import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import { removeMyAvatar, updateMyAvatar } from "../api/myAccountApi";
import {
  AVATAR_ACCEPTED_TYPES,
  isAvatarFileValid,
} from "../schema/avatarRules";
import { useAccountOperation } from "../hooks/useAccountOperation";
import type { MyAccount } from "../types/myAccount";

const AVATAR_ERROR =
  "Ảnh không hợp lệ. Chọn ảnh JPEG, PNG hoặc WEBP, tối đa 2 MB.";

type AvatarEditorProps = {
  account: MyAccount;
  /** Cỡ lớn cho khu bệnh nhân: nút cao 48px, chữ 16px. */
  large?: boolean;
  /** Cấp tiêu đề "Ảnh đại diện"; dùng `h3` khi khối nằm dưới một `h2` của màn. */
  headingLevel?: "h2" | "h3";
};

/** Chọn, xem trước, lưu và gỡ ảnh đại diện (UC04). Gỡ ảnh đổi lại được nên không hỏi xác nhận. */
export function AvatarEditor(props: AvatarEditorProps) {
  return <AvatarEditorContent key={props.account.id} {...props} />;
}

function AvatarEditorContent({
  account,
  large = false,
  headingLevel: Heading = "h2",
}: AvatarEditorProps) {
  const operation = useAccountOperation();
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<"save" | "remove" | null>(null);

  const canUpdate =
    account.allowedActions.includes("updateAvatar") &&
    !account.mustChangePassword;
  const canRemove =
    account.allowedActions.includes("removeAvatar") &&
    !account.mustChangePassword;

  // Thu hồi object URL khi đổi ảnh chờ hoặc rời trang.
  useEffect(() => {
    if (!file) return;
    const url = URL.createObjectURL(file);
    // eslint-disable-next-line react-hooks/set-state-in-effect -- URL xem trước sinh từ ảnh chờ, cần thu hồi khi dọn effect
    setPreviewUrl(url);
    return () => {
      URL.revokeObjectURL(url);
      setPreviewUrl(null);
    };
  }, [file]);

  function handlePick(event: ChangeEvent<HTMLInputElement>) {
    const picked = event.target.files?.[0];
    event.target.value = "";
    if (!picked) return;
    if (!isAvatarFileValid(picked)) {
      setFile(null);
      setError(AVATAR_ERROR);
      return;
    }
    setError(null);
    setFile(picked);
  }

  function cancelPending() {
    setFile(null);
    setError(null);
  }

  async function handleSave() {
    if (!file || !canUpdate) return;
    const signal = operation.begin();
    if (!signal) return;
    setPending("save");
    try {
      await updateMyAvatar(file, signal);
      if (!operation.isCurrent()) return;
      notifySuccess("MSG-05");
      setFile(null);
      setError(null);
    } catch (cause) {
      if (!operation.isCurrent()) return;
      if (
        isApiError(cause) &&
        (cause.status === 400 || toFieldErrorMap(cause.fieldErrors).photo)
      )
        setError(AVATAR_ERROR);
      else notifyError(cause);
    } finally {
      if (operation.isCurrent()) setPending(null);
      operation.finish();
    }
  }

  async function handleRemove() {
    if (!canRemove) return;
    const signal = operation.begin();
    if (!signal) return;
    setPending("remove");
    try {
      await removeMyAvatar(signal);
      if (operation.isCurrent()) notifySuccess("MSG-05");
    } catch (cause) {
      if (operation.isCurrent()) notifyError(cause);
    } finally {
      if (operation.isCurrent()) setPending(null);
      operation.finish();
    }
  }
  const shownUrl =
    previewUrl ??
    (account.avatarUrl?.startsWith("blob:") ? account.avatarUrl : null);
  const busy = pending !== null;
  const buttonClass = large ? "h-12 px-5 text-base" : undefined;

  return (
    <section aria-labelledby="avatar-heading" className="grid gap-3">
      <Heading
        id="avatar-heading"
        className={large ? "text-lg font-semibold" : "text-base font-medium"}
      >
        Ảnh đại diện
      </Heading>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
        <Avatar className={large ? "size-20" : "size-16"}>
          {shownUrl ? (
            <AvatarImage
              src={shownUrl}
              alt={`Ảnh đại diện của ${account.displayName}`}
            />
          ) : null}
          <AvatarFallback className={large ? "text-lg" : undefined}>
            {initialsOf(account.displayName)}
          </AvatarFallback>
        </Avatar>
        <div className="flex flex-wrap items-center gap-2">
          <input
            ref={inputRef}
            type="file"
            accept={AVATAR_ACCEPTED_TYPES.join(",")}
            className="sr-only"
            tabIndex={-1}
            aria-label="Tệp ảnh đại diện"
            onChange={handlePick}
          />
          {canUpdate ? (
            <Button
              variant="outline"
              className={buttonClass}
              disabled={busy}
              onClick={() => inputRef.current?.click()}
            >
              Chọn ảnh
            </Button>
          ) : null}
          {canRemove && !file ? (
            <Button
              variant="ghost"
              className={buttonClass}
              disabled={busy}
              onClick={() => void handleRemove()}
            >
              {pending === "remove" ? "Đang gỡ..." : "Gỡ ảnh"}
            </Button>
          ) : null}
          {file ? (
            <>
              <Button
                variant="ghost"
                className={buttonClass}
                disabled={busy}
                onClick={cancelPending}
              >
                Hủy
              </Button>
              <Button
                className={buttonClass}
                disabled={busy}
                onClick={() => void handleSave()}
              >
                {pending === "save" ? "Đang lưu..." : "Lưu ảnh"}
              </Button>
            </>
          ) : null}
        </div>
      </div>
      <p
        className={
          large
            ? "text-sm text-muted-foreground"
            : "text-xs text-muted-foreground"
        }
      >
        JPEG, PNG hoặc WEBP, tối đa 2 MB.
      </p>
      <FieldError message={error} />
    </section>
  );
}

/** Chữ cái đầu của tên và họ, dùng làm ảnh đại diện tạm khi chưa có ảnh. */
function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0]?.charAt(0) ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : "";
  return (first + last).toUpperCase();
}
