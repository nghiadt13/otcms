"use client";

import { useId, useState, type FormEvent } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { notifySuccess } from "@/lib/messages/notify";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { myAccountErrorMessage } from "../api/myAccountErrors";
import {
  requestMyEmailChange,
  verifyMyEmailChange,
} from "../api/myAccountProfileApi";
import { useAccountOperation } from "../hooks/useAccountOperation";
import { EmailChangeErrorNotice } from "./EmailChangeErrorNotice";

export function EmailCodeStep({
  email,
  onBack,
  onVerified,
}: {
  email: string;
  onBack: () => void;
  onVerified: () => void;
}) {
  const inputId = useId();
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState<string | null>(null);
  const [alertText, setAlertText] = useState<string | null>(null);
  const [pending, setPending] = useState<"verify" | "resend" | null>(null);
  const operation = useAccountOperation();

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!/^[0-9]{6}$/.test(code)) {
      setCodeError("Nhập đủ 6 chữ số.");
      return;
    }
    const signal = operation.begin();
    if (!signal) return;
    setPending("verify");
    setAlertText(null);
    try {
      await verifyMyEmailChange(code, signal);
      if (operation.isCurrent()) {
        setCode("");
        notifySuccess("MSG-05");
        onVerified();
      }
    } catch (cause) {
      if (operation.isCurrent()) {
        setCode("");
        setAlertText(myAccountErrorMessage(cause));
      }
    } finally {
      if (operation.isCurrent()) setPending(null);
      operation.finish();
    }
  }
  async function handleResend() {
    const signal = operation.begin();
    if (!signal) return;
    setPending("resend");
    setAlertText(null);
    setCode("");
    try {
      await requestMyEmailChange(email, signal);
    } catch (cause) {
      if (operation.isCurrent()) setAlertText(myAccountErrorMessage(cause));
    } finally {
      if (operation.isCurrent()) setPending(null);
      operation.finish();
    }
  }
  return (
    <form className="grid gap-5" noValidate onSubmit={handleSubmit}>
      <p role="status" className="text-base">
        {formatAppMessage("MSG-06")}
      </p>
      {alertText ? <EmailChangeErrorNotice text={alertText} /> : null}
      <p className="text-base">
        Nhập mã OTP cho <span className="font-medium break-all">{email}</span>.
      </p>
      <div className="grid gap-1.5">
        <Label className="text-base" htmlFor={inputId}>
          Mã xác minh
        </Label>
        <Input
          aria-describedby={codeError ? `${inputId}-error` : undefined}
          aria-invalid={!!codeError}
          autoComplete="one-time-code"
          className="h-12 text-center text-xl tracking-[0.5em]"
          disabled={pending !== null}
          id={inputId}
          inputMode="numeric"
          maxLength={6}
          onChange={(event) => {
            setCode(event.target.value.replace(/\D/g, ""));
            setCodeError(null);
          }}
          placeholder="••••••"
          value={code}
        />
        <FieldError id={`${inputId}-error`} message={codeError} />
      </div>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-base">
          <Button
            className="h-11 px-0 text-base"
            onClick={onBack}
            type="button"
            variant="link"
          >
            Đổi email
          </Button>
          <Button
            className="h-11 px-0 text-base"
            disabled={pending !== null}
            onClick={() => void handleResend()}
            type="button"
            variant="link"
          >
            {pending === "resend" ? "Đang xử lý..." : "Gửi lại mã"}
          </Button>
        </div>
        <Button
          className="h-12 px-5 text-base"
          disabled={pending !== null}
          type="submit"
        >
          {pending === "verify" ? "Đang xác minh..." : "Xác minh"}
        </Button>
      </div>
    </form>
  );
}
