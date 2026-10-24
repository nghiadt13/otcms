"use client";

import { useId } from "react";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import type { DoctorRef } from "../../types/appointment";
import type { BookingVariant } from "../../types/booking";
import { bookingSize } from "./bookingSizes";

// Chọn bác sĩ (không bắt buộc) trong số bác sĩ còn trống ở giờ đã chọn. Mặc định là phòng khám
// sắp xếp bác sĩ, giá trị `null` ở state.

/** Giá trị radio của lựa chọn "phòng khám sắp xếp" (không trùng mã bác sĩ nào). */
export const ANY_DOCTOR_VALUE = "__clinic_assigns__";

export function DoctorChoice({
  doctors,
  value,
  onChange,
  variant,
  label,
}: {
  doctors: readonly DoctorRef[];
  /** null = phòng khám sắp xếp. */
  value: string | null;
  onChange: (doctorId: string | null) => void;
  variant: BookingVariant;
  label: string;
}) {
  const sizes = bookingSize(variant);
  const groupId = useId();
  const rows: { id: string; value: string; name: string }[] = [
    {
      id: `${groupId}-any`,
      value: ANY_DOCTOR_VALUE,
      name: "Phòng khám sắp xếp bác sĩ",
    },
    ...doctors.map((doctor) => ({
      id: `${groupId}-${doctor.id}`,
      value: doctor.id,
      name: doctor.fullName,
    })),
  ];

  return (
    <div className="grid gap-3">
      <p className={sizes.detail}>
        Không bắt buộc. Nếu không chọn, phòng khám sắp xếp bác sĩ có ca vào giờ
        này.
      </p>
      <RadioGroup
        aria-label={label}
        value={value ?? ANY_DOCTOR_VALUE}
        onValueChange={(next: string) =>
          onChange(next === ANY_DOCTOR_VALUE ? null : next)
        }
        className="gap-0 divide-y rounded-lg border"
      >
        {rows.map((row) => (
          <div key={row.value} className={`flex items-center gap-3 ${sizes.row}`}>
            <RadioGroupItem id={row.id} value={row.value} />
            <Label htmlFor={row.id} className={`min-w-0 flex-1 cursor-pointer font-normal ${sizes.body}`}>
              {row.name}
            </Label>
          </div>
        ))}
      </RadioGroup>
    </div>
  );
}
