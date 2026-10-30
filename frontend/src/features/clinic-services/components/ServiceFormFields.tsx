"use client";

import type { ReactNode } from "react";
import { FieldError } from "@/components/admin/FieldError";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Textarea } from "@/components/ui/textarea";
import {
  CATEGORY_LABELS,
  SERVICE_CATEGORIES,
  type ServiceCategory,
} from "@/lib/domain/clinicCategories";
import { APP_MESSAGES } from "@/lib/messages/appMessages";
import type { FieldErrorMap } from "../schema/serviceFieldRules";
import {
  emptyIndicatorDraft,
  type ServiceFormDraft,
} from "../schema/serviceFormDraft";
import {
  MODALITY_LABELS,
  SERVICE_MODALITIES,
  type ServiceModality,
} from "../types/serviceCatalogPrototype";
import { IndicatorTable } from "./IndicatorTable";

type ServiceFormFieldsProps = {
  mode: "create" | "edit";
  draft: ServiceFormDraft;
  onChange: (next: ServiceFormDraft) => void;
  errors: FieldErrorMap;
  disabled: boolean;
};

function Field({
  id,
  label,
  required,
  error,
  hint,
  children,
}: {
  id: string;
  label: string;
  required?: boolean;
  error?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>
        {label}
        {required ? <span aria-hidden> *</span> : null}
      </Label>
      {children}
      {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
      <FieldError
        id={`${id}-error`}
        message={error ? APP_MESSAGES[error] : null}
      />
    </div>
  );
}

/** Các ô của form dịch vụ; ô hiện/ẩn theo loại (khám, chẩn đoán hình ảnh, xét nghiệm). */
export function ServiceFormFields({
  mode,
  draft,
  onChange,
  errors,
  disabled,
}: ServiceFormFieldsProps) {
  const set = (patch: Partial<ServiceFormDraft>) =>
    onChange({ ...draft, ...patch });
  const invalid = (path: string) => (errors[path] ? true : undefined);
  const describedBy = (id: string, path: string) =>
    errors[path] ? `${id}-error` : undefined;

  function chooseType(type: ServiceCategory) {
    // Đổi loại xóa dữ liệu của loại cũ để không gửi nhầm trường không thuộc loại.
    onChange({
      ...draft,
      type,
      modality: null,
      bookingDetails: { ...draft.bookingDetails, slotMinutes: "" },
      indicators: type === "LABORATORY" ? [emptyIndicatorDraft()] : [],
    });
  }

  return (
    <div className="space-y-5">
      {mode === "create" ? (
        <fieldset className="space-y-2">
          <legend className="text-sm font-medium">
            Loại dịch vụ<span aria-hidden> *</span>
          </legend>
          <RadioGroup
            value={draft.type ?? ""}
            onValueChange={(value) => chooseType(value as ServiceCategory)}
            disabled={disabled}
            aria-label="Loại dịch vụ"
            className="gap-3 sm:flex sm:flex-wrap"
          >
            {SERVICE_CATEGORIES.map((category) => (
              <label key={category} className="flex items-center gap-2 text-sm">
                <RadioGroupItem
                  value={category}
                  aria-label={CATEGORY_LABELS[category]}
                />
                {CATEGORY_LABELS[category]}
              </label>
            ))}
          </RadioGroup>
          <FieldError
            message={errors.type ? APP_MESSAGES[errors.type] : null}
          />
        </fieldset>
      ) : (
        <div className="space-y-1">
          <p className="text-sm font-medium">Loại dịch vụ</p>
          <p className="text-sm">
            {draft.type ? CATEGORY_LABELS[draft.type] : "—"}
          </p>
          <p className="text-xs text-muted-foreground">
            Không đổi loại sau khi tạo.
          </p>
        </div>
      )}

      <Field id="service-name" label="Tên dịch vụ" required error={errors.name}>
        <Input
          id="service-name"
          value={draft.name}
          onChange={(event) => set({ name: event.target.value })}
          disabled={disabled}
          aria-invalid={invalid("name")}
          aria-describedby={describedBy("service-name", "name")}
        />
      </Field>

      <Field id="service-description" label="Mô tả">
        <Textarea
          id="service-description"
          value={draft.description}
          onChange={(event) => set({ description: event.target.value })}
          disabled={disabled}
          rows={3}
        />
      </Field>

      <Field
        id="service-price"
        label="Giá (VND)"
        required
        error={errors.price}
        hint={
          mode === "edit"
            ? "Giá mới chỉ áp dụng cho lịch hẹn và chỉ định tạo sau."
            : undefined
        }
      >
        <Input
          id="service-price"
          value={draft.price}
          onChange={(event) => set({ price: event.target.value })}
          disabled={disabled}
          inputMode="numeric"
          className="text-right tabular-nums"
          aria-invalid={invalid("price")}
          aria-describedby={describedBy("service-price", "price")}
        />
      </Field>

      {draft.type === "EXAMINATION" ? (
        <Field
          id="service-slot"
          label="Độ dài khung giờ (phút)"
          error={errors["bookingDetails.slotMinutes"]}
          hint="Tối thiểu 10 phút. Để trống nếu dùng khung giờ mặc định."
        >
          <Input
            id="service-slot"
            value={draft.bookingDetails.slotMinutes}
            onChange={(event) =>
              set({
                bookingDetails: {
                  ...draft.bookingDetails,
                  slotMinutes: event.target.value,
                },
              })
            }
            disabled={disabled}
            inputMode="numeric"
            className="w-32 text-right tabular-nums"
            aria-invalid={invalid("bookingDetails.slotMinutes")}
            aria-describedby={describedBy(
              "service-slot",
              "bookingDetails.slotMinutes",
            )}
          />
        </Field>
      ) : null}

      <Field id="service-patient-note" label="Ghi chú dặn bệnh nhân">
        <Textarea
          id="service-patient-note"
          value={draft.bookingDetails.patientNote}
          onChange={(event) =>
            set({
              bookingDetails: {
                ...draft.bookingDetails,
                patientNote: event.target.value,
              },
            })
          }
          disabled={disabled}
          rows={2}
        />
      </Field>

      {draft.type === "IMAGING" ? (
        <fieldset className="space-y-2">
          <legend className="text-sm font-medium">
            Phương thức<span aria-hidden> *</span>
          </legend>
          <RadioGroup
            value={draft.modality ?? ""}
            onValueChange={(value) =>
              set({ modality: value as ServiceModality })
            }
            disabled={disabled}
            aria-label="Phương thức"
            className="gap-3 sm:flex sm:flex-wrap"
          >
            {SERVICE_MODALITIES.map((modality) => (
              <label key={modality} className="flex items-center gap-2 text-sm">
                <RadioGroupItem
                  value={modality}
                  aria-label={MODALITY_LABELS[modality]}
                />
                {MODALITY_LABELS[modality]}
              </label>
            ))}
          </RadioGroup>
          <FieldError
            message={errors.modality ? APP_MESSAGES[errors.modality] : null}
          />
        </fieldset>
      ) : null}

      {draft.type === "LABORATORY" ? (
        <section className="space-y-2" aria-label="Chỉ số xét nghiệm">
          <h3 className="text-sm font-medium">
            Chỉ số xét nghiệm<span aria-hidden> *</span>
          </h3>
          {mode === "edit" ? (
            <p className="text-xs text-muted-foreground">
              Thay đổi chỉ áp dụng cho chỉ định tạo sau. Kết quả đã duyệt giữ
              nguyên.
            </p>
          ) : null}
          <IndicatorTable
            rows={draft.indicators}
            onChange={(indicators) => set({ indicators })}
            errors={errors}
            disabled={disabled}
          />
        </section>
      ) : null}
    </div>
  );
}
