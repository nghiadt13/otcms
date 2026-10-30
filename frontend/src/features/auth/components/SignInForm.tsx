"use client";

import { Mail, UserRound } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  clearMockSignedInKind,
  setMockSignedInKind,
} from "@/lib/session/mockSignedInKind";
import { signIn, signInStaff, signInAdministrator } from "../api/authApi";
import type { AuthMessageCode } from "../authMessages";
import { focusFirstInvalidField } from "../focusFirstInvalidField";
import { safePortalNext } from "../safePortalNext";
import { validateEmail, validateStaffUsername } from "../schema/authFieldRules";
import { toAuthMessageCode } from "../types/auth";
import { AuthAlert } from "./AuthAlert";
import { authLinkClass } from "./AuthCard";
import { AuthSubmitButton } from "./AuthSubmitButton";
import { AuthTextField } from "./AuthTextField";
import { Button } from "@/components/ui/button";
import { PasswordField } from "./PasswordField";

// UC01 Đăng nhập bệnh nhân (email + mật khẩu). Mọi trường hợp bị từ chối (thiếu thông tin,
// sai mật khẩu, tài khoản không đủ điều kiện) đều hiện cùng MSG-01 để không lộ tài khoản
// nào tồn tại; lỗi dịch vụ hiện MSG-04. Thành công thì về `next` nếu là đường dẫn an toàn
// trong khu bệnh nhân, không thì về trang chủ `/`. `defaultEmail` dùng khi vừa đăng ký xong (UC06).
// `staff` là màn đăng nhập nội bộ (/staff-login): gọi endpoint nhân viên rồi về /workspace, nơi
// `RoleHomeRedirect` chuyển tới khu đúng vai trò (quản trị hoặc lễ tân).
export function SignInForm({
  defaultEmail = "",
  next,
  staff = false,
  submitLabel = "Đăng nhập",
}: {
  defaultEmail?: string;
  next?: string;
  staff?: boolean;
  submitLabel?: string;
}) {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const [email, setEmail] = useState(defaultEmail);
  const [staffMode, setStaffMode] = useState<"administrator" | "staff">(
    "administrator",
  );
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{
    email?: string;
    password?: string;
  }>({});
  const [message, setMessage] = useState<AuthMessageCode | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Mở màn đăng nhập nghĩa là chưa có phiên (đăng xuất cũng đi qua đây): bỏ phiên giả của khu bệnh nhân.
  useEffect(() => clearMockSignedInKind(), []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = {
      email: staff ? validateStaffUsername(email) : validateEmail(email),
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
        const session =
          staffMode === "administrator"
            ? await signInAdministrator(email, password)
            : await signInStaff(email, password);
        router.replace(
          session.mustChangePassword ? "/first-login-password" : "/workspace",
        );
        return;
      }
      await signIn(email, password);
      // Giả lập: phiên thật do backend cấp (lib/auth/authSession); ghi loại tài khoản giả để `/api/v1/me` giả trả đúng bệnh nhân.
      setMockSignedInKind("PATIENT");
      router.replace(safePortalNext(next));
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

      {staff && (
        <fieldset className="space-y-2">
          <legend className="text-base font-medium">Loại tài khoản</legend>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            <Button
              type="button"
              variant={staffMode === "administrator" ? "secondary" : "outline"}
              aria-pressed={staffMode === "administrator"}
              onClick={() => {
                setStaffMode("administrator");
                setEmail("");
                setPassword("");
                setFieldErrors({});
                setMessage(null);
              }}
            >
              Admin / Chủ phòng khám
            </Button>
            <Button
              type="button"
              variant={staffMode === "staff" ? "secondary" : "outline"}
              aria-pressed={staffMode === "staff"}
              onClick={() => {
                setStaffMode("staff");
                setEmail("");
                setPassword("");
                setFieldErrors({});
                setMessage(null);
              }}
            >
              Nhân viên khác
            </Button>
          </div>
        </fieldset>
      )}

      <AuthTextField
        autoComplete={staff ? "username" : "email"}
        error={fieldErrors.email}
        icon={staff ? UserRound : Mail}
        inputMode={staff ? "text" : "email"}
        label={
          staff
            ? staffMode === "administrator"
              ? "Tên đăng nhập"
              : "Tên đăng nhập hoặc email"
            : "Email"
        }
        onChange={(event) => {
          setEmail(event.target.value);
          setFieldErrors((current) => ({ ...current, email: undefined }));
        }}
        placeholder={staff ? "ten.dang.nhap" : "ten@email.com"}
        required
        type={staff ? "text" : "email"}
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
        {!staff && (
          <div className="mt-2 flex justify-end">
            <Link
              className={`text-base ${authLinkClass}`}
              href="/forgot-password"
            >
              Quên mật khẩu?
            </Link>
          </div>
        )}
      </div>

      <AuthSubmitButton
        submitting={submitting}
        submittingLabel="Đang đăng nhập..."
      >
        {submitLabel}
      </AuthSubmitButton>
    </form>
  );
}
