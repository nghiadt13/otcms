import { LoaderCircle } from "lucide-react";
import type { ReactNode } from "react";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

// Nút chính duy nhất của mỗi màn xác thực (Button của shadcn, cỡ public). Khi
// đang gửi: khóa nút, hiện vòng xoay và đổi nhãn để không bấm hai lần
// (ui-guidelines mục 5).
export function AuthSubmitButton({
  submitting,
  submittingLabel,
  children,
}: {
  submitting: boolean;
  submittingLabel: string;
  children: ReactNode;
}) {
  return (
    <Button
      className={cn(publicButtonClass("primary"), "w-full")}
      disabled={submitting}
      type="submit"
    >
      {submitting && (
        <LoaderCircle
          aria-hidden
          className="animate-spin motion-reduce:animate-none"
        />
      )}
      {submitting ? submittingLabel : children}
    </Button>
  );
}

// Nút phụ (viền) rộng hết thẻ, đi kèm nút chính, ví dụ "Về đăng nhập".
export const authSecondaryButtonClass = cn(
  publicButtonClass("secondary"),
  "w-full",
);

// Link dạng nút chính rộng hết thẻ, ví dụ "Đăng nhập" sau khi đặt lại mật khẩu.
export const authPrimaryLinkClass = cn(publicButtonClass("primary"), "w-full");
