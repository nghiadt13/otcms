"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { getSupabaseBrowserClient } from "@/lib/supabase/browser";
import { AuthIcon } from "./AuthIcon";
import styles from "./auth.module.css";
export function SignInForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(true);
  const [showPassword, setShowPassword] = useState(false);
  const [status, setStatus] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  async function handleSubmit(event) {
    event.preventDefault();
    setStatus(null);
    setSubmitting(true);
    try {
      const { error } =
        await getSupabaseBrowserClient().auth.signInWithPassword({
          email: email.trim(),
          password,
        });
      if (error) throw error;
      router.replace("/workspace");
    } catch (cause) {
      setStatus({
        kind: "error",
        message: cause instanceof Error ? cause.message : "Unable to sign in.",
      });
    } finally {
      setSubmitting(false);
    }
  }
  async function handlePasswordReset() {
    if (!email.trim()) {
      setStatus({ kind: "error", message: "Enter your email address first." });
      return;
    }
    setStatus(null);
    try {
      const { error } =
        await getSupabaseBrowserClient().auth.resetPasswordForEmail(
          email.trim(),
          { redirectTo: `${window.location.origin}/login` },
        );
      if (error) throw error;
      setStatus({
        kind: "success",
        message: "Password reset instructions have been sent to your email.",
      });
    } catch (cause) {
      setStatus({
        kind: "error",
        message:
          cause instanceof Error
            ? cause.message
            : "Unable to send reset instructions.",
      });
    }
  }
  return (
    <div className={styles.loginContent}>
      <div className={styles.formWrap}>
        <p className={styles.eyebrow}>Secure patient access</p>
        <h1 className={styles.title}>Welcome back</h1>
        <p className={styles.intro}>
          Sign in to appointments, active visit updates, released medical
          records and payments.
        </p>

        <form className={styles.loginForm} onSubmit={handleSubmit}>
          <label className={styles.field}>
            <span>Email address</span>
            <span className={styles.inputShell}>
              <AuthIcon className={styles.fieldIcon} name="mail" />
              <input
                autoComplete="email"
                onChange={(event) => setEmail(event.target.value)}
                placeholder="patient@example.com"
                required
                type="email"
                value={email}
              />
            </span>
          </label>

          <label className={styles.field}>
            <span>Password</span>
            <span className={styles.inputShell}>
              <AuthIcon className={styles.fieldIcon} name="lock" />
              <input
                autoComplete="current-password"
                minLength={8}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Enter your password"
                required
                type={showPassword ? "text" : "password"}
                value={password}
              />
              <button
                aria-label={showPassword ? "Hide password" : "Show password"}
                className={styles.iconButton}
                onClick={() => setShowPassword((current) => !current)}
                type="button"
              >
                <AuthIcon name={showPassword ? "eye-off" : "eye"} />
              </button>
            </span>
          </label>

          <div className={styles.formOptions}>
            <label className={styles.checkLabel}>
              <input
                checked={remember}
                onChange={(event) => setRemember(event.target.checked)}
                type="checkbox"
              />
              <span className={styles.fakeCheck}>
                <AuthIcon name="check" />
              </span>
              Remember me
            </label>
            <button
              className={styles.textButton}
              onClick={handlePasswordReset}
              type="button"
            >
              Forgot password?
            </button>
          </div>

          <button
            className={styles.primaryButton}
            disabled={submitting}
            type="submit"
          >
            {submitting ? "Signing in..." : "Sign in securely"}
            {!submitting && <AuthIcon name="arrow-right" />}
          </button>

          <div className={styles.divider}>or</div>

          <button
            className={styles.secondaryButton}
            onClick={() => router.push("/")}
            type="button"
          >
            <AuthIcon name="globe" />
            Continue as guest
          </button>

          <p className={styles.switchCopy}>
            New patient? <Link href="/register">Create patient account</Link>
          </p>

          {status && (
            <p
              aria-live="polite"
              className={`${styles.message} ${status.kind === "success" ? styles.success : ""}`}
              role="status"
            >
              {status.message}
            </p>
          )}
        </form>
      </div>

      <div className={styles.securityNote}>
        <AuthIcon name="shield-check" />
        <span>
          Your credentials are validated through the clinic authentication
          service.
        </span>
      </div>
    </div>
  );
}
