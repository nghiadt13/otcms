import type { ReactNode } from "react";

/** Cặp nhãn–giá trị cỡ chữ khu bệnh nhân: 2 cột từ sm, 1 cột ở 375px. */
export function ResultFieldList({
  items,
}: {
  items: { label: string; value: ReactNode }[];
}) {
  return (
    <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-[minmax(0,11rem)_minmax(0,1fr)]">
      {items.map(({ label, value }) => (
        <div key={label} className="grid gap-1 sm:contents">
          <dt className="text-base text-muted-foreground">{label}</dt>
          <dd className="min-w-0 text-base break-words">{value ?? "Chưa có"}</dd>
        </div>
      ))}
    </dl>
  );
}
