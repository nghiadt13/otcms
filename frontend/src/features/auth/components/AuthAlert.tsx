import { CircleAlert, CircleCheck } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { authMessages, type AuthMessageCode } from "../authMessages";

// Thông báo MSG của appendix ngay trong thẻ, dựng trên Alert của shadcn (khu
// public chưa có toast). Lỗi dùng `role="alert"` để đọc ngay; thành công dùng
// `role="status"` để không cắt ngang. Lỗi dùng đỏ (destructive), không dùng coral.
// Chữ lỗi red-800 trên nền đỏ nhạt (~7:1) vì `destructive` thuần chỉ ~4.4:1 ở nền này.
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

  return (
    <Alert
      className={cn(
        "gap-x-3 px-4 py-3",
        isError
          ? "border-destructive/30 bg-destructive/5 text-red-800"
          : "border-brand-muted bg-secondary text-secondary-foreground",
        className,
      )}
      role={isError ? "alert" : "status"}
    >
      <Icon aria-hidden className="size-5" />
      <AlertDescription className="text-base leading-6 text-current">
        {authMessages[code]}
      </AlertDescription>
    </Alert>
  );
}
