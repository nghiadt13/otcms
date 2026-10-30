"use client";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { ApiError } from "@/lib/api/apiError";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  administratorError,
  createAdministrator,
  reissueAdministratorPassword,
  type Administrator,
  type IssuedAdministratorPassword,
} from "../api/administratorApi";
import { validateAdministratorUsername } from "../schema/administratorRules";

export function AdministratorCredentialDialog({
  account,
  onClose,
  onSaved,
}: {
  account?: Administrator;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [username, setUsername] = useState(account?.username ?? "");
  const [issued, setIssued] = useState<IssuedAdministratorPassword | null>(
    null,
  );
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [fieldError, setFieldError] = useState<string>();
  const [copyMessage, setCopyMessage] = useState("");
  // Only component memory holds the one-time credential. Closing unmounts this component.
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (pending || issued) return;
    const invalid = account
      ? undefined
      : validateAdministratorUsername(username);
    setFieldError(invalid);
    if (invalid) return;
    setPending(true);
    setError("");
    try {
      setIssued(
        account
          ? await reissueAdministratorPassword(account.accountId)
          : await createAdministrator(username),
      );
      if (!account)
        toast.success(
          `Đã tạo tài khoản Administrator với tên đăng nhập ${username}. Mật khẩu khởi tạo chỉ hiển thị một lần.`,
        );
      onSaved();
    } catch (error) {
      const message = administratorError(error);
      if (error instanceof ApiError && error.status === 400 && !account) {
        setFieldError(message);
      } else {
        toast.error(message);
        if (
          error instanceof ApiError &&
          (error.errorCode === "USERNAME_ALREADY_EXISTS" ||
            error.status === 409)
        )
          setError(message);
        else {
          onSaved();
          onClose();
        }
      }
    } finally {
      setPending(false);
    }
  }
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !pending && !issued) onClose();
      }}
    >
      <DialogContent
        showCloseButton={!pending && !issued}
        className="sm:max-w-md"
      >
        <DialogHeader>
          <DialogTitle>
            {issued
              ? "Mật khẩu khởi tạo đã được cấp"
              : account
                ? "Cấp lại mật khẩu Admin"
                : "Tạo tài khoản Admin"}
          </DialogTitle>
          <DialogDescription>
            {issued
              ? "Chỉ hiển thị tại đây một lần. Đóng cửa sổ sẽ không xem lại được mật khẩu."
              : account
                ? "Mật khẩu hiện tại sẽ bị thay, các phiên đăng nhập bị thu hồi. Admin phải đổi mật khẩu khi đăng nhập tiếp theo."
                : "Vai trò cố định: Administrator. Hệ thống sẽ tạo mật khẩu khởi tạo."}
          </DialogDescription>
        </DialogHeader>
        {issued ? (
          <>
            <dl className="space-y-3 rounded-lg border bg-muted/30 p-4">
              <div>
                <dt className="text-muted-foreground">Tên đăng nhập</dt>
                <dd className="break-all font-medium">{username}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Mật khẩu khởi tạo</dt>
                <dd
                  className="mt-1 select-all break-all font-mono text-base"
                  aria-label="Mật khẩu khởi tạo"
                >
                  {issued.temporaryPassword}
                </dd>
              </div>
            </dl>
            <p className="text-muted-foreground">
              Bàn giao trực tiếp cho Admin. Admin phải đổi mật khẩu ở lần đăng
              nhập tiếp theo.
            </p>
            {copyMessage && <p role="status">{copyMessage}</p>}
            <DialogFooter>
              <Button
                variant="outline"
                onClick={async () => {
                  try {
                    await navigator.clipboard.writeText(
                      issued.temporaryPassword,
                    );
                    setCopyMessage("Đã sao chép mật khẩu.");
                  } catch {
                    setCopyMessage(
                      "Không thể sao chép tự động. Hãy chọn và sao chép mật khẩu ở trên.",
                    );
                  }
                }}
              >
                Sao chép mật khẩu
              </Button>
              <Button onClick={onClose}>Đã lưu, đóng</Button>
            </DialogFooter>
          </>
        ) : (
          <form onSubmit={submit} className="space-y-4" noValidate>
            {account ? (
              <p className="break-all font-medium">{account.username}</p>
            ) : (
              <div className="space-y-2">
                <Label htmlFor="administrator-username">Tên đăng nhập</Label>
                <Input
                  autoFocus
                  id="administrator-username"
                  autoComplete="off"
                  maxLength={64}
                  value={username}
                  disabled={pending}
                  onChange={(event) => {
                    setUsername(event.target.value);
                    setFieldError(undefined);
                  }}
                  aria-invalid={!!fieldError}
                  aria-describedby="administrator-username-hint"
                />
                <p
                  id="administrator-username-hint"
                  className={
                    fieldError ? "text-destructive" : "text-muted-foreground"
                  }
                >
                  {fieldError ??
                    "Tối đa 64 ký tự. Tên đăng nhập phải duy nhất."}
                </p>
              </div>
            )}
            {error && (
              <p role="alert" className="text-destructive">
                {error}
              </p>
            )}
            <DialogFooter>
              <Button
                variant="outline"
                type="button"
                disabled={pending}
                onClick={onClose}
              >
                Hủy
              </Button>
              <Button type="submit" disabled={pending}>
                {pending
                  ? "Đang xử lý…"
                  : account
                    ? "Cấp lại mật khẩu"
                    : "Tạo tài khoản"}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
