"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { OtcmsLogo } from "@/components/brand/OtcmsLogo";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { useSlidingUnderline } from "@/components/shell/useSlidingUnderline";
import { cn } from "@/lib/utils";
import { isActiveNavItem, siteNavItems } from "../siteNavigation";
import { SiteAccountLinks } from "./SiteAccountLinks";

const mobileItems = [
  { label: "Trang chủ", href: "/" },
  { label: "Dịch vụ", href: "/services" },
  { label: "Bác sĩ", href: "/doctors" },
  { label: "Hướng dẫn", href: "/#quy-trinh-kham" },
];

export function SiteHeader({
  notificationSlot,
  topBar,
}: {
  hotline: string;
  notificationSlot?: ReactNode;
  topBar?: ReactNode;
}) {
  const pathname = usePathname();
  const home = pathname === "/";
  const underline = useSlidingUnderline<HTMLUListElement>();
  return (
    <>
      {topBar}
      <header
        className={cn(
          "sticky top-0 z-40 border-b shadow-sm shadow-foreground/5 backdrop-blur",
          home ? "bg-background/95" : "bg-card/95",
        )}
      >
        <div
          className={cn(
            publicContainerClass,
            "flex min-h-16 items-center justify-between gap-2 py-2 max-[359px]:gap-1 max-[359px]:px-3 xl:h-20 xl:justify-start xl:py-0",
            home && "xl:gap-0",
          )}
        >
          <div className="shrink-0 max-[359px]:[&_a>img]:size-7 max-[359px]:[&_a>span]:text-sm max-sm:[&_a]:gap-1.5 max-sm:[&_a>span]:text-base max-sm:[&_a>span]:tracking-normal max-sm:[&_a>img]:size-9">
            <OtcmsLogo tone="onLight" />
          </div>
          <nav
            aria-label="Menu chính"
            className={cn(
              "hidden h-full",
              home ? "ml-8 xl:block" : "ml-6 xl:block",
            )}
          >
            <ul
              className="relative flex h-full items-center gap-2"
              {...underline.navProps}
            >
              {siteNavItems.map((item) => (
                <li className="h-full" key={item.href}>
                  <Link
                    aria-current={
                      isActiveNavItem(item.href, pathname) ? "page" : undefined
                    }
                    className={cn(
                      "flex h-full items-center px-3 text-base font-medium whitespace-nowrap outline-none hover:text-primary focus-visible:ring-3 focus-visible:ring-ring/50",
                      home &&
                        "transition-colors duration-150 motion-reduce:transition-none",
                      isActiveNavItem(item.href, pathname)
                        ? "text-primary"
                        : "text-foreground/80",
                    )}
                    href={item.href}
                  >
                    {item.label}
                  </Link>
                </li>
              ))}
              {underline.indicator}
            </ul>
          </nav>
          <div className="ml-auto hidden items-center gap-5 xl:flex">
            <SiteAccountLinks
              notificationSlot={notificationSlot}
              variant="nav"
            />
          </div>
          <div
            className={cn(
              "ml-auto flex min-w-0 items-center gap-2 max-[359px]:gap-1 xl:hidden",
              home && "lg:gap-3",
            )}
          >
            <SiteAccountLinks
              compact
              notificationSlot={notificationSlot}
              variant="nav"
            />
            <PublicBookAppointmentLink
              className={cn(
                publicButtonClass("primary"),
                "h-11 shrink-0 px-3 text-sm max-[359px]:px-2 sm:px-5 sm:text-base",
                home && "lg:hidden",
              )}
            >
              {home ? (
                <>
                  <span className="min-[900px]:hidden">Đặt lịch</span>
                  <span className="hidden min-[900px]:inline">
                    Đặt lịch khám
                  </span>
                </>
              ) : (
                "Đặt lịch"
              )}
            </PublicBookAppointmentLink>
          </div>
        </div>
        <nav
          aria-label="Điều hướng mobile"
          className="border-t xl:hidden"
        >
          <ul className={cn(publicContainerClass, "grid grid-cols-4")}>
            {mobileItems.map((item) => (
              <li className="min-w-0" key={item.href}>
                <Link
                  aria-current={
                    item.href === "/#quy-trinh-kham"
                      ? undefined
                      : isActiveNavItem(item.href, pathname)
                        ? "page"
                        : undefined
                  }
                  className={cn(
                    "flex min-h-12 items-center justify-center border-b-2 px-1 text-sm font-medium whitespace-nowrap outline-none hover:text-primary focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring/50 sm:text-base",
                    item.href !== "/#quy-trinh-kham" &&
                      isActiveNavItem(item.href, pathname)
                      ? "border-primary font-semibold text-primary"
                      : "border-transparent text-foreground/80",
                  )}
                  href={item.href}
                >
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
      </header>
    </>
  );
}
