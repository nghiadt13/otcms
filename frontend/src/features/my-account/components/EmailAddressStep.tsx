"use client";

import { useId, useState, type FormEvent } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { messageFromError } from "@/lib/messages/appMessages";
import { requestMyEmailChange } from "../api/myAccountProfileApi";
import { EmailChangeErrorNotice } from "./EmailChangeErrorNotice";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

// Bước 1 của đổi email: nhập email mới rồi gửi mã. Email cũ vẫn dùng được tới khi xác minh xong (BR-05).
export function EmailAddressStep({
  currentEmail,
  onBusyChange,
  onSent,
  onCancel,
}: {
  currentEmail: string;
  onBusyChange: (busy: boolean) => void;
  onSent: (newEmail: string) => void;
  onCancel: () => void;
}) {
  const inputId = useId();
  const errorId = `${inputId}-error`;
  const [email, setEmail] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [alertText, setAlertText] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = email.trim();
    if (!EMAIL_PATTERN.test(value)) {
      setFieldError("Email chưa đúng định dạng.");
      return;
    }

    setPending(true);
    onBusyChange(true);
    setAlertText(null);
    try {
      await requestMyEmailChange(value);
      // Thành công thì màn này được thay bằng bước nhập mã (unmount), nên không đặt lại trạng thái.
      onBusyChange(false);
      onSent(value);
    } catch (cause) {
      setAlertText(messageFromError(cause));
      setPending(false);
      onBusyChange(false);
    }
  }

  return (
    <form className="grid gap-5" noValidate onSubmit={handleSubmit}>
      {alertText ? <EmailChangeErrorNotice text={alertText} /> : null}
      <p className="text-base text-muted-foreground">
        Email hiện tại:{" "}
        <span className="break-all text-foreground">{currentEmail}</span>
      </p>
      <div className="grid gap-1.5">
        <Label className="text-base" htmlFor={inputId}>
          Email mới
        </Label>
        <Input
          aria-describedby={fieldError ? errorId : undefined}
          aria-invalid={fieldError ? true : undefined}
          autoComplete="email"
          className="h-12 text-base"
          disabled={pending}
          id={inputId}
          inputMode="email"
          onChange={(event) => {
            setEmail(event.target.value);
            setFieldError(null);
          }}
          type="email"
          value={email}
        />
        <FieldError id={errorId} message={fieldError} />
      </div>
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button
          className="h-12 text-base"
          disabled={pending}
          onClick={onCancel}
          type="button"
          variant="outline"
        >
          Hủy
        </Button>
        <Button className="h-12 px-5 text-base" disabled={pending} type="submit">
          {pending ? "Đang gửi..." : "Gửi mã"}
        </Button>
      </div>
    </form>
  );
}
