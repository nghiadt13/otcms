"use client";

import { ShieldAlert } from "lucide-react";
import { type FormEvent, useState } from "react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { UserAccount } from "../types/account";

type AccountLockDialogProps = {
  account: UserAccount | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirmLock: (account: UserAccount, reason: string) => Promise<void>;
};

export function AccountLockDialog({
  account,
  open,
  onOpenChange,
  onConfirmLock,
}: AccountLockDialogProps) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!account) return null;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!account) return;

    const trimmed = reason.trim();
    if (!trimmed) {
      setError("Vui lòng nhập lý do tạm khóa tài khoản.");
      return;
    }
    if (trimmed.length < 5) {
      setError("Lý do tạm khóa cần tối thiểu 5 ký tự.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      await onConfirmLock(account, trimmed);
      setReason("");
      onOpenChange(false);
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Không thể khóa tài khoản. Vui lòng thử lại.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  const handleClose = () => {
    if (submitting) return;
    setReason("");
    setError(null);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-destructive">
            <ShieldAlert className="size-5" /> Khóa tài khoản người dùng
          </DialogTitle>
          <DialogDescription>
            Tài khoản của <strong>{account.fullName}</strong> ({account.email}) sẽ bị
            vô hiệu hóa quyền đăng nhập vào hệ thống.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <Alert className="border-destructive/20 bg-destructive/5 text-foreground">
            <AlertDescription className="text-xs text-muted-foreground">
              Theo quy định <strong>BR-15</strong>: Việc tạm khóa chỉ hạn chế quyền truy
              cập, toàn bộ lịch sử ca làm việc, lịch hẹn và hồ sơ bệnh án liên quan vẫn
              được lưu trữ đầy đủ.
            </AlertDescription>
          </Alert>

          <div className="space-y-2">
            <Label htmlFor="lock-reason">
              Lý do tạm khóa <span className="text-destructive">*</span>
            </Label>
            <Textarea
              id="lock-reason"
              placeholder="Ví dụ: Nhân viên nghỉ việc / Vi phạm quy định bảo mật / Yêu cầu từ quản trị viên..."
              rows={3}
              value={reason}
              onChange={(event) => {
                setReason(event.target.value);
                if (error) setError(null);
              }}
              aria-invalid={Boolean(error)}
              aria-describedby={error ? "lock-reason-error" : undefined}
            />
            {error ? (
              <p id="lock-reason-error" className="text-xs text-destructive">
                {error}
              </p>
            ) : null}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={handleClose}
              disabled={submitting}
            >
              Hủy
            </Button>
            <Button
              type="submit"
              variant="destructive"
              disabled={submitting}
            >
              {submitting ? "Đang xử lý..." : "Xác nhận khóa tài khoản"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
