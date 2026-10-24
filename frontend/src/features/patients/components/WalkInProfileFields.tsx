"use client";

import { useId } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import type { PatientFieldErrors } from "../schema/patientFieldRules";
import type { WalkInFormValues } from "../schema/patientRequestRules";
import { DateOfBirthField, PatientTextField } from "./PatientFormField";

type WalkInProfileFieldsProps = {
  values: WalkInFormValues;
  errors: PatientFieldErrors;
  disabled: boolean;
  onChange: (patch: Partial<WalkInFormValues>) => void;
};

/**
 * Các ô của hồ sơ vãng lai (UC10). Giới tính không chọn sẵn: nhầm giới tính là nhầm dữ liệu
 * y tế, nên lễ tân phải chọn. Email không bắt buộc, kèm câu hỏi lễ tân nêu cho bệnh nhân.
 */
export function WalkInProfileFields({
  values,
  errors,
  disabled,
  onChange,
}: WalkInProfileFieldsProps) {
  const sexLabelId = useId();
  return (
    <div className="grid gap-4">
      <PatientTextField
        label="Họ và tên"
        required
        autoComplete="off"
        value={values.fullName}
        onChange={(event) => onChange({ fullName: event.target.value })}
        error={errors.fullName}
        disabled={disabled}
      />

      <div className="grid gap-4 sm:grid-cols-2">
        <DateOfBirthField
          label="Ngày sinh"
          required
          value={values.dateOfBirth}
          onChange={(dateOfBirth) => onChange({ dateOfBirth })}
          error={errors.dateOfBirth}
          disabled={disabled}
        />
        <div className="grid gap-1.5">
          <Label id={sexLabelId}>
            Giới tính<span aria-hidden="true"> *</span>
          </Label>
          <RadioGroup
            aria-labelledby={sexLabelId}
            value={values.sex}
            onValueChange={(sex) => onChange({ sex: String(sex) })}
            disabled={disabled}
            className="gap-2 sm:flex sm:items-center sm:gap-6"
          >
            <Label className="font-normal">
              <RadioGroupItem value="MALE" />
              Nam
            </Label>
            <Label className="font-normal">
              <RadioGroupItem value="FEMALE" />
              Nữ
            </Label>
          </RadioGroup>
          <FieldError message={errors.sex} />
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <PatientTextField
          label="Số điện thoại"
          required
          type="tel"
          inputMode="tel"
          autoComplete="off"
          placeholder="0912 345 678"
          value={values.phone}
          onChange={(event) => onChange({ phone: event.target.value })}
          error={errors.phone}
          disabled={disabled}
        />
        <PatientTextField
          label="Số CCCD / định danh"
          autoComplete="off"
          help="Trẻ em: mã định danh hoặc số hộ chiếu. Không bắt buộc."
          value={values.citizenId}
          onChange={(event) => onChange({ citizenId: event.target.value })}
          error={errors.citizenId}
          disabled={disabled}
        />
      </div>

      <PatientTextField
        label="Địa chỉ"
        required
        autoComplete="off"
        value={values.address}
        onChange={(event) => onChange({ address: event.target.value })}
        error={errors.address}
        disabled={disabled}
      />

      <PatientTextField
        label="Email (không bắt buộc)"
        type="email"
        autoComplete="off"
        help="Hỏi bệnh nhân có đồng ý ghi email để sau này tự xem kết quả online không?"
        value={values.email}
        onChange={(event) => onChange({ email: event.target.value })}
        error={errors.email}
        disabled={disabled}
      />
    </div>
  );
}
