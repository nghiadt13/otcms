import { FieldError } from "@/components/admin/FieldError";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { StaffProfileFieldErrors } from "../schema/staffProfileFieldRules";
import { FIELD_ERROR_TEXT, fieldDomId } from "../schema/staffProfileFormValues";

type StaffCommonFieldsProps = {
  displayName: string;
  workContact: string;
  errors: StaffProfileFieldErrors;
  onChange: (key: "displayName" | "workContact", value: string) => void;
  disabled?: boolean;
};

/** Hai ô bắt buộc của mọi vai trò: họ tên hiển thị và liên hệ công việc. */
export function StaffCommonFields({
  displayName,
  workContact,
  errors,
  onChange,
  disabled,
}: StaffCommonFieldsProps) {
  return (
    <>
      <div>
        <Label htmlFor={fieldDomId("displayName")}>Họ tên hiển thị *</Label>
        <Input
          id={fieldDomId("displayName")}
          className="mt-1.5"
          value={displayName}
          maxLength={100}
          disabled={disabled}
          aria-invalid={errors.displayName ? true : undefined}
          aria-describedby={errors.displayName ? "err-displayName" : undefined}
          onChange={(event) => onChange("displayName", event.target.value)}
        />
        <FieldError
          id="err-displayName"
          message={errors.displayName ? FIELD_ERROR_TEXT.displayName : null}
        />
      </div>
      <div>
        <Label htmlFor={fieldDomId("workContact")}>Liên hệ công việc *</Label>
        <Input
          id={fieldDomId("workContact")}
          className="mt-1.5"
          value={workContact}
          maxLength={100}
          placeholder="Số điện thoại hoặc email công việc"
          disabled={disabled}
          aria-invalid={errors.workContact ? true : undefined}
          aria-describedby={errors.workContact ? "err-workContact" : undefined}
          onChange={(event) => onChange("workContact", event.target.value)}
        />
        <FieldError
          id="err-workContact"
          message={errors.workContact ? FIELD_ERROR_TEXT.workContact : null}
        />
      </div>
    </>
  );
}
