import { Check, X } from "lucide-react";

// Báo "Mật khẩu khớp/Chưa khớp" dưới ô nhập lại. Chỉ hiện khi người dùng đã gõ
// đủ độ dài mật khẩu hoặc đã rời khỏi ô, để không báo "chưa khớp" từ ký tự đầu.
export function PasswordMatchHint({
  password,
  confirmation,
  touched,
}: {
  password: string;
  confirmation: string;
  touched: boolean;
}) {
  const ready =
    confirmation.length > 0 &&
    (touched || confirmation.length >= password.length);
  if (!ready) return null;

  const matched = confirmation === password;
  const Icon = matched ? Check : X;
  return (
    <p
      aria-live="polite"
      className={`mt-1.5 flex items-center gap-1.5 text-sm ${
        matched ? "font-medium text-primary" : "text-destructive"
      }`}
    >
      <Icon aria-hidden className="size-4 shrink-0" />
      {matched ? "Mật khẩu khớp" : "Mật khẩu chưa khớp"}
    </p>
  );
}
