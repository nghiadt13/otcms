"use client";

import { Check, Copy } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { IssuedCredential } from "../types/account";

type IssuedPasswordStepProps = {
  title: string;
  /** Câu mở đầu: lời nhắn sau khi tạo (theo spec) hoặc sau khi cấp lại. */
  message: string;
  credential: IssuedCredential;
  onDone: () => void;
};

/**
 * Bước kết quả: mật khẩu chỉ hiện một lần. Không có nút X, Esc và click nền không đóng;
 * chỉ đóng bằng "Xong" để Admin không mất mật khẩu vì bấm nhầm. Mật khẩu chỉ nằm trong
 * props/state của Dialog này, đóng là gỡ khỏi DOM.
 */
export function IssuedPasswordStep({
  title,
  message,
  credential,
  onDone,
}: IssuedPasswordStepProps) {
  const [copyState, setCopyState] = useState<"idle" | "copied" | "failed">(
    "idle",
  );
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);

  async function copyPassword() {
    clearTimeout(timer.current);
    try {
      await navigator.clipboard.writeText(credential.initialPassword);
      setCopyState("copied");
      timer.current = setTimeout(() => setCopyState("idle"), 2000);
    } catch {
      // Clipboard chỉ có trong ngữ cảnh an toàn; báo chọn tay thay vì ném lỗi.
      setCopyState("failed");
    }
  }

  return (
    <Dialog open onOpenChange={() => undefined}>
      <DialogContent showCloseButton={false} className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{message}</DialogDescription>
        </DialogHeader>
        <dl className="grid gap-3 text-sm">
          <div>
            <dt className="text-xs text-muted-foreground">Tên đăng nhập</dt>
            <dd className="mt-0.5 font-medium break-all">{credential.username}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted-foreground">Mật khẩu</dt>
            <dd className="mt-0.5 rounded-lg border bg-muted px-3 py-2 font-mono text-base break-all select-all">
              {credential.initialPassword}
            </dd>
          </div>
        </dl>
        <Button
          variant="outline"
          className="w-full sm:w-fit"
          onClick={() => void copyPassword()}
        >
          {copyState === "copied" ? (
            <Check data-icon="inline-start" />
          ) : (
            <Copy data-icon="inline-start" />
          )}
          {copyState === "copied" ? "Đã sao chép" : "Sao chép mật khẩu"}
        </Button>
        {copyState === "failed" ? (
          <p role="alert" className="text-xs text-destructive">
            Không sao chép được. Chọn mật khẩu và sao chép thủ công.
          </p>
        ) : null}
        <p className="text-sm text-muted-foreground">
          Mật khẩu chỉ hiện một lần. Đóng hộp thoại này là mất mật khẩu. Đưa mật
          khẩu trực tiếp cho nhân viên.
        </p>
        <DialogFooter>
          <Button onClick={onDone}>Xong</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
