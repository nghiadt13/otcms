"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  resetPassword,
  PasswordRecoveryError,
  type RecoveryPortal,
  type RecoveryFailure,
} from "../api/passwordRecoveryApi";
import {
  recoveryPasswordPolicy,
  validateRecoveryPassword,
} from "../schema/recoveryPasswordRules";
import { RecoveryErrorNotice } from "./RecoveryErrorNotice";
import type { AuthMessageCode } from "../authMessages";
import { focusFirstInvalidField } from "../focusFirstInvalidField";
import { validatePasswordConfirmation } from "../schema/authFieldRules";
import { toAuthMessageCode } from "../types/auth";
import { AuthAlert } from "./AuthAlert";
import { authLinkClass } from "./AuthCard";
import { AuthSubmitButton, authPrimaryLinkClass } from "./AuthSubmitButton";
import { PasswordField } from "./PasswordField";
import { PasswordMatchHint } from "./PasswordMatchHint";
import { PasswordPolicyHint } from "./PasswordPolicyHint";

// UC02 bước 5–6: mở từ link trong email, đặt mật khẩu mới. Thiếu/sai token là
// xác nhận bị từ chối (MSG-04, E2): cho bắt đầu lại từ "Quên mật khẩu".
export function ResetPasswordForm({
  token,
  portal = "patient",
}: {
  token: string;
  portal?: RecoveryPortal;
}) {
  const loginHref = portal === "staff" ? "/staff-login" : "/login";
  const recoveryHref =
    portal === "staff" ? "/forgot-password?portal=staff" : "/forgot-password";
  const [failure, setFailure] = useState<RecoveryFailure | null>(null);
  useEffect(() => {
    const url = new URL(window.location.href);
    if (url.searchParams.has("token")) {
      url.searchParams.delete("token");
      window.history.replaceState(
        window.history.state,
        "",
        url.pathname + url.search + url.hash,
      );
    }
  }, []);
  const formRef = useRef<HTMLFormElement>(null);
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [confirmTouched, setConfirmTouched] = useState(false);
  const [errors, setErrors] = useState<{ password?: string; confirm?: string }>(
    {},
  );
  const [message, setMessage] = useState<AuthMessageCode | null>(
    token ? null : "MSG-04",
  );
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = {
      password: validateRecoveryPassword(password),
      confirm: validatePasswordConfirmation(password, confirmation),
    };
    setErrors(next);
    if (next.password || next.confirm) {
      setMessage("MSG-08");
      focusFirstInvalidField(formRef.current);
      return;
    }

    setMessage(null);
    setFailure(null);
    setSubmitting(true);
    try {
      await resetPassword(token, password, portal);
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

  if (!token || failure === "invalid-token") {
    return (
      <div className="mt-8 space-y-6">
        <RecoveryErrorNotice reason="invalid-token" />
        <Link className={authPrimaryLinkClass} href={recoveryHref}>
          Gửi lại hướng dẫn
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
      {failure && failure !== "password-reuse" && (
        <RecoveryErrorNotice reason={failure} />
      )}
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
      {message === "MSG-04" && (
        <p className="text-center text-base text-muted-foreground">
          Link đã hết hạn?{" "}
          <Link className={authLinkClass} href={recoveryHref}>
            Gửi lại hướng dẫn
          </Link>
        </p>
      )}
    </form>
  );
}
