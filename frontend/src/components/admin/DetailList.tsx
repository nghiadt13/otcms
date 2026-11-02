import type { ReactNode } from "react";

/** Danh sách nhãn–giá trị trong Sheet chi tiết: 2 cột từ sm, 1 cột ở 375px. */
export function DetailList({
  items,
}: {
  items: { label: string; value: ReactNode }[];
}) {
  return (
    <dl className="grid gap-x-6 gap-y-4 text-sm sm:grid-cols-2">
      {items.map(({ label, value }) => (
        <div key={label} className="min-w-0">
          <dt className="text-xs text-muted-foreground">{label}</dt>
          <dd className="mt-0.5 break-words">{value ?? "—"}</dd>
        </div>
      ))}
    </dl>
  );
}
