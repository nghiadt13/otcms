"use client";

import { useId } from "react";
import { Label } from "@/components/ui/label";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import type { LinkedProfile } from "@/lib/patient-scope/types";

// Chọn "người khám" ở đầu trang khi tài khoản có từ hai hồ sơ trở lên. Giá trị `undefined`
// là "Tất cả". Component không tự đọc router: trang giữ giá trị trên URL `?profileId=`
// và truyền xuống. Chọn native để dùng ổn trên điện thoại và với trình đọc màn hình.
export function ProfileScopeSelect({
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
    <div className="grid gap-2">
      <Label className="text-base font-medium" htmlFor={id}>
        Người khám
      </Label>
      <NativeSelect
        className="w-full [&_select]:h-12 [&_select]:border-muted-foreground [&_select]:px-3 [&_select]:text-base"
        id={id}
        onChange={(event) => onChange(event.target.value || undefined)}
        value={value ?? ""}
      >
        <NativeSelectOption value="">Tất cả</NativeSelectOption>
        {profiles.map((profile) => (
          <NativeSelectOption key={profile.id} value={profile.id}>
            {`${profile.fullName} · ${CARE_RELATIONSHIP_LABELS[profile.relationship]}`}
          </NativeSelectOption>
        ))}
      </NativeSelect>
    </div>
  );
}
