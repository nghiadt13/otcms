"use client";

import { MailCheck } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  resendRegistrationCode,
  verifyRegistrationEmail,
} from "../api/authApi";
import type { AuthMessageCode } from "../authMessages";
import { focusFirstInvalidField } from "../focusFirstInvalidField";
import { validateVerificationCode } from "../schema/authFieldRules";
import { toAuthMessageCode } from "../types/auth";
import { AuthAlert } from "./AuthAlert";
import { Button } from "@/components/ui/button";
import { authLinkButtonClass } from "./AuthCard";
import { AuthSubmitButton } from "./AuthSubmitButton";
import { AuthTextField } from "./AuthTextField";

const RESEND_SECONDS = 60;

// UC06 bước 2: nhập mã 6 số gửi tới email. Có gửi lại mã (chờ 60 giây giữa hai
// lần) và "Đổi email" để quay về bước 1 với dữ liệu cũ (E3: thử lại/làm lại).
export function EmailVerificationStep({
  email,
  onVerified,
  onChangeEmail,
}: {
  email: string;
  onVerified: () => void;
  onChangeEmail: () => void;
}) {
  const formRef = useRef<HTMLFormElement>(null);
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState<string>();
  const [message, setMessage] = useState<AuthMessageCode | null>(null);
  const [notice, setNotice] = useState<AuthMessageCode>("MSG-09");
  const [submitting, setSubmitting] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(RESEND_SECONDS);

  useEffect(() => {
    if (secondsLeft <= 0) return;
    const timer = setTimeout(() => setSecondsLeft((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [secondsLeft]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const error = validateVerificationCode(code);
    setCodeError(error);
    if (error) {
      setMessage("MSG-08");
      focusFirstInvalidField(formRef.current);
      return;
    }

    setMessage(null);
    setSubmitting(true);
    try {
      await verifyRegistrationEmail(email, code);
      onVerified();
    } catch (cause) {
      setMessage(toAuthMessageCode(cause));
      setSubmitting(false);
    }
  }

  async function handleResend() {
    setMessage(null);
    setSecondsLeft(RESEND_SECONDS);
    try {
      await resendRegistrationCode(email);
      setNotice("MSG-09");
    } catch (cause) {
      setMessage(toAuthMessageCode(cause));
      setSecondsLeft(0);
    }
  }

  return (
    <form
      className="mt-6 space-y-5"
      noValidate
      onSubmit={handleSubmit}
      ref={formRef}
    >
      <div className="flex items-center gap-3 rounded-lg border border-border px-4 py-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-secondary text-primary">
          <MailCheck aria-hidden className="size-5" />
        </span>
        <div className="min-w-0 text-base">
          <p className="text-muted-foreground">Mã xác minh đã gửi tới</p>
          <p className="font-medium break-all text-foreground">{email}</p>
        </div>
      </div>

      {message ? (
        <AuthAlert code={message} tone="error" />
      ) : (
        <AuthAlert code={notice} tone="success" />
      )}

      <AuthTextField
        autoComplete="one-time-code"
        className="[&_input]:text-center [&_input]:text-xl [&_input]:tracking-[0.5em]"
        error={codeError}
        hint="Mã gồm 6 chữ số, có hiệu lực trong 10 phút."
        inputMode="numeric"
        label="Mã xác minh"
        maxLength={6}
        onChange={(event) => {
          setCode(event.target.value.replace(/\D/g, ""));
          setCodeError(undefined);
        }}
        placeholder="••••••"
        required
        value={code}
      />

      <AuthSubmitButton
        submitting={submitting}
        submittingLabel="Đang xác minh..."
      >
        Xác minh
      </AuthSubmitButton>

      <div className="flex flex-col items-center gap-3 text-base text-muted-foreground sm:flex-row sm:justify-between">
        <p aria-live="polite">
          {secondsLeft > 0 ? (
            <>Gửi lại mã sau {secondsLeft} giây</>
          ) : (
            <>
              Chưa nhận được mã?{" "}
              <Button
                className={authLinkButtonClass}
                onClick={handleResend}
                type="button"
                variant="link"
              >
                Gửi lại mã
              </Button>
            </>
          )}
        </p>
        <Button
          className={authLinkButtonClass}
          onClick={onChangeEmail}
          type="button"
          variant="link"
        >
          Đổi email
        </Button>
      </div>
    </form>
  );
}
