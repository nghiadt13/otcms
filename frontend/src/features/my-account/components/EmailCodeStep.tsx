"use client";

import { useEffect, useId, useState, type FormEvent } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { notifySuccess } from "@/lib/messages/notify";
import { messageFromError } from "@/lib/messages/appMessages";
import {
  requestMyEmailChange,
  verifyMyEmailChange,
} from "../api/myAccountProfileApi";
import { emitMyAccountChanged } from "../hooks/useMyAccount";
import { EmailChangeErrorNotice } from "./EmailChangeErrorNotice";

const CODE_PATTERN = /^\d{6}$/;
const RESEND_SECONDS = 60;

// Bước 2 của đổi email: nhập mã 6 số gửi tới email mới. Đúng mã thì email của tài khoản đổi ngay.
// Chờ 60 giây giữa hai lần gửi lại mã; "Đổi email" quay về bước 1 với email đã nhập.
export function EmailCodeStep({
  email,
  onBusyChange,
  onBack,
  onVerified,
}: {
  email: string;
  onBusyChange: (busy: boolean) => void;
  onBack: () => void;
  onVerified: () => void;
}) {
  const inputId = useId();
  const errorId = `${inputId}-error`;
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState<string | null>(null);
  const [alertText, setAlertText] = useState<string | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [resending, setResending] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(RESEND_SECONDS);

  useEffect(() => {
    if (secondsLeft <= 0) return;
    const timer = setTimeout(() => setSecondsLeft((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [secondsLeft]);

  const busy = verifying || resending;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!CODE_PATTERN.test(code)) {
      setCodeError("Nhập đủ 6 chữ số trong email.");
      return;
    }

    setVerifying(true);
    onBusyChange(true);
    setAlertText(null);
    try {
      await verifyMyEmailChange(code);
      notifySuccess("MSG-05");
      emitMyAccountChanged();
      onBusyChange(false);
      onVerified();
    } catch (cause) {
      setAlertText(messageFromError(cause));
      setVerifying(false);
      onBusyChange(false);
    }
  }

  async function handleResend() {
    setResending(true);
    onBusyChange(true);
    setAlertText(null);
    try {
      await requestMyEmailChange(email);
      setSecondsLeft(RESEND_SECONDS);
    } catch (cause) {
      setAlertText(messageFromError(cause));
    } finally {
      setResending(false);
      onBusyChange(false);
    }
  }

  return (
    <form className="grid gap-5" noValidate onSubmit={handleSubmit}>
      {alertText ? <EmailChangeErrorNotice text={alertText} /> : null}
      <p className="text-base">
        Mã gồm 6 chữ số đã gửi tới{" "}
        <span className="font-medium break-all">{email}</span>.
      </p>
      <div className="grid gap-1.5">
        <Label className="text-base" htmlFor={inputId}>
          Mã xác minh
        </Label>
        <Input
          aria-describedby={codeError ? errorId : undefined}
          aria-invalid={codeError ? true : undefined}
          autoComplete="one-time-code"
          className="h-12 text-center text-xl tracking-[0.5em]"
          disabled={busy}
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
        <FieldError id={errorId} message={codeError} />
      </div>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-base">
          <Button
            className="h-11 px-0 text-base"
            disabled={busy}
            onClick={onBack}
            type="button"
            variant="link"
          >
            Đổi email
          </Button>
          {secondsLeft > 0 ? (
            <span aria-live="polite" className="text-muted-foreground">
              Gửi lại mã sau {secondsLeft} giây
            </span>
          ) : (
            <Button
              className="h-11 px-0 text-base"
              disabled={busy}
              onClick={() => void handleResend()}
              type="button"
              variant="link"
            >
              {resending ? "Đang gửi lại..." : "Gửi lại mã"}
            </Button>
          )}
        </div>
        <Button className="h-12 px-5 text-base" disabled={busy} type="submit">
          {verifying ? "Đang xác minh..." : "Xác minh"}
        </Button>
      </div>
    </form>
  );
}
