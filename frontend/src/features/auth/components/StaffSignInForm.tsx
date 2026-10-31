"use client";

import { Mail } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { signOut } from "@/lib/auth/authSession";
import { AuthRequestError } from "../types/auth";
import { signInStaff } from "../api/authApi";
import { authMessages } from "../authMessages";
import { validateEmail } from "../schema/authFieldRules";
import { AuthSubmitButton } from "./AuthSubmitButton";
import { AuthTextField } from "./AuthTextField";
import { PasswordField } from "./PasswordField";

/** Login riêng cho Staff Portal; vai trò/quyền luôn do backend xác thực. */
export function StaffSignInForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (validateEmail(email) || !password) {
      setError(authMessages["MSG-01"]);
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      await signOut();
      await signInStaff(email, password);
      router.replace("/admin");
    } catch (cause) {
      setError(
        cause instanceof AuthRequestError
          ? authMessages[cause.code]
          : authMessages["MSG-04"],
      );
      setSubmitting(false);
    }
  }

  return (
    <form className="mt-8 space-y-5" noValidate onSubmit={handleSubmit}>
      {error && (
        <div
          role="alert"
          className="rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-sm text-foreground"
        >
          {error}
        </div>
      )}
      <AuthTextField
        autoComplete="email"
        icon={Mail}
        inputMode="email"
        label="Email"
        onChange={(event) => setEmail(event.target.value)}
        placeholder="ten@email.com"
        required
        type="email"
        value={email}
      />
      <PasswordField
        autoComplete="current-password"
        label="Mật khẩu"
        onChange={(event) => setPassword(event.target.value)}
        required
        value={password}
      />
      <AuthSubmitButton
        submitting={submitting}
        submittingLabel="Đang đăng nhập..."
      >
        Đăng nhập nội bộ
      </AuthSubmitButton>
    </form>
  );
}
