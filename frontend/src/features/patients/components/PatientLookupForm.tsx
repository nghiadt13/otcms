import type { FormEvent, Ref } from "react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import type { PatientFieldErrors } from "../schema/patientFieldRules";
import type { PatientSearchMode } from "../types/patient";
import { PatientTextField } from "./PatientFormField";
import { PhoneDobFields } from "./PhoneDobFields";

/** Giá trị các ô của khối tìm, đúng như người dùng gõ (chưa chuẩn hóa). */
export interface LookupFormValues {
  mode: PatientSearchMode;
  phone: string;
  /** dd/mm/yyyy */
  dateOfBirth: string;
  patientCode: string;
  fullName: string;
}

type PatientLookupFormProps = {
  values: LookupFormValues;
  errors: PatientFieldErrors;
  /** Lần tìm trước trả MULTIPLE: hiện ô họ tên. */
  askFullName: boolean;
  searching: boolean;
  autoFocus: boolean;
  fullNameRef: Ref<HTMLInputElement>;
  onModeChange: (mode: PatientSearchMode) => void;
  onChange: (patch: Partial<LookupFormValues>) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

/** Form tìm: chọn cách tìm, các ô tương ứng, nút Tìm (Enter cũng gửi). Không có nút `default`. */
export function PatientLookupForm({
  values,
  errors,
  askFullName,
  searching,
  autoFocus,
  fullNameRef,
  onModeChange,
  onChange,
  onSubmit,
}: PatientLookupFormProps) {
  const byPhone = values.mode === "phone";
  return (
    <form className="grid gap-4" onSubmit={onSubmit} noValidate>
      <fieldset className="grid gap-2">
        <legend className="text-sm font-medium">Tìm theo</legend>
        <RadioGroup
          aria-label="Tìm theo"
          value={values.mode}
          onValueChange={(value) =>
            onModeChange(value === "code" ? "code" : "phone")
          }
          disabled={searching}
          className="gap-2 sm:flex sm:gap-6"
        >
          <Label className="font-normal">
            <RadioGroupItem value="phone" />
            Số điện thoại và ngày sinh
          </Label>
          <Label className="font-normal">
            <RadioGroupItem value="code" />
            Mã bệnh nhân
          </Label>
        </RadioGroup>
      </fieldset>

      {byPhone ? (
        <PhoneDobFields
          phone={values.phone}
          dateOfBirth={values.dateOfBirth}
          errors={errors}
          disabled={searching}
          autoFocusPhone={autoFocus}
          onPhoneChange={(phone) => onChange({ phone })}
          onDateOfBirthChange={(dateOfBirth) => onChange({ dateOfBirth })}
        />
      ) : (
        <PatientTextField
          label="Mã bệnh nhân"
          autoComplete="off"
          autoCapitalize="characters"
          placeholder="BN-000123"
          value={values.patientCode}
          onChange={(event) => onChange({ patientCode: event.target.value })}
          error={errors.patientCode}
          disabled={searching}
          autoFocus={autoFocus}
        />
      )}

      {byPhone && askFullName ? (
        <PatientTextField
          ref={fullNameRef}
          label="Họ và tên"
          autoComplete="off"
          placeholder="Họ và tên đầy đủ"
          value={values.fullName}
          onChange={(event) => onChange({ fullName: event.target.value })}
          error={errors.fullName}
          disabled={searching}
        />
      ) : null}

      <div className="flex justify-end">
        <Button type="submit" variant="outline" disabled={searching}>
          {searching ? "Đang tìm..." : "Tìm"}
        </Button>
      </div>
    </form>
  );
}
