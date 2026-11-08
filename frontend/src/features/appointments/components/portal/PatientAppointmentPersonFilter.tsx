"use client";

import { Users } from "lucide-react";
import { useId } from "react";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import type { LinkedProfile } from "@/lib/patient-scope/types";

// Bộ lọc "người khám" của danh sách lịch: ô chọn gọn có biểu tượng nhóm người, nhãn chỉ cho trình đọc.
// Chỉ hiện khi tài khoản có từ hai hồ sơ trở lên. `undefined` là tất cả người khám.
export function PatientAppointmentPersonFilter({
  profiles,
  value,
  onChange,
}: {
  profiles: LinkedProfile[];
  value: string | undefined;
  onChange: (profileId: string | undefined) => void;
}) {
  const id = useId();
  if (profiles.length <= 1) return null;

  return (
    <div className="relative">
      <Label className="sr-only" htmlFor={id}>
        Người khám
      </Label>
      <Users
        aria-hidden
        className="pointer-events-none absolute top-1/2 left-3.5 z-10 size-5 -translate-y-1/2 text-muted-foreground"
      />
      <NativeSelect
        className="w-full [&_select]:h-12 [&_select]:pr-10 [&_select]:pl-11 [&_select]:text-base"
        id={id}
        onChange={(event) => onChange(event.target.value || undefined)}
        value={value ?? ""}
      >
        <NativeSelectOption value="">Tất cả người khám</NativeSelectOption>
        {profiles.map((profile) => (
          <NativeSelectOption key={profile.id} value={profile.id}>
            {`${profile.fullName} · ${CARE_RELATIONSHIP_LABELS[profile.relationship]}`}
          </NativeSelectOption>
        ))}
      </NativeSelect>
    </div>
  );
}
