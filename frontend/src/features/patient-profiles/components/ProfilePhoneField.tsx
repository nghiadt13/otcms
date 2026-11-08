"use client";

import { localPhoneFromInput } from "../schema/patientProfileFieldChecks";
import { ProfileTextField, type ProfileTextFieldProps } from "./ProfileTextField";

/**
 * Ô số điện thoại có sẵn "+84": người dùng chỉ gõ 9 số còn lại. Giá trị của form vẫn là số
 * nội địa "0xxxxxxxxx" (dạng BE lưu), nên chỉ phần hiển thị và phần gõ vào được chuyển đổi.
 */
export function ProfilePhoneField({
  value,
  onChange,
  ...rest
}: Omit<ProfileTextFieldProps, "prefix" | "inputMode" | "maxLength">) {
  return (
    <ProfileTextField
      {...rest}
      inputMode="tel"
      maxLength={16}
      onChange={(raw) => onChange(localPhoneFromInput(raw))}
      placeholder="912 345 678"
      prefix={<span className="font-medium">+84</span>}
      value={value.startsWith("0") ? value.slice(1) : value}
    />
  );
}
