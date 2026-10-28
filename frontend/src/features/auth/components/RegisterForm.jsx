"use client";
import { useState } from "react";
import Link from "next/link";
import { getSupabaseBrowserClient } from "@/lib/supabase/browser";
import { AuthIcon } from "./AuthIcon";
import styles from "./auth.module.css";
const emptyErrors = {
  fullName: false,
  email: false,
  phone: false,
  password: false,
  confirmPassword: false,
};
export function RegisterForm() {
  const [form, setForm] = useState({
    fullName: "",
    email: "",
    phone: "",
    password: "",
    confirmPassword: "",
  });
  const [errors, setErrors] = useState(emptyErrors);
  const [acceptedTerms, setAcceptedTerms] = useState(true);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [currentStep, setCurrentStep] = useState(1);
  const [status, setStatus] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  function updateField(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: false }));
  }
  async function handleSubmit(event) {
    event.preventDefault();
    const passwordIsValid =
      form.password.length >= 8 &&
      /\d/.test(form.password) &&
      /[^A-Za-z0-9]/.test(form.password);
    const nextErrors = {
      fullName: form.fullName.trim().length < 2,
      email: !/^\S+@\S+\.\S+$/.test(form.email.trim()),
      phone: !/^[+\d][\d\s()-]{7,}$/.test(form.phone.trim()),
      password: !passwordIsValid,
      confirmPassword: form.confirmPassword !== form.password,
    };
    setErrors(nextErrors);
    if (Object.values(nextErrors).some(Boolean) || !acceptedTerms) {
      setStatus({
        kind: "error",
        message: acceptedTerms
          ? "Check the highlighted fields before continuing."
          : "Accept the terms to continue.",
      });
      return;
    }
    setStatus(null);
    setSubmitting(true);
    try {
      const { error } = await getSupabaseBrowserClient().auth.signUp({
        email: form.email.trim(),
        password: form.password,
        options: {
          data: {
            account_type: "patient",
            full_name: form.fullName.trim(),
            phone: form.phone.trim(),
          },
          emailRedirectTo: `${window.location.origin}/workspace`,
        },
      });
      if (error) throw error;
      setCurrentStep(2);
      setStatus({
        kind: "success",
        message: "Account created. Check your email for the verification link.",
      });
    } catch (cause) {
      setStatus({
        kind: "error",
        message:
          cause instanceof Error
            ? cause.message
            : "Unable to create your account.",
      });
    } finally {
      setSubmitting(false);
    }
  }
  return (
    <div className={styles.registerContent}>
      <div className={styles.topLine}>
        <span className={styles.accountBadge}>Patient account</span>
        <p className={styles.signinCopy}>
          Already registered? <Link href="/login">Sign in</Link>
        </p>
      </div>

      <h1 className={styles.title}>Create your account</h1>
      <p className={styles.registerIntro}>
        Enter account details first. Email verification and profile completion
        follow.
      </p>

      <div className={styles.progress} aria-label="Account creation progress">
        {["Account", "Verify email", "Complete profile"].map((label, index) => (
          <div
            className={`${styles.progressStep} ${currentStep === index + 1 ? styles.current : ""}`}
            key={label}
          >
            <span className={styles.progressNumber}>{index + 1}</span>
            <span>{label}</span>
          </div>
        ))}
      </div>

      <form noValidate onSubmit={handleSubmit}>
        <div className={styles.formGrid}>
          <Field
            error={errors.fullName}
            icon="user"
            label="Full name"
            name="fullName"
            onChange={(value) => updateField("fullName", value)}
            placeholder="Enter your legal name"
            value={form.fullName}
          />
          <Field
            error={errors.email}
            icon="mail"
            label="Email address"
            name="email"
            onChange={(value) => updateField("email", value)}
            placeholder="name@example.com"
            type="email"
            value={form.email}
          />
          <Field
            error={errors.phone}
            full
            icon="phone"
            label="Mobile number"
            name="phone"
            onChange={(value) => updateField("phone", value)}
            placeholder="+84 000 000 000"
            type="tel"
            value={form.phone}
          />
          <Field
            error={errors.password}
            icon="lock"
            label="Password"
            name="password"
            onChange={(value) => updateField("password", value)}
            onToggle={() => setShowPassword((current) => !current)}
            placeholder="Create password"
            showPassword={showPassword}
            type="password"
            value={form.password}
          />
          <Field
            error={errors.confirmPassword}
            icon="shield-check"
            label="Confirm password"
            name="confirmPassword"
            onChange={(value) => updateField("confirmPassword", value)}
            onToggle={() => setShowConfirmation((current) => !current)}
            placeholder="Repeat password"
            showPassword={showConfirmation}
            type="password"
            value={form.confirmPassword}
          />
          <p className={styles.passwordHint}>
            <strong>Use 8+ characters</strong> with a number and a symbol.
          </p>
        </div>

        <div className={styles.terms}>
          <label className={styles.checkLabel}>
            <input
              checked={acceptedTerms}
              onChange={(event) => setAcceptedTerms(event.target.checked)}
              type="checkbox"
            />
            <span className={styles.fakeCheck}>
              <AuthIcon name="check" />
            </span>
            <span>
              I agree to the <Link href="/">Terms of Service</Link> and
              acknowledge the <Link href="/">Privacy Notice</Link>.
            </span>
          </label>
        </div>

        <div className={styles.actionRow}>
          <button
            className={styles.primaryButton}
            disabled={submitting || currentStep === 2}
            type="submit"
          >
            {submitting
              ? "Creating account..."
              : currentStep === 2
                ? "Verification email sent"
                : "Create account and send OTP"}
            {!submitting && currentStep === 1 && (
              <AuthIcon name="arrow-right" />
            )}
          </button>
          <div className={styles.staffNote}>
            <strong>Clinic staff:</strong> Employee accounts are issued by an
            Administrator or Clinic Owner.
          </div>
        </div>

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
  );
}
function Field({
  error,
  full,
  icon,
  label,
  name,
  onChange,
  onToggle,
  placeholder,
  showPassword,
  type = "text",
  value,
}) {
  const resolvedType = type === "password" && showPassword ? "text" : type;
  return (
    <label className={`${styles.field} ${full ? styles.fullField : ""}`}>
      <span>{label}</span>
      <span className={styles.inputShell}>
        <AuthIcon className={styles.fieldIcon} name={icon} />
        <input
          aria-invalid={error}
          autoComplete={
            name === "fullName"
              ? "name"
              : name === "phone"
                ? "tel"
                : name === "email"
                  ? "email"
                  : "new-password"
          }
          className={error ? styles.invalid : undefined}
          name={name}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
          type={resolvedType}
          value={value}
        />
        {type === "password" && (
          <button
            aria-label={showPassword ? "Hide password" : "Show password"}
            className={styles.iconButton}
            onClick={onToggle}
            type="button"
          >
            <AuthIcon name={showPassword ? "eye-off" : "eye"} />
          </button>
        )}
      </span>
    </label>
  );
}
