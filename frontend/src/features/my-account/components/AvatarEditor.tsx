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
  AVATAR_MAX_BYTES,
} from "../api/myAccountMockData";
import { emitMyAccountChanged } from "../hooks/useMyAccount";
import type { MyAccount } from "../types/myAccount";

const AVATAR_ERROR =
  "Ảnh không hợp lệ. Chọn ảnh JPEG, PNG hoặc WEBP, tối đa 2 MB.";

type AvatarEditorProps = {
  account: MyAccount;
};

/** Chọn, xem trước, lưu và gỡ ảnh đại diện (UC04). Gỡ ảnh đổi lại được nên không hỏi xác nhận. */
export function AvatarEditor({ account }: AvatarEditorProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState<"save" | "remove" | null>(null);

  const canUpdate = account.allowedActions.includes("updateAvatar");
  const canRemove = account.allowedActions.includes("removeAvatar");

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
    if (!AVATAR_ACCEPTED_TYPES.includes(picked.type) || picked.size > AVATAR_MAX_BYTES) {
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
    if (!file) return;
    setPending("save");
    try {
      await updateMyAvatar(file);
      notifySuccess("Thông tin tài khoản đã được cập nhật.");
      setFile(null);
      setError(null);
      emitMyAccountChanged();
    } catch (err) {
      if (isApiError(err) && toFieldErrorMap(err.fieldErrors).file) {
        setError(AVATAR_ERROR);
      } else {
        notifyError(err);
      }
    } finally {
      setPending(null);
    }
  }

  async function handleRemove() {
    setPending("remove");
    try {
      await removeMyAvatar();
      notifySuccess("Thông tin tài khoản đã được cập nhật.");
      emitMyAccountChanged();
    } catch (err) {
      notifyError(err);
    } finally {
      setPending(null);
    }
  }

  const shownUrl = previewUrl ?? account.avatarUrl;
  const busy = pending !== null;

  return (
    <section aria-labelledby="avatar-heading" className="grid gap-3">
      <h2 id="avatar-heading" className="text-base font-medium">
        Ảnh đại diện
      </h2>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
        <Avatar className="size-16">
          {shownUrl ? (
            <AvatarImage src={shownUrl} alt={`Ảnh đại diện của ${account.username}`} />
          ) : null}
          <AvatarFallback>{account.username.slice(0, 2).toUpperCase()}</AvatarFallback>
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
              disabled={busy}
              onClick={() => inputRef.current?.click()}
            >
              Chọn ảnh
            </Button>
          ) : null}
          {canRemove && !file ? (
            <Button variant="ghost" disabled={busy} onClick={() => void handleRemove()}>
              {pending === "remove" ? "Đang gỡ..." : "Gỡ ảnh"}
            </Button>
          ) : null}
          {file ? (
            <>
              <Button variant="ghost" disabled={busy} onClick={cancelPending}>
                Hủy
              </Button>
              <Button disabled={busy} onClick={() => void handleSave()}>
                {pending === "save" ? "Đang lưu..." : "Lưu ảnh"}
              </Button>
            </>
          ) : null}
        </div>
      </div>
      <p className="text-xs text-muted-foreground">
        JPEG, PNG hoặc WEBP, tối đa 2 MB.
      </p>
      <FieldError message={error} />
    </section>
  );
}
