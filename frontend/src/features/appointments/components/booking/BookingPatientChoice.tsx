"use client";

import { useId } from "react";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { formatDate } from "@/lib/format/formatDate";
import type { BookingPatient, BookingVariant } from "../../types/booking";
import { bookingSize } from "./bookingSizes";

// "Ai đi khám?": chọn một hồ sơ đã gắn với tài khoản. Luôn ghi tên, quan hệ và ngày sinh để
// không nhầm người (chỉ dùng khi có từ hai hồ sơ; một hồ sơ thì khối này không hỏi).

export function BookingPatientChoice({
  patients,
  value,
  onChange,
  variant,
  label,
}: {
  patients: readonly BookingPatient[];
  value: string | null;
  onChange: (patientId: string) => void;
  variant: BookingVariant;
  label: string;
}) {
  const sizes = bookingSize(variant);
  const groupId = useId();

  return (
    <RadioGroup
      aria-label={label}
      value={value ?? ""}
      onValueChange={(next: string) => onChange(next)}
      className="gap-0 divide-y rounded-lg border"
    >
      {patients.map((patient) => {
        const id = `${groupId}-${patient.id}`;
        const relation = patient.relationship
          ? CARE_RELATIONSHIP_LABELS[patient.relationship]
          : null;
        return (
          <div key={patient.id} className={`flex items-center gap-3 ${sizes.row}`}>
            <RadioGroupItem id={id} value={patient.id} />
            <Label
              htmlFor={id}
              className="grid min-w-0 flex-1 cursor-pointer gap-0.5 font-normal"
            >
              <span className={sizes.strong}>
                {relation ? `${patient.fullName} · ${relation}` : patient.fullName}
              </span>
              <span className={sizes.detail}>
                Sinh ngày {formatDate(patient.dateOfBirth)}
              </span>
            </Label>
          </div>
        );
      })}
    </RadioGroup>
  );
}
