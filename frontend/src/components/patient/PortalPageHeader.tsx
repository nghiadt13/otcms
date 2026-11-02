import type { ReactNode } from "react";

// Tiêu đề màn khu bệnh nhân: chữ cỡ "màn xác thực" (ui-guidelines mục 9), một h1 duy nhất.
// `actions` là slot nút của màn (đúng một nút `default`, còn lại outline/ghost/link).
export function PortalPageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {description ? (
          <p className="mt-2 text-base text-muted-foreground">{description}</p>
        ) : null}
      </div>
      {actions ? <div className="flex flex-wrap gap-3">{actions}</div> : null}
    </div>
  );
}
