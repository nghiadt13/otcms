"use client";

import { useId } from "react";
import { formatDateOfBirthText } from "../schema/dateOfBirthText";
import type {
  ProfileFieldErrors,
  ProfileFieldValues,
} from "../schema/patientProfileFieldRules";
import type { EditableProfileField } from "../types/patientProfile";
import { ProfileSexField } from "./ProfileSexField";
import { ProfileTextField } from "./ProfileTextField";

const LOCKED_HINT =
  "Không sửa trên web vì hồ sơ đã có lượt khám. Nhờ lễ tân sửa tại quầy.";

/** Bảy trường hành chính dùng chung cho form tạo và form sửa hồ sơ. */
export function ProfileFieldInputs({
  values,
  errors,
  editable,
  disabled = false,
  onChange,
}: {
  values: ProfileFieldValues;
  errors: ProfileFieldErrors;
  /** Trường người dùng được sửa. Trường ngoài danh sách hiện chỉ đọc. */
  editable: readonly EditableProfileField[];
  disabled?: boolean;
  onChange: <K extends keyof ProfileFieldValues>(
    field: K,
    value: ProfileFieldValues[K],
  ) => void;
}) {
  const id = useId();
  const isEditable = (field: EditableProfileField) =>
    !disabled && editable.includes(field);
  const lockedHint = (field: EditableProfileField) =>
    editable.includes(field) ? undefined : LOCKED_HINT;

  return (
    <>
      <ProfileTextField
        disabled={!isEditable("fullName")}
        error={errors.fullName}
        hint={lockedHint("fullName")}
        id={`${id}-fullName`}
        label="Họ và tên"
        onChange={(next) => onChange("fullName", next)}
        value={values.fullName}
      />
      <ProfileTextField
        disabled={!isEditable("dateOfBirth")}
        error={errors.dateOfBirth}
        hint={lockedHint("dateOfBirth") ?? "Gõ liền, ví dụ 20051990."}
        id={`${id}-dateOfBirth`}
        inputMode="numeric"
        label="Ngày sinh (dd/mm/yyyy)"
        onChange={(next) =>
          onChange("dateOfBirth", formatDateOfBirthText(next))
        }
        placeholder="dd/mm/yyyy"
        value={values.dateOfBirth}
      />
      <ProfileSexField
        disabled={!isEditable("sex")}
        error={errors.sex}
        id={`${id}-sex`}
        onChange={(next) => onChange("sex", next)}
        value={values.sex}
      />
      <ProfileTextField
        autoComplete="tel"
        disabled={!isEditable("phone")}
        error={errors.phone}
        id={`${id}-phone`}
        inputMode="tel"
        label="Số điện thoại"
        onChange={(next) => onChange("phone", next)}
        value={values.phone}
      />
      <ProfileTextField
        disabled={!isEditable("address")}
        error={errors.address}
        id={`${id}-address`}
        label="Địa chỉ"
        onChange={(next) => onChange("address", next)}
        value={values.address}
      />
      <ProfileTextField
        disabled={!isEditable("citizenId")}
        error={errors.citizenId}
        id={`${id}-citizenId`}
        label="CCCD/Mã định danh (không bắt buộc)"
        onChange={(next) => onChange("citizenId", next)}
        value={values.citizenId}
      />
      <ProfileTextField
        autoComplete="email"
        disabled={!isEditable("email")}
        error={errors.email}
        id={`${id}-email`}
        inputMode="email"
        label="Email (không bắt buộc)"
        onChange={(next) => onChange("email", next)}
        type="email"
        value={values.email}
      />
    </>
  );
}
