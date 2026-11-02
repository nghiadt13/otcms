import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

// Đầu trang công khai: đúng một h1 và một câu mô tả. Không đặt nhãn phía trên tiêu đề.
// `children` dành cho dòng phụ (ví dụ ngày đăng) nằm dưới mô tả.
export function PublicPageHeader({
  title,
  description,
  children,
  className,
}: {
  title: string;
  description?: string;
  children?: ReactNode;
  className?: string;
}) {
  return (
    <header className={cn("max-w-3xl", className)}>
      <h1 className="text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
        {title}
      </h1>
      {description ? (
        <p className="mt-4 text-lg leading-8 text-pretty text-muted-foreground">
          {description}
        </p>
      ) : null}
      {children}
    </header>
  );
}
