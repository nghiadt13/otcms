"use client";

import { CalendarPlus, Menu, PhoneCall } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { OtcmsLogo } from "@/components/brand/OtcmsLogo";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { cn } from "@/lib/utils";
import {
  hotlineTelHref,
  isActiveNavItem,
  siteNavItems,
} from "../siteNavigation";
import { SiteAccountLinks } from "./SiteAccountLinks";

// Thanh menu chính, dính trên cùng khi cuộn. Thiết kế cho desktop: logo, menu
// ngang (mục đang mở có gạch dưới màu primary) và nút "Đặt lịch khám"; từ lg
// hotline đã có ở dải SiteTopBar ngay trên nên header không lặp lại.
// Dưới xl, bảy mục menu không vừa một hàng nên menu nằm trong Sheet (shadcn) và Sheet mang hotline
// để người dùng điện thoại bấm gọi. Esc, bẫy focus và trả focus về nút ☰ do Sheet lo.
export function SiteHeader({ hotline }: { hotline: string }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const items = siteNavItems.map((item) => ({
    ...item,
    active: isActiveNavItem(item.href, pathname),
  }));

  return (
    <header className="sticky top-0 z-40 border-b bg-background/95 shadow-sm shadow-foreground/5 backdrop-blur">
      <div className={`${publicContainerClass} flex h-16 items-center lg:h-20`}>
        <OtcmsLogo tone="onLight" />

        <nav aria-label="Menu chính" className="ml-12 hidden h-full xl:block">
          <ul className="flex h-full items-center gap-2">
            {items.map((item) => (
              <li className="relative h-full" key={item.href}>
                <Link
                  aria-current={item.active ? "page" : undefined}
                  className={cn(
                    "flex h-full items-center px-4 text-base font-medium transition-colors duration-150 outline-none hover:text-primary focus-visible:ring-3 focus-visible:ring-ring/50 motion-reduce:transition-none",
                    item.active ? "text-primary" : "text-foreground/80",
                  )}
                  href={item.href}
                >
                  {item.label}
                </Link>
                {item.active && (
                  <span
                    aria-hidden
                    className="absolute inset-x-4 bottom-0 h-0.5 rounded-full bg-primary"
                  />
                )}
              </li>
            ))}
          </ul>
        </nav>

        <div className="ml-auto flex items-center gap-6">
          <div className="hidden items-center gap-5 lg:flex">
            <SiteAccountLinks variant="nav" />
          </div>
          {/* Từ lg nút đặt lịch đã nằm ở SiteTopBar. */}
          <PublicBookAppointmentLink
            className={cn(
              publicButtonClass("primary"),
              "max-sm:h-11 max-sm:px-4 lg:hidden",
            )}
          >
            <CalendarPlus aria-hidden className="max-sm:hidden" />
            Đặt lịch khám
          </PublicBookAppointmentLink>

          <Sheet onOpenChange={setOpen} open={open}>
            <SheetTrigger
              render={
                <Button
                  aria-label="Mở menu"
                  className="-ml-3 size-11 xl:hidden [&_svg:not([class*='size-'])]:size-6"
                  variant="ghost"
                />
              }
            >
              <Menu aria-hidden />
            </SheetTrigger>
            <SheetContent className="w-80 gap-0" side="right">
              <SheetHeader className="border-b px-6 py-5">
                <SheetTitle>
                  <OtcmsLogo tone="onLight" />
                </SheetTitle>
              </SheetHeader>
              <nav aria-label="Menu chính" className="px-3 py-3">
                <ul>
                  {items.map((item) => (
                    <li key={item.href}>
                      <Link
                        aria-current={item.active ? "page" : undefined}
                        className={cn(
                          "flex h-12 items-center rounded-md px-3 text-base font-medium outline-none hover:bg-accent focus-visible:ring-3 focus-visible:ring-ring/50",
                          item.active
                            ? "bg-accent text-accent-foreground"
                            : "text-foreground",
                        )}
                        href={item.href}
                        onClick={() => setOpen(false)}
                      >
                        {item.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </nav>
              <div className="mt-auto grid gap-3 border-t p-6">
                <a
                  className="mb-2 rounded-lg outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  href={hotlineTelHref(hotline)}
                >
                  <span className="block text-sm text-muted-foreground">
                    Hotline đặt lịch
                  </span>
                  <span className="mt-0.5 flex items-center gap-2 text-2xl font-semibold text-primary tabular-nums">
                    <PhoneCall aria-hidden className="size-5" />
                    {hotline}
                  </span>
                </a>
                {/* Từ lg tài khoản đã nằm ở hàng menu. */}
                <div className="grid gap-3 lg:hidden">
                  <SiteAccountLinks
                    onNavigate={() => setOpen(false)}
                    variant="sheet"
                  />
                </div>
              </div>
            </SheetContent>
          </Sheet>
        </div>
      </div>
    </header>
  );
}
