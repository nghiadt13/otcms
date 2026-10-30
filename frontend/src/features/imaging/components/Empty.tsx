"use client";

import type { ReactNode } from "react";
import { Search } from "lucide-react";

export function Empty({
  title = "Không có dữ liệu phù hợp",
  description = "Thử thay đổi bộ lọc hoặc kiểm tra lại sau.",
  children,
}: {
  title?: string;
  description?: string;
  children?: ReactNode;
}) {
  return (
    <div className="im-empty">
      <Search className="size-6 text-muted-foreground" aria-hidden />
      <h3 className="font-medium">{title}</h3>
      <p className="text-muted-foreground">{description}</p>
      {children}
    </div>
  );
}
