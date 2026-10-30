"use client";

import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export function Panel({
  title,
  subtitle,
  children,
  action,
  className = "",
}: {
  title?: string;
  subtitle?: string;
  children: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <section
      className={cn(
        "im-panel rounded-lg border bg-card text-card-foreground",
        className,
      )}
    >
      {title && (
        <header className="im-panel-heading">
          <h2 className="text-lg font-semibold" title={subtitle}>
            {title}
          </h2>
          {action}
        </header>
      )}
      {children}
    </section>
  );
}
