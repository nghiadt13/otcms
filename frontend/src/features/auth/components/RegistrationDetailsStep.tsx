"use client";

import { Mail, UserRound } from "lucide-react";
import { useRef, useState, type FormEvent } from "react";
import { registerPatient } from "../api/authApi";
import type { AuthMessageCode } from "../authMessages";
import { focusFirstInvalidField } from "../focusFirstInvalidField";
import {
  validateRegistration,
  type RegistrationErrors,
} from "../schema/authFieldRules";
import {
  toAuthMessageCode,
  type PatientRegistrationInput,
} from "../types/auth";
import { AuthAlert } from "./AuthAlert";
import { AuthSubmitButton } from "./AuthSubmitButton";
import { AuthTextField } from "./AuthTextField";
import { PasswordField } from "./PasswordField";
import { PasswordMatchHint } from "./PasswordMatchHint";
import { PasswordPolicyHint } from "./PasswordPolicyHint";
import { RegistrationConsentField } from "./RegistrationConsentField";

type TextKey = Exclude<keyof PatientRegistrationInput, "consent">;

const legendClass = "text-base font-semibold text-foreground";

// UC06 bước 1 (R3 sau sửa 23/10): họ tên (tên hiển thị), email, mật khẩu. Hồ sơ khám
// (ngày sinh, giới tính, SĐT) được thêm sau khi đăng nhập. Dữ liệu do lớp cha giữ để khi
// quay lại từ bước xác minh ("Đổi email") không phải nhập lại. Lỗi chỉ hiện khi rời ô (ô
// đã có nội dung) hoặc khi bấm "Tiếp tục"; sửa trường nào thì lỗi của trường đó được gỡ.
export function RegistrationDetailsStep({
  value,
  onChange,
  onRegistered,
}: {
  value: PatientRegistrationInput;
  onChange: (next: PatientRegistrationInput) => void;
  onRegistered: () => void;
}) {
  const formRef = useRef<HTMLFormElement>(null);
  const [errors, setErrors] = useState<RegistrationErrors>({});
  const [confirmTouched, setConfirmTouched] = useState(false);
  const [message, setMessage] = useState<AuthMessageCode | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function update<K extends keyof PatientRegistrationInput>(
    key: K,
    next: PatientRegistrationInput[K],
  ) {
    onChange({ ...value, [key]: next });
    if (errors[key]) setErrors((current) => ({ ...current, [key]: undefined }));
  }

  function validateOnBlur(key: TextKey) {
    if (!value[key]) return;
    const fieldError = validateRegistration(value)[key];
    setErrors((current) => ({ ...current, [key]: fieldError }));
  }

  const field = (key: TextKey) => ({
    value: value[key],
    error: errors[key],
    onChange: (event: { target: { value: string } }) =>
      update(key, event.target.value),
    onBlur: () => validateOnBlur(key),
  });

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = validateRegistration(value);
    setErrors(next);
    if (Object.keys(next).length > 0) {
      setMessage("MSG-08");
      focusFirstInvalidField(formRef.current);
      return;
    }

    setMessage(null);
    setSubmitting(true);
    try {
      await registerPatient(value);
      onRegistered();
    } catch (cause) {
      setMessage(toAuthMessageCode(cause));
      setSubmitting(false);
    }
  }

  return (
    <form
      className="mt-6 space-y-6"
      noValidate
      onSubmit={handleSubmit}
      ref={formRef}
    >
      {message && <AuthAlert code={message} tone="error" />}

      <fieldset className="space-y-4">
        <legend className={legendClass}>Tài khoản</legend>
        <AuthTextField
          {...field("fullName")}
          icon={UserRound}
          autoComplete="name"
          hint="Tên hiển thị trong tài khoản của bạn."
          label="Họ và tên"
          placeholder="Nguyễn Văn An"
          required
        />
        <AuthTextField
          {...field("email")}
          icon={Mail}
          autoComplete="email"
          hint="Mã xác minh sẽ gửi tới email này."
          inputMode="email"
          label="Email"
          placeholder="ten@email.com"
          required
          type="email"
        />
      </fieldset>

      <fieldset className="space-y-4">
        <legend className={legendClass}>Mật khẩu</legend>
        <div>
          <PasswordField
            {...field("password")}
            autoComplete="new-password"
            label="Mật khẩu"
            required
          />
          <PasswordPolicyHint password={value.password} />
        </div>
        <div>
          <PasswordField
            {...field("confirmPassword")}
            autoComplete="new-password"
            label="Nhập lại mật khẩu"
            onBlur={() => setConfirmTouched(true)}
            required
          />
          {!errors.confirmPassword && (
            <PasswordMatchHint
              confirmation={value.confirmPassword}
              password={value.password}
              touched={confirmTouched}
            />
          )}
        </div>
      </fieldset>

      <RegistrationConsentField
        checked={value.consent}
        error={errors.consent}
        onChange={(next) => update("consent", next)}
      />

      <AuthSubmitButton submitting={submitting} submittingLabel="Đang gửi...">
        Tiếp tục
      </AuthSubmitButton>
    </form>
  );
}
