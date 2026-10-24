"use client";

import { ChevronDown, Menu } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import type { PatientNavGroupView } from "../patientNavigation";

// Menu theo nhóm. Từ md: mỗi nhóm là một nút ngang, bấm xổ danh sách mục. Dưới md: nút
// "Menu" mở Sheet liệt kê theo nhóm, mỗi mục là một dòng cao 48px. Không có nhóm nào thì
// không render gì (cả nút "Menu"), nên khi chưa có trang thì header không có lối trống.
export function PatientPortalNav({
  groups,
}: {
  groups: PatientNavGroupView[];
}) {
  const [open, setOpen] = useState(false);
  if (groups.length === 0) return null;

  return (
    <>
      <nav
        aria-label="Menu khu bệnh nhân"
        className="hidden items-center gap-1 md:flex"
      >
        {groups.map(({ group, items }) => (
          <DropdownMenu key={group}>
            <DropdownMenuTrigger
              render={
                <Button className="h-12 gap-1 px-3 text-base" variant="ghost" />
              }
            >
              {group}
              <ChevronDown aria-hidden />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" className="min-w-56">
              {items.map((item) => (
                <DropdownMenuItem
                  className="h-11 text-base"
                  key={item.href}
                  render={<Link href={item.href} />}
                >
                  <item.icon aria-hidden />
                  {item.label}
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        ))}
      </nav>

      <Sheet onOpenChange={setOpen} open={open}>
        <SheetTrigger
          render={
            <Button
              className="h-11 gap-2 px-3 text-base md:hidden"
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
