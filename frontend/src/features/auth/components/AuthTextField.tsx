import type { LucideIcon } from "lucide-react";
import { useId, type InputHTMLAttributes, type ReactNode } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

// Phóng to Input của shadcn cho màn xác thực: cao 48px (dư vùng chạm 44px), chữ
// 16px ở mọi cỡ màn (iOS không tự phóng to). Viền dùng `muted-foreground`
// (~5.6:1 trên nền trắng) thay cho `input` mặc định (~1.4:1) để đạt ngưỡng ≥ 3:1
// của WCAG cho viền ô nhập.
export const authInputClass =
  "h-12 rounded-lg border-muted-foreground bg-card px-3 text-base hover:border-foreground md:text-base";

// Nhãn + ô nhập + gợi ý/lỗi ngay dưới ô. Lỗi thay chỗ gợi ý để không đẩy bố cục
// hai lần; `icon` là icon đầu ô gợi ý loại dữ liệu, `trailing` dành cho nút nằm
// cuối ô (ví dụ hiện/ẩn mật khẩu).
export function AuthTextField({
  label,
  icon: Icon,
  error,
  hint,
  required,
  trailing,
  className,
  ...inputProps
}: InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  icon?: LucideIcon;
  error?: string;
  hint?: ReactNode;
  trailing?: ReactNode;
}) {
  const id = useId();
  const messageId = `${id}-message`;
  const message = error ?? hint;

  return (
    <div className={className}>
      <AuthFieldLabel htmlFor={id} required={required}>
        {label}
      </AuthFieldLabel>
      <div className="relative mt-2">
        {Icon && (
          <Icon
            aria-hidden
            className="pointer-events-none absolute top-1/2 left-3.5 z-10 size-5 -translate-y-1/2 text-muted-foreground"
          />
        )}
        <Input
          {...inputProps}
          aria-describedby={message ? messageId : undefined}
          aria-invalid={error ? true : undefined}
          aria-required={required || undefined}
          className={cn(authInputClass, Icon && "pl-11", trailing && "pr-12")}
          id={id}
        />
        {trailing && (
          <div className="absolute inset-y-0 right-0 flex items-center pr-1">
            {trailing}
          </div>
        )}
      </div>
      {message && (
        <AuthFieldMessage error={Boolean(error)} id={messageId}>
          {message}
        </AuthFieldMessage>
      )}
    </div>
  );
}

// Nhãn ô nhập dùng chung (ô chữ, ô chọn giới tính): Label của shadcn cỡ 16px,
// dấu * đỏ cho trường bắt buộc (trình đọc màn hình đã nghe `aria-required`).
export function AuthFieldLabel({
  htmlFor,
  required,
  children,
}: {
  htmlFor: string;
  required?: boolean;
  children: ReactNode;
}) {
  return (
    <Label className="gap-0.5 text-base text-foreground/90" htmlFor={htmlFor}>
      {children}
      {required && (
        <span aria-hidden className="text-destructive">
          *
        </span>
      )}
    </Label>
  );
}

// Dòng gợi ý (xám) hoặc lỗi (đỏ) ngay dưới ô nhập, nối với ô qua aria-describedby.
export function AuthFieldMessage({
  id,
  error,
  children,
}: {
  id: string;
  error: boolean;
  children: ReactNode;
}) {
  return (
    <p
      className={cn(
        "mt-1.5 text-sm",
        error ? "text-destructive" : "text-muted-foreground",
      )}
      id={id}
    >
      {children}
    </p>
  );
}
