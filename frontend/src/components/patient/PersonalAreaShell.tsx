"use client";

import {
  Bell,
  CalendarDays,
  FileText,
  Pill,
  Receipt,
  UserRound,
  Users,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { cn } from "@/lib/utils";

// Khung "khu cá nhân" của bệnh nhân: hai thẻ trắng cạnh nhau (tiêu đề trang chỉ cho trình đọc). Thẻ
// trái chia ba phần (người đăng nhập, nhóm hồ sơ, nhóm khám bệnh), thẻ phải là nội dung màn. Từ
// `lg` thẻ trái dính khi cuộn; dưới `lg` chỉ còn phần người đăng nhập vì menu chính của header
// đã có đủ các mục này. Thuộc tính
// `data-portal-wide` báo layout /portal nới khung từ max-w-3xl lên max-w-6xl cho riêng màn này.

type AreaLink = { label: string; href: string; icon: LucideIcon };

// Hai nhóm menu: nhóm đầu là hồ sơ của chính người dùng, nhóm sau là việc khám bệnh.
const AREA_GROUPS: readonly (readonly AreaLink[])[] = [
  [
    {
      label: "Thông tin cá nhân",
      href: PATIENT_ROUTES.account,
      icon: UserRound,
    },
    { label: "Hồ sơ và gia đình", href: PATIENT_ROUTES.profiles, icon: Users },
  ],
  [
    { label: "Lịch hẹn", href: PATIENT_ROUTES.appointments, icon: CalendarDays },
    { label: "Kết quả khám", href: PATIENT_ROUTES.results, icon: FileText },
    { label: "Đơn thuốc", href: PATIENT_ROUTES.prescriptions, icon: Pill },
    { label: "Thanh toán", href: PATIENT_ROUTES.payments, icon: Receipt },
    { label: "Thông báo", href: PATIENT_ROUTES.notifications, icon: Bell },
  ],
];

/** Người hiện ở đầu cột trái. `null` khi dữ liệu còn đang tải. */
export type PersonalAreaPerson = {
  name: string;
  avatarUrl: string | null;
  /** Dòng phụ dưới tên, ví dụ "Nam, 21/07/1992". */
  subtitle?: string;
} | null;

const LINK_BASE =
  "flex min-h-12 items-center gap-3 rounded-lg px-3 text-base font-medium transition-colors focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50 motion-reduce:transition-none";

export function PersonalAreaShell({
  title,
  person,
  children,
}: {
  title: string;
  person: PersonalAreaPerson;
  children: ReactNode;
}) {
  const pathname = usePathname();

  return (
    <div data-portal-wide>
      {/* Chỉ trình đọc màn hình thấy: hai thẻ bên dưới tự nói màn này là gì, tiêu đề hiện ra chỉ làm lệch. */}
      <h1 className="sr-only">{title}</h1>
      <div className="grid gap-6 lg:grid-cols-[17rem_minmax(0,1fr)] lg:items-start">
        <aside className="overflow-hidden rounded-xl border bg-card lg:sticky lg:top-24">
          <PersonSummary person={person} />
          <nav aria-label="Các mục cá nhân" className="hidden lg:block">
            {AREA_GROUPS.map((links) => (
              <ul
                className="grid gap-1 border-t p-3"
                key={links[0].href}
              >
                {links.map((link) => {
                  const active =
                    pathname === link.href ||
                    pathname.startsWith(`${link.href}/`);
                  return (
                    <li key={link.href}>
                      <Link
                        aria-current={active ? "page" : undefined}
                        className={cn(
                          LINK_BASE,
                          active
                            ? "bg-primary text-primary-foreground"
                            : "text-foreground hover:bg-muted",
                        )}
                        href={link.href}
                      >
                        <link.icon aria-hidden className="size-5 shrink-0" />
                        {link.label}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            ))}
          </nav>
        </aside>
        <div className="min-w-0 space-y-6">{children}</div>
      </div>
    </div>
  );
}

function PersonSummary({ person }: { person: PersonalAreaPerson }) {
  if (!person) {
    return (
      <div aria-hidden className="flex items-center gap-4 p-5">
        <Skeleton className="size-14 shrink-0 rounded-full lg:size-16" />
        <div className="grid flex-1 gap-2">
          <Skeleton className="h-5 w-3/4" />
          <Skeleton className="h-4 w-1/2" />
        </div>
      </div>
    );
  }
  return (
    <div className="flex items-center gap-4 p-5">
      <Avatar className="size-14 lg:size-16">
        {person.avatarUrl ? (
          <AvatarImage
            alt={`Ảnh đại diện của ${person.name}`}
            src={person.avatarUrl}
          />
        ) : null}
        <AvatarFallback className="text-lg">
          {initialsOf(person.name)}
        </AvatarFallback>
      </Avatar>
      <div className="min-w-0">
        <p className="truncate text-base font-semibold">{person.name}</p>
        {person.subtitle ? (
          <p className="truncate text-sm text-muted-foreground">
            {person.subtitle}
          </p>
        ) : null}
      </div>
    </div>
  );
}

/** Chữ cái đầu của tên và họ, làm ảnh tạm khi chưa có ảnh. */
function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0]?.charAt(0) ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : "";
  return (first + last).toUpperCase();
}
