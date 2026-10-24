import type { PatientFieldErrors } from "../schema/patientFieldRules";
import { DateOfBirthField, PatientTextField } from "./PatientFormField";

type PhoneDobFieldsProps = {
  phone: string;
  /** dd/mm/yyyy như đang gõ */
  dateOfBirth: string;
  errors: PatientFieldErrors;
  disabled: boolean;
  autoFocusPhone: boolean;
  onPhoneChange: (phone: string) => void;
  onDateOfBirthChange: (dateOfBirth: string) => void;
};

/** Hai ô tìm theo SĐT và ngày sinh; trên màn hẹp xếp dọc, ô chiếm hết chiều ngang. */
export function PhoneDobFields({
  phone,
  dateOfBirth,
  errors,
  disabled,
  autoFocusPhone,
  onPhoneChange,
  onDateOfBirthChange,
}: PhoneDobFieldsProps) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <PatientTextField
        label="Số điện thoại"
        type="tel"
        inputMode="tel"
        autoComplete="off"
        placeholder="0912 345 678"
        value={phone}
        onChange={(event) => onPhoneChange(event.target.value)}
        error={errors.phone}
        disabled={disabled}
        autoFocus={autoFocusPhone}
      />
      <DateOfBirthField
        label="Ngày sinh"
        value={dateOfBirth}
        onChange={onDateOfBirthChange}
        error={errors.dateOfBirth}
        disabled={disabled}
      />
    </div>
  );
}
