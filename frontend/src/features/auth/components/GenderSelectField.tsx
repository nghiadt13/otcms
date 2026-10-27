import { Users } from "lucide-react";
import { useId } from "react";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import type { Gender } from "../types/auth";
import { AuthFieldLabel, AuthFieldMessage } from "./AuthTextField";

// Ô chọn giới tính dùng NativeSelect của shadcn (vẫn là <select> gốc nên bàn phím,
// di động chạy sẵn). NativeSelect chỉ nhận className cho khung bọc, nên cỡ ô được
// chỉnh qua `[&_select]:` cho khớp AuthTextField (cao 48px, chữ 16px, viền đậm).
export function GenderSelectField({
  value,
  error,
  onChange,
  onBlur,
}: {
  value: Gender | "";
  error?: string;
  onChange: (next: string) => void;
  onBlur?: () => void;
}) {
  const id = useId();
  return (
    <div>
      <AuthFieldLabel htmlFor={id} required>
        Giới tính
      </AuthFieldLabel>
      <div className="relative mt-2">
        <Users
          aria-hidden
          className="pointer-events-none absolute top-1/2 left-3.5 z-10 size-5 -translate-y-1/2 text-muted-foreground"
        />
        <NativeSelect
          aria-describedby={error ? `${id}-error` : undefined}
          aria-invalid={error ? true : undefined}
          aria-required
          className="w-full [&_select]:h-12 [&_select]:cursor-pointer [&_select]:border-muted-foreground [&_select]:bg-card [&_select]:pl-11 [&_select]:text-base [&_select]:hover:border-foreground"
          id={id}
          onBlur={onBlur}
          onChange={(event) => onChange(event.target.value)}
          value={value}
        >
          <NativeSelectOption value="">Chọn giới tính</NativeSelectOption>
          <NativeSelectOption value="male">Nam</NativeSelectOption>
          <NativeSelectOption value="female">Nữ</NativeSelectOption>
          <NativeSelectOption value="other">Khác</NativeSelectOption>
        </NativeSelect>
      </div>
      {error && (
        <AuthFieldMessage error id={`${id}-error`}>
          {error}
        </AuthFieldMessage>
      )}
    </div>
  );
}
