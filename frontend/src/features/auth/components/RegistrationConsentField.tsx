import { useId } from "react";
import { Checkbox } from "@/components/ui/checkbox";
import { AuthFieldMessage } from "./AuthTextField";

// Ô đồng ý xử lý dữ liệu cá nhân và sức khỏe, bắt buộc trước khi tạo tài khoản.
// Checkbox của shadcn phóng lên 20px, nằm trong nhãn cả dòng nên vùng bấm đủ lớn.
// Trang điều khoản và chính sách chưa có nên chưa gắn link; thêm khi nội dung được chốt.
export function RegistrationConsentField({
  checked,
  error,
  onChange,
}: {
  checked: boolean;
  error?: string;
  onChange: (next: boolean) => void;
}) {
  const id = useId();
  return (
    <div>
      <label
        className="flex cursor-pointer items-start gap-3 rounded-lg py-1 text-base leading-7 text-foreground/90"
        htmlFor={id}
      >
        <Checkbox
          aria-describedby={error ? `${id}-error` : undefined}
          aria-invalid={error ? true : undefined}
          aria-required
          checked={checked}
          className="mt-1 size-5 border-muted-foreground [&_svg]:size-4"
          id={id}
          onCheckedChange={(next) => onChange(next)}
        />
        <span>
          Tôi đồng ý với Điều khoản sử dụng và cho phép phòng khám xử lý dữ liệu
          cá nhân, dữ liệu sức khỏe của tôi để phục vụ khám chữa bệnh.
          <span aria-hidden className="ml-0.5 text-destructive">
            *
          </span>
        </span>
      </label>
      {error && (
        <AuthFieldMessage error id={`${id}-error`}>
          {error}
        </AuthFieldMessage>
      )}
    </div>
  );
}
