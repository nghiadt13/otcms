import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import type {
  ClinicFormFields,
  WebsiteContentFormErrors,
} from "../schema/websiteContentFieldRules";
import { WebsiteContentField } from "./WebsiteContentField";

type Props = {
  value: ClinicFormFields;
  errors: WebsiteContentFormErrors;
  onChange: (patch: Partial<ClinicFormFields>) => void;
};

/** Năm trường có cấu trúc của loại Thông tin phòng khám. Khóa lỗi dạng `body.<trường>`. */
export function WebsiteContentClinicInfoFields({ value, errors, onChange }: Props) {
  const text = (key: keyof ClinicFormFields) => ({
    value: value[key],
    onChange: (event: { target: { value: string } }) =>
      onChange({ [key]: event.target.value }),
    "aria-invalid": Boolean(errors[`body.${key}`]),
    "aria-describedby": errors[`body.${key}`] ? `clinic-${key}-error` : undefined,
  });
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <WebsiteContentField
        id="clinic-clinicName"
        label="Tên phòng khám"
        required
        error={errors["body.clinicName"]}
      >
        <Input id="clinic-clinicName" autoComplete="off" {...text("clinicName")} />
      </WebsiteContentField>
      <WebsiteContentField
        id="clinic-hotline"
        label="Hotline"
        required
        error={errors["body.hotline"]}
      >
        <Input id="clinic-hotline" type="tel" autoComplete="off" {...text("hotline")} />
      </WebsiteContentField>
      <div className="sm:col-span-2">
        <WebsiteContentField
          id="clinic-address"
          label="Địa chỉ"
          required
          error={errors["body.address"]}
        >
          <Input id="clinic-address" autoComplete="off" {...text("address")} />
        </WebsiteContentField>
      </div>
      <div className="sm:col-span-2">
        <WebsiteContentField
          id="clinic-email"
          label="Email liên hệ"
          error={errors["body.email"]}
        >
          <Input id="clinic-email" type="email" autoComplete="off" {...text("email")} />
        </WebsiteContentField>
      </div>
      <div className="sm:col-span-2">
        <WebsiteContentField
          id="clinic-openingHours"
          label="Giờ mở cửa"
          required
          hint="Mỗi dòng một khung giờ, ví dụ: Thứ 2 – Thứ 6: 7:30 – 17:00"
          error={errors["body.openingHours"]}
        >
          <Textarea
            id="clinic-openingHours"
            rows={3}
            {...text("openingHours")}
            aria-describedby="clinic-openingHours-hint clinic-openingHours-error"
          />
        </WebsiteContentField>
      </div>
    </div>
  );
}
