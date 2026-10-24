import { CircleAlert, CircleCheck } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { authMessages, type AuthMessageCode } from "../authMessages";

// Thông báo MSG của appendix ngay trong thẻ, dựng trên Alert của shadcn (khu
// public chưa có toast). Lỗi dùng `role="alert"` để đọc ngay; thành công dùng
// `role="status"` để không cắt ngang. Lỗi: biểu tượng màu `destructive`, chữ dùng màu
// nền chính (tương phản cao, đạt 4.5:1 trên nền đỏ nhạt; `destructive` thuần chỉ ~4.4:1).
export function AuthAlert({
  code,
  tone,
  className,
}: {
  code: AuthMessageCode;
  tone: "error" | "success";
  className?: string;
}) {
  const isError = tone === "error";
  const Icon = isError ? CircleAlert : CircleCheck;
  // Biểu tượng lỗi dùng `!` vì Alert đặt `svg { color: currentColor }` ưu tiên hơn một lớp đơn.

  return (
    <Alert
      className={cn(
        "gap-x-3 px-4 py-3",
        isError
          ? "border-destructive/30 bg-destructive/5 text-foreground"
          : "border-brand-muted bg-secondary text-secondary-foreground",
        className,
      )}
      role={isError ? "alert" : "status"}
    >
      <Icon
        aria-hidden
        className={cn("size-5", isError && "text-destructive!")}
      />
      <AlertDescription className="text-base leading-6 text-current">
        {authMessages[code]}
      </AlertDescription>
    </Alert>
  );
}
