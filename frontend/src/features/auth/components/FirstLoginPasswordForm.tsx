"use client";
import { useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { getSession } from "@/lib/auth/authSession";
import { Button } from "@/components/ui/button";
import {
  changeFirstLoginPassword,
  FirstLoginPasswordError,
} from "../api/firstLoginPasswordApi";
import {
  validateRecoveryPassword,
  recoveryPasswordPolicy,
} from "../schema/recoveryPasswordRules";
import { validatePasswordConfirmation } from "../schema/authFieldRules";
import { PasswordField } from "./PasswordField";
import { PasswordPolicyHint } from "./PasswordPolicyHint";

export function FirstLoginPasswordForm() {
  const [sessionState, setSessionState] = useState<"loading" | "ready" | "unavailable">("loading");
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [fieldError, setFieldError] = useState<{
    current?: string;
    next?: string;
    confirm?: string;
  }>({});
  const [message, setMessage] = useState("");
  const [pending, setPending] = useState(false);
  const [done, setDone] = useState(false);
  useEffect(() => {
    let active = true;
    getSession()
      .then((session) => { if (active) setSessionState(session && session.accountType !== "PATIENT" ? "ready" : "unavailable"); })
      .catch(() => { if (active) setSessionState("unavailable"); });
    return () => { active = false; };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = {
      current: current ? undefined : "Nhập mật khẩu khởi tạo.",
      next: validateRecoveryPassword(next),
      confirm: validatePasswordConfirmation(next, confirm),
    };
    setFieldError(errors);
    if (errors.current || errors.next || errors.confirm) return;
    setPending(true);
    setMessage("");
    try {
      await changeFirstLoginPassword(current, next);
      setDone(true);
    } catch (error) {
      if (error instanceof FirstLoginPasswordError) {
        if (error.reason === "current")
          setFieldError({ current: "Mật khẩu hiện tại không đúng." });
        else if (error.reason === "reused")
          setFieldError({ next: "Mật khẩu mới phải khác mật khẩu hiện tại." });
        else if (error.reason === "invalid")
          setFieldError({ next: "Mật khẩu mới không hợp lệ." });
        else
          setMessage(
            error.reason === "session"
              ? "Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại."
              : "Không thể đổi mật khẩu. Vui lòng thử lại.",
          );
      } else setMessage("Không thể đổi mật khẩu. Vui lòng thử lại.");
    } finally {
      setPending(false);
    }
  }

  if (sessionState === "loading") return <p role="status" className="mt-8 text-muted-foreground">Đang kiểm tra phiên đăng nhập…</p>;
  if (sessionState === "unavailable")
    return (
      <div className="mt-8 space-y-4">
        <p role="status">
          Nếu phiên đăng nhập đã hết hạn, hãy đăng nhập lại để đổi mật khẩu.
        </p>
        <Link className="text-primary underline" href="/staff-login">
          Về đăng nhập
        </Link>
      </div>
    );
  if (done)
    return (
      <div className="mt-8 space-y-4">
        <p role="status">
          Mật khẩu đã được đổi. Vui lòng đăng nhập lại bằng mật khẩu mới.
        </p>
        <Link className="text-primary underline" href="/staff-login">
          Đăng nhập lại
        </Link>
      </div>
    );
  return (
    <form className="mt-8 space-y-5" onSubmit={submit} noValidate>
      {message && (
        <p role="alert" className="text-destructive">
          {message}
        </p>
      )}
      <PasswordField
        label="Mật khẩu khởi tạo"
        value={current}
        autoComplete="current-password"
        error={fieldError.current}
        onChange={(event) => {
          setCurrent(event.target.value);
          setFieldError((errors) => ({ ...errors, current: undefined }));
        }}
        required
      />
      <div>
        <PasswordField
          label="Mật khẩu mới"
          value={next}
          autoComplete="new-password"
          error={fieldError.next}
          onChange={(event) => {
            setNext(event.target.value);
            setFieldError((errors) => ({ ...errors, next: undefined }));
          }}
          required
        />
        <PasswordPolicyHint password={next} rules={recoveryPasswordPolicy} />
      </div>
      <PasswordField
        label="Nhập lại mật khẩu mới"
        value={confirm}
        autoComplete="new-password"
        error={fieldError.confirm}
        onChange={(event) => {
          setConfirm(event.target.value);
          setFieldError((errors) => ({ ...errors, confirm: undefined }));
        }}
        required
      />
      <Button type="submit" className="w-full" disabled={pending}>
        {pending ? "Đang đổi mật khẩu…" : "Đổi mật khẩu"}
      </Button>
    </form>
  );
}
