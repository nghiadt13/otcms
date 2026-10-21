"use client";

import { Mail } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";
import { signIn, signInStaff } from "../api/authApi";
import type { AuthMessageCode } from "../authMessages";
import { focusFirstInvalidField } from "../focusFirstInvalidField";
import { validateEmail, validateStaffEmail } from "../schema/authFieldRules";
import { toAuthMessageCode } from "../types/auth";
import { AuthAlert } from "./AuthAlert";
import { authLinkClass } from "./AuthCard";
import { AuthSubmitButton } from "./AuthSubmitButton";
import { AuthTextField } from "./AuthTextField";
import { PasswordField } from "./PasswordField";

// UC01 Đăng nhập. Mọi trường hợp bị từ chối (thiếu thông tin, sai mật khẩu, tài
// khoản không đủ điều kiện) đều hiện cùng MSG-01 để không lộ tài khoản nào tồn
// tại; lỗi dịch vụ hiện MSG-04. `defaultEmail` dùng khi vừa đăng ký xong (UC06).
export function SignInForm({
  defaultEmail = "",
  staff = false,
}: {
  defaultEmail?: string;
  staff?: boolean;
}) {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const [email, setEmail] = useState(defaultEmail);
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{
    email?: string;
    password?: string;
  }>({});
  const [message, setMessage] = useState<AuthMessageCode | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = {
      email: staff ? validateStaffEmail(email) : validateEmail(email),
      password: password ? undefined : "Nhập mật khẩu.",
    };
    setFieldErrors(errors);
    if (errors.email || errors.password) {
      setMessage("MSG-01");
      focusFirstInvalidField(formRef.current);
      return;
    }

    setMessage(null);
    setSubmitting(true);
    try {
      if (staff) {
        await signInStaff(email, password);
        router.replace("/admin");
      } else {
        await signIn(email, password);
        router.replace("/workspace");
      }
    } catch (cause) {
      setMessage(toAuthMessageCode(cause));
      setSubmitting(false);
    }
  }

  return (
    <form
      className="mt-8 space-y-5"
      noValidate
      onSubmit={handleSubmit}
      ref={formRef}
    >
      {message && <AuthAlert code={message} tone="error" />}

      <AuthTextField
        autoComplete="email"
        error={fieldErrors.email}
        icon={Mail}
        inputMode="email"
        label="Email"
        onChange={(event) => {
          setEmail(event.target.value);
          setFieldErrors((current) => ({ ...current, email: undefined }));
        }}
        placeholder="ten@email.com"
        required
        type="email"
        value={email}
      />

      <div>
        <PasswordField
          autoComplete="current-password"
          error={fieldErrors.password}
          label="Mật khẩu"
          onChange={(event) => {
            setPassword(event.target.value);
            setFieldErrors((current) => ({ ...current, password: undefined }));
          }}
          required
          value={password}
        />
        <div className="mt-2 flex justify-end">
          <Link
            className={`text-base ${authLinkClass}`}
            href="/forgot-password"
          >
            Quên mật khẩu?
          </Link>
        </div>
      </div>

      <AuthSubmitButton
        submitting={submitting}
        submittingLabel="Đang đăng nhập..."
      >
        Đăng nhập
      </AuthSubmitButton>
    </form>
  );
}
