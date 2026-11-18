"use client";

import { Mail } from "lucide-react";
import { useRef, useState, type FormEvent } from "react";
import {
  requestPasswordReset,
  PasswordRecoveryError,
  type RecoveryPortal,
  type RecoveryFailure,
} from "../api/passwordRecoveryApi";
import { RecoveryErrorNotice } from "./RecoveryErrorNotice";
import type { AuthMessageCode } from "../authMessages";
import { focusFirstInvalidField } from "../focusFirstInvalidField";
import { validateEmail } from "../schema/authFieldRules";
import { toAuthMessageCode } from "../types/auth";
import { ResetPasswordForm } from "./ResetPasswordForm";
import { AuthAlert } from "./AuthAlert";
import { AuthSubmitButton } from "./AuthSubmitButton";
import { AuthTextField } from "./AuthTextField";

// UC02 bước 1–4: nhập email để nhận hướng dẫn đặt lại mật khẩu. Sau khi gửi luôn
// hiện cùng một màn xác nhận (MSG-02), dù email có thuộc tài khoản nào hay không
// (BR-03).
export function ForgotPasswordForm({
  portal = "patient",
}: {
  portal?: RecoveryPortal;
}) {
  const [failure, setFailure] = useState<RecoveryFailure | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const [email, setEmail] = useState("");
  const [emailError, setEmailError] = useState<string>();
  const [message, setMessage] = useState<AuthMessageCode | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const error = validateEmail(email);
    setEmailError(error);
    if (error) {
      setMessage("MSG-08");
      focusFirstInvalidField(formRef.current);
      return;
    }

    setMessage(null);
    setFailure(null);
    setSubmitting(true);
    try {
      await requestPasswordReset(email, portal);
      setSentTo(email.trim());
    } catch (cause) {
      if (cause instanceof PasswordRecoveryError) setFailure(cause.reason);
      else setMessage(toAuthMessageCode(cause));
    } finally {
      setSubmitting(false);
    }
  }

  if (sentTo) {
    return (
      <div className="mt-8 space-y-4">
        <AuthAlert code="MSG-02" tone="success" />
        <p>{sentTo}</p>
        <ResetPasswordForm email={sentTo} onRestart={() => setSentTo(null)} />
      </div>
    );
  }

  return (
    <form
      className="mt-8 space-y-5"
      noValidate
      onSubmit={handleSubmit}
      ref={formRef}
    >
      {message && <AuthAlert code={message} tone="error" />}
      {failure && <RecoveryErrorNotice reason={failure} />}
      <AuthTextField
        autoComplete="email"
        error={emailError}
        icon={Mail}
        hint="Dùng email bạn đã đăng ký với phòng khám."
        inputMode="email"
        label="Email"
        onChange={(event) => {
          setEmail(event.target.value);
          setEmailError(undefined);
        }}
        placeholder="ten@email.com"
        required
        type="email"
        value={email}
      />
      <AuthSubmitButton submitting={submitting} submittingLabel="Đang gửi...">
        Gửi hướng dẫn
      </AuthSubmitButton>
    </form>
  );
}
