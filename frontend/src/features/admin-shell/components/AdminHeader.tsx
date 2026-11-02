"use client";

import { ChevronRight } from "lucide-react";
import { usePathname } from "next/navigation";
import { Separator } from "@/components/ui/separator";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { NotificationBell } from "@/features/notifications/components/NotificationBell";
import { findActiveBreadcrumb } from "../adminNavigation";
import { CurrentDateTime } from "@/components/shell/CurrentDateTime";

export function AdminHeader() {
  const pathname = usePathname();
  const breadcrumb = findActiveBreadcrumb(pathname);

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b px-4">
      <SidebarTrigger className="-ml-1" />
      <Separator orientation="vertical" className="mr-2 h-4" />

      <nav aria-label="Breadcrumb" className="flex items-center text-sm">
        <ol className="flex items-center gap-1.5 text-xs sm:text-sm">
          {breadcrumb ? (
            <>
              {breadcrumb.groupLabel && (
                <>
                  <li className="hidden sm:inline-flex items-center text-muted-foreground">
                    <span>{breadcrumb.groupLabel}</span>
                  </li>
                  <li
                    className="hidden sm:inline-flex items-center text-muted-foreground"
                    aria-hidden="true"
                  >
                    <ChevronRight className="size-3.5" />
                  </li>
                </>
              )}
              <li
                className="flex items-center font-medium text-foreground"
                aria-current="page"
              >
                <span>{breadcrumb.itemTitle}</span>
              </li>
            </>
          ) : (
            <li
              className="flex items-center font-medium text-foreground"
              aria-current="page"
            >
              <span>Quản trị</span>
            </li>
          )}
        </ol>
      </nav>

      <div className="ml-auto flex items-center gap-3">
        <CurrentDateTime />
        <Separator orientation="vertical" className="h-4" />
        <NotificationBell />
      </div>
    </header>
  );
}
