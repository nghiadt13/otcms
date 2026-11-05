"use client";

import { ChevronRight } from "lucide-react";
import { usePathname } from "next/navigation";
import { CurrentDateTime } from "@/components/shell/CurrentDateTime";
import { Separator } from "@/components/ui/separator";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { NotificationBell } from "@/features/notifications/components/NotificationBell";
import { DOCTOR_ROUTES } from "@/lib/routes/doctorRoutes";
import { findActiveBreadcrumb } from "../doctorNavigation";
import { findVisitBreadcrumb } from "../doctorVisitNavigation";

export function DoctorHeader() {
  const pathname = usePathname();
  const breadcrumb =
    findVisitBreadcrumb(pathname) ?? findActiveBreadcrumb(pathname);

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b px-4">
      <SidebarTrigger className="-ml-1" />
      <Separator orientation="vertical" className="mr-2 h-4" />

      <nav aria-label="Breadcrumb" className="flex items-center text-sm">
        <ol className="flex items-center gap-1.5 text-xs sm:text-sm">
          {breadcrumb ? (
            <>
              <li className="hidden items-center text-muted-foreground sm:inline-flex">
                <span>{breadcrumb.groupLabel}</span>
              </li>
              <li
                className="hidden items-center text-muted-foreground sm:inline-flex"
                aria-hidden="true"
              >
                <ChevronRight className="size-3.5" />
              </li>
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
              <span>Bác sĩ</span>
            </li>
          )}
        </ol>
      </nav>

      <div className="ml-auto flex items-center gap-3">
        <CurrentDateTime />
        <Separator orientation="vertical" className="h-4" />
        <NotificationBell allNotificationsHref={DOCTOR_ROUTES.notifications} />
      </div>
    </header>
  );
}
