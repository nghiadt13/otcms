"use client";

import { Check, ChevronsUpDown } from "lucide-react";
import { useState } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { DOCTOR_SPECIALTY_OPTIONS } from "../schema/doctorSpecialtyOptions";
import type { StaffProfileFieldErrors } from "../schema/staffProfileFieldRules";
import { FIELD_ERROR_TEXT, fieldDomId } from "../schema/staffProfileFormValues";
import type { DoctorInfo } from "../types/staffProfile";

type DoctorFieldsProps = {
  value: DoctorInfo;
  errors: StaffProfileFieldErrors;
  onChange: (patch: Partial<DoctorInfo>) => void;
  disabled?: boolean;
  /** Sheet sửa đã có Switch riêng ở chế độ xem nên ẩn Switch trong form. */
  showPublicSwitch?: boolean;
};

/** Nhóm trường chỉ dành cho Bác sĩ chỉnh hình. */
export function DoctorFields({
  value,
  errors,
  onChange,
  disabled,
  showPublicSwitch = true,
}: DoctorFieldsProps) {
  const [open, setOpen] = useState(false);
  const toggle = (name: string) =>
    onChange({
      specialties: value.specialties.includes(name)
        ? value.specialties.filter((item) => item !== name)
        : [...value.specialties, name],
    });
  // Giữ cả chuyên khoa đã lưu nhưng không còn trong danh mục tạm.
  const options = [
    ...DOCTOR_SPECIALTY_OPTIONS,
    ...value.specialties.filter(
      (item) => !(DOCTOR_SPECIALTY_OPTIONS as readonly string[]).includes(item),
    ),
  ];
  const textError = (key: keyof typeof FIELD_ERROR_TEXT) =>
    errors[key as keyof StaffProfileFieldErrors] ? FIELD_ERROR_TEXT[key] : null;

  return (
    <fieldset className="grid gap-4 border-t pt-4" disabled={disabled}>
      <legend className="sr-only">Thông tin bác sĩ</legend>
      <div>
        <Label>Chuyên khoa</Label>
        <Popover open={open} onOpenChange={setOpen}>
          <PopoverTrigger
            render={
              <Button
                type="button"
                variant="outline"
                role="combobox"
                aria-expanded={open}
                className="mt-1.5 w-full justify-between font-normal"
              />
            }
          >
            <span className="truncate">
              {value.specialties.length > 0
                ? value.specialties.join(", ")
                : "Chọn một hoặc nhiều chuyên khoa"}
            </span>
            <ChevronsUpDown aria-hidden className="text-muted-foreground" />
          </PopoverTrigger>
          <PopoverContent align="start" className="w-(--anchor-width) p-0">
            <Command>
              <CommandInput placeholder="Tìm chuyên khoa" />
              <CommandList>
                <CommandEmpty>Không có chuyên khoa khớp từ khóa.</CommandEmpty>
                <CommandGroup>
                  {options.map((name) => (
                    <CommandItem
                      key={name}
                      value={name}
                      onSelect={() => toggle(name)}
                    >
                      <Check
                        aria-hidden
                        className={
                          value.specialties.includes(name) ? "" : "invisible"
                        }
                      />
                      {name}
                    </CommandItem>
                  ))}
                </CommandGroup>
              </CommandList>
            </Command>
          </PopoverContent>
        </Popover>
      </div>
      <div>
        <Label htmlFor={fieldDomId("doctor.qualifications")}>Trình độ</Label>
        <Input
          id={fieldDomId("doctor.qualifications")}
          className="mt-1.5"
          value={value.qualifications}
          aria-invalid={errors["doctor.qualifications"] ? true : undefined}
          onChange={(event) => onChange({ qualifications: event.target.value })}
        />
        <FieldError message={textError("doctor.qualifications")} />
      </div>
      <div>
        <Label htmlFor={fieldDomId("doctor.biography")}>Tiểu sử</Label>
        <Textarea
          id={fieldDomId("doctor.biography")}
          className="mt-1.5"
          rows={3}
          value={value.biography}
          aria-invalid={errors["doctor.biography"] ? true : undefined}
          onChange={(event) => onChange({ biography: event.target.value })}
        />
        <FieldError message={textError("doctor.biography")} />
      </div>
      <div>
        <Label htmlFor={fieldDomId("doctor.consultationInfo")}>
          Thông tin khám
        </Label>
        <Textarea
          id={fieldDomId("doctor.consultationInfo")}
          className="mt-1.5"
          rows={3}
          placeholder="Lịch tư vấn, lưu ý khi đến khám"
          value={value.consultationInfo}
          aria-invalid={errors["doctor.consultationInfo"] ? true : undefined}
          onChange={(event) => onChange({ consultationInfo: event.target.value })}
        />
        <FieldError message={textError("doctor.consultationInfo")} />
      </div>
      {showPublicSwitch ? (
        <div className="flex items-center justify-between gap-4">
          <div>
            <Label htmlFor="staff-field-isPublic">Hiển thị công khai</Label>
            <p className="mt-0.5 text-xs text-muted-foreground">
              {value.isPublic
                ? "Bác sĩ sẽ hiện trên website sau khi lưu."
                : "Tắt: bác sĩ chưa hiện trên website."}
            </p>
          </div>
          <Switch
            id="staff-field-isPublic"
            checked={value.isPublic}
            onCheckedChange={(checked) => onChange({ isPublic: checked })}
          />
        </div>
      ) : null}
    </fieldset>
  );
}
