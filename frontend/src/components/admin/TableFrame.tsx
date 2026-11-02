import type { ReactNode } from "react";

/** Khung bảng chuẩn khu admin: viền, nền thẻ, cuộn ngang ở màn hẹp. */
export function TableFrame({ children }: { children: ReactNode }) {
  return (
    <div className="overflow-x-auto rounded-lg border bg-card">{children}</div>
  );
}
