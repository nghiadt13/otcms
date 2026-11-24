import type { ReactNode } from "react";

export function ResultFieldList({
  columns = 1,
  items,
}: {
  columns?: 1 | 2;
  items: { label: string; value: ReactNode }[];
}) {
  return (
    <dl
      className={
        columns === 2 ? "grid gap-x-6 gap-y-3 sm:grid-cols-2" : "grid gap-y-3"
      }
    >
      {items.map(({ label, value }) => (
        <div key={label} className="min-w-0 space-y-0.5">
          <dt className="text-base font-medium text-muted-foreground">
            {label}
          </dt>
          <dd className="wrap-break-word text-base leading-6">
            {value ?? "Chưa có"}
          </dd>
        </div>
      ))}
    </dl>
  );
}
