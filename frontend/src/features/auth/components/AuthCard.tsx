import type { ReactNode } from "react";
import { OtcmsLogo } from "@/components/brand/OtcmsLogo";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { AuthBrandPanel } from "./AuthBrandPanel";

// Khung chung của các màn xác thực (đăng nhập, quên/đặt lại mật khẩu, đăng ký) ở
// khu public. Từ màn lg: hai cột bằng nhau, panel thương hiệu bên trái (AuthBrandPanel),
// form bên phải. Màn hẹp hơn: ẩn panel, chỉ còn logo phía trên thẻ form. Thẻ là Card
// của shadcn; màu theo token theme (docs/ui-guidelines.md, mục 1). Tiêu đề h1 đứng
// đầu thẻ, không có ô icon hay nhãn trang trí phía trên.
export function AuthCard({
  progress,
  title,
  description,
  children,
  footer,
  wide = false,
}: {
  // Luồng nhiều bước (đăng ký): hiện thanh tiến trình kèm chữ "Bước x/y" đầu thẻ.
  progress?: { current: number; total: number };
  title: string;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
}) {
  return (
    <div className="min-h-dvh bg-background text-foreground lg:grid lg:grid-cols-2">
      <AuthBrandPanel />

      <main className="flex min-h-dvh flex-col items-center px-4 py-10 sm:justify-center sm:px-6 lg:px-10">
        <div className={wide ? "w-full max-w-xl" : "w-full max-w-lg"}>
          <div className="mb-6 lg:hidden">
            <OtcmsLogo tone="onLight" />
          </div>

          <Card className="gap-0 rounded-2xl p-6 text-base shadow-sm sm:p-8">
            {progress && (
              <div className="mb-6 flex items-center gap-4">
                <div aria-hidden className="flex flex-1 gap-2">
                  {Array.from({ length: progress.total }, (_, index) => (
                    <span
                      className={cn(
                        "h-1 flex-1 rounded-full transition-colors duration-300 ease-out motion-reduce:transition-none",
                        index < progress.current ? "bg-primary" : "bg-border",
                      )}
                      key={index}
                    />
                  ))}
                </div>
                <p
                  className="shrink-0 text-sm font-medium text-muted-foreground tabular-nums"
                  id={authProgressId}
                >
                  Bước {progress.current}/{progress.total}
                </p>
              </div>
            )}
            <h1
              className="text-3xl font-semibold tracking-tight focus:outline-none"
              aria-describedby={progress ? authProgressId : undefined}
              id={authTitleId}
              tabIndex={-1}
            >
              {title}
            </h1>
            {description && (
              <p className="mt-2 text-base leading-7 text-muted-foreground">
                {description}
              </p>
            )}
            {children}
          </Card>

          {footer && (
            <p className="mt-6 text-center text-base text-muted-foreground">
              {footer}
            </p>
          )}
        </div>
      </main>
    </div>
  );
}

// Id của tiêu đề thẻ, để luồng nhiều bước chuyển focus về tiêu đề khi đổi bước.
export const authTitleId = "auth-title";

// Id của dòng "Bước x/y": tiêu đề trỏ tới nó (aria-describedby) để khi focus chuyển
// về tiêu đề lúc đổi bước, trình đọc màn hình đọc cả số bước.
const authProgressId = "auth-progress";

// Liên kết chữ dùng trong form và chân thẻ, cùng một kiểu ở mọi màn xác thực.
export const authLinkClass =
  "rounded-sm font-semibold text-primary underline-offset-4 outline-none hover:text-brand-hover hover:underline focus-visible:ring-3 focus-visible:ring-ring/50";

// Button variant="link" có cỡ chữ/độ đậm khớp authLinkClass, cho thao tác dạng
// link nhưng là nút (ví dụ "Gửi lại mã", "Đổi email").
export const authLinkButtonClass =
  "h-auto p-0 text-base font-semibold hover:text-brand-hover";
