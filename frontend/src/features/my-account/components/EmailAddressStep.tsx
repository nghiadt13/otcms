"use client";

import { useId, useState, type FormEvent } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { myAccountErrorMessage } from "../api/myAccountErrors";
import { requestMyEmailChange } from "../api/myAccountProfileApi";
import { useAccountOperation } from "../hooks/useAccountOperation";
import { EmailChangeErrorNotice } from "./EmailChangeErrorNotice";

export function EmailAddressStep({
  currentEmail,
  onSent,
  onCancel,
}: {
  currentEmail: string;
  onSent: (newEmail: string) => void;
  onCancel: () => void;
}) {
  const inputId = useId();
  const [email, setEmail] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [alertText, setAlertText] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const operation = useAccountOperation();

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = email.trim();
    if (
      value.length > 254 ||
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) ||
      /\p{Cc}/u.test(value)
    ) {
      setFieldError("Email chưa đúng định dạng.");
      return;
    }
    const signal = operation.begin();
    if (!signal) return;
    setPending(true);
    setAlertText(null);
    try {
      await requestMyEmailChange(value, signal);
      if (operation.isCurrent()) onSent(value);
    } catch (cause) {
      if (operation.isCurrent()) setAlertText(myAccountErrorMessage(cause));
    } finally {
      if (operation.isCurrent()) setPending(false);
      operation.finish();
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
          aria-describedby={fieldError ? `${inputId}-error` : undefined}
          aria-invalid={!!fieldError}
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
        <FieldError id={`${inputId}-error`} message={fieldError} />
      </div>
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button
          className="h-12 text-base"
          onClick={onCancel}
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
          {pending ? "Đang xử lý..." : "Gửi mã"}
        </Button>
      </div>
    </form>
  );
}
