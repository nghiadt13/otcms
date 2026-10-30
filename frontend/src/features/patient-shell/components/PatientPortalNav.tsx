"use client";

import { Menu } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { cn } from "@/lib/utils";
import type { PatientNavGroupView } from "../patientNavigation";

export type PatientSiteLink = { label: string; href: string };

/** Mục đang mở: trang chủ khớp đúng "/", mục khác khớp cả trang con; neo (#) không bao giờ mở. */
function isActiveSiteLink(href: string, pathname: string | null) {
  if (!pathname || href.includes("#")) return false;
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}

// Menu của khu bệnh nhân, cùng kiểu với menu trang chủ: các link trang giới thiệu (`siteLinks`,
// do layout truyền vào vì feature này không import clinic-website; mục đang mở gạch dưới 2px).
// Từ xl: hàng ngang y hệt trang chủ, chỉ có các link đó. Dưới xl: nút "Menu" mở Sheet, mỗi mục là
// một dòng cao 48px, kèm các nhóm của khu bệnh nhân (Khám bệnh, Hồ sơ) vốn không có ở hàng ngang.
// Không có nhóm lẫn link nào thì không render gì (cả nút "Menu").
export function PatientPortalNav({
  groups,
  siteLinks = [],
}: {
  groups: PatientNavGroupView[];
  siteLinks?: PatientSiteLink[];
}) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  if (groups.length === 0 && siteLinks.length === 0) return null;

  return (
    <>
      <nav
        aria-label="Menu khu bệnh nhân"
        className="hidden h-full items-center gap-2 xl:flex"
      >
        {siteLinks.map((link) => {
          const active = isActiveSiteLink(link.href, pathname);
          return (
            <div
              className="group relative flex h-full items-center"
              key={link.href}
            >
              <Link
                aria-current={active ? "page" : undefined}
                className={cn(
                  "flex h-full items-center rounded-md px-4 text-base font-medium whitespace-nowrap transition-colors duration-150 outline-none hover:text-primary focus-visible:ring-3 focus-visible:ring-ring/50 motion-reduce:transition-none",
                  active ? "text-primary" : "text-foreground/80",
                )}
                href={link.href}
              >
                {link.label}
              </Link>
              {active && (
                <span
                  aria-hidden
                  className="absolute inset-x-4 bottom-0 h-0.5 rounded-full bg-primary"
                />
              )}
            </div>
          );
        })}
      </nav>

      <Sheet onOpenChange={setOpen} open={open}>
        <SheetTrigger
          render={
            <Button
              className="h-11 gap-2 px-3 text-base xl:hidden"
              variant="ghost"
            />
          }
        >
          <Menu aria-hidden />
          Menu
        </SheetTrigger>
        <SheetContent className="w-80 gap-0" side="right">
          <SheetHeader className="border-b px-6 py-5">
            <SheetTitle className="text-lg">Menu</SheetTitle>
          </SheetHeader>
          <nav
            aria-label="Danh mục khu bệnh nhân"
            className="overflow-y-auto px-3 py-3"
          >
            {siteLinks.length > 0 && (
              <ul className="border-b pb-2">
                {siteLinks.map((link) => {
                  const active = isActiveSiteLink(link.href, pathname);
                  return (
                    <li key={link.href}>
                      <Link
                        aria-current={active ? "page" : undefined}
                        className={cn(
                          "flex h-12 items-center rounded-md px-3 text-base font-medium outline-none hover:bg-accent focus-visible:ring-3 focus-visible:ring-ring/50",
                          active && "bg-accent text-accent-foreground",
                        )}
                        href={link.href}
                        onClick={() => setOpen(false)}
                      >
                        {link.label}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            )}
            {groups.map(({ group, items }) => (
              <div className="py-2" key={group}>
                <p className="px-3 pb-1 text-sm font-medium text-muted-foreground">
                  {group}
                </p>
                <ul>
                  {items.map((item) => (
                    <li key={item.href}>
                      <Link
                        className="flex h-12 items-center gap-3 rounded-md px-3 text-base font-medium outline-none hover:bg-accent focus-visible:ring-3 focus-visible:ring-ring/50 motion-reduce:transition-none"
                        href={item.href}
                        onClick={() => setOpen(false)}
                      >
                        <item.icon aria-hidden className="size-5" />
                        {item.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </nav>
        </SheetContent>
      </Sheet>
    </>
  );
}
