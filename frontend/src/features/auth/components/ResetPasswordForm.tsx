"use client";

import Link from "next/link";
import { useRef, useState, type FormEvent } from "react";
import {
  resetPassword,
  PasswordRecoveryError,
  type RecoveryFailure,
} from "../api/passwordRecoveryApi";
import {
  recoveryPasswordPolicy,
  validateRecoveryPassword,
} from "../schema/recoveryPasswordRules";
import { RecoveryErrorNotice } from "./RecoveryErrorNotice";
import type { AuthMessageCode } from "../authMessages";
import { focusFirstInvalidField } from "../focusFirstInvalidField";
import {
  validatePasswordConfirmation,
  validateVerificationCode,
} from "../schema/authFieldRules";
import { toAuthMessageCode } from "../types/auth";
import { AuthTextField } from "./AuthTextField";
import { AuthAlert } from "./AuthAlert";
import { AuthSubmitButton, authPrimaryLinkClass } from "./AuthSubmitButton";
import { PasswordField } from "./PasswordField";
import { PasswordMatchHint } from "./PasswordMatchHint";
import { PasswordPolicyHint } from "./PasswordPolicyHint";

// UC02: OTP and the new password are submitted together.
export function ResetPasswordForm({
  email,
  onRestart,
}: {
  email: string;
  onRestart: () => void;
}) {
  const loginHref = "/login";
  const [failure, setFailure] = useState<RecoveryFailure | null>(null);
  const [otp, setOtp] = useState("");
  const formRef = useRef<HTMLFormElement>(null);
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [confirmTouched, setConfirmTouched] = useState(false);
  const [errors, setErrors] = useState<{
    otp?: string;
    password?: string;
    confirm?: string;
  }>({});
  const [message, setMessage] = useState<AuthMessageCode | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = {
      otp: validateVerificationCode(otp),
      password: validateRecoveryPassword(password),
      confirm: validatePasswordConfirmation(password, confirmation),
    };
    setErrors(next);
    if (next.otp || next.password || next.confirm) {
      setMessage("MSG-08");
      focusFirstInvalidField(formRef.current);
      return;
    }

    setMessage(null);
    setFailure(null);
    setSubmitting(true);
    try {
      await resetPassword(email, otp, password);
      setDone(true);
    } catch (cause) {
      if (cause instanceof PasswordRecoveryError) {
        setFailure(cause.reason);
        if (cause.reason === "password-reuse")
          setErrors({ password: "Mật khẩu mới phải khác mật khẩu hiện tại." });
      } else setMessage(toAuthMessageCode(cause));
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <div className="mt-8 space-y-6">
        <AuthAlert code="MSG-03" tone="success" />
        <Link className={authPrimaryLinkClass} href={loginHref}>
          Đăng nhập
        </Link>
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
      {failure === "invalid-token" && <AuthAlert code="MSG-77" tone="error" />}
      {failure &&
        failure !== "invalid-token" &&
        failure !== "password-reuse" && (
          <RecoveryErrorNotice reason={failure} />
        )}
      <AuthTextField
        label="Mã OTP"
        value={otp}
        onChange={(event) => setOtp(event.target.value)}
        error={errors.otp}
        required
        inputMode="numeric"
      />
      <div>
        <PasswordField
          autoComplete="new-password"
          error={errors.password}
          label="Mật khẩu mới"
          onChange={(event) => {
            setPassword(event.target.value);
            setErrors((current) => ({ ...current, password: undefined }));
          }}
          required
          value={password}
        />
        <PasswordPolicyHint
          password={password}
          rules={recoveryPasswordPolicy}
        />
      </div>
      <div>
        <PasswordField
          autoComplete="new-password"
          error={errors.confirm}
          label="Nhập lại mật khẩu mới"
          onBlur={() => setConfirmTouched(true)}
          onChange={(event) => {
            setConfirmation(event.target.value);
            setErrors((current) => ({ ...current, confirm: undefined }));
          }}
          required
          value={confirmation}
        />
        {!errors.confirm && (
          <PasswordMatchHint
            confirmation={confirmation}
            password={password}
            touched={confirmTouched}
          />
        )}
      </div>
      <AuthSubmitButton
        submitting={submitting}
        submittingLabel="Đang cập nhật..."
      >
        Đặt mật khẩu mới
      </AuthSubmitButton>
      <button
        type="button"
        onClick={onRestart}
        className="text-primary underline"
      >
        Yêu cầu mã mới
      </button>
    </form>
  );
}
