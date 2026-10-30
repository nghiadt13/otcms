import {
  LayoutDashboard, ListOrdered, FileText, Activity, Layers, Hospital,
  CalendarDays, Bell, UserRound, LockKeyhole, type LucideIcon,
} from "lucide-react";

export type ImagingNavItem = { href: string; title: string; icon: LucideIcon };
export type ImagingNavGroup = { label: string; items: ImagingNavItem[] };
export const imagingNavGroups: ImagingNavGroup[] = [
  {
    label: "Công việc",
    items: [
      {
        href: "/imaging",
        title: "Tổng quan",
        icon: LayoutDashboard,
      },
      {
        href: "/imaging/worklist",
        title: "Danh sách chờ",
        icon: ListOrdered,
      },
      {
        href: "/imaging/orders",
        title: "Chỉ định hình ảnh",
        icon: FileText,
      },
      {
        href: "/imaging/status",
        title: "Trạng thái chỉ định",
        icon: Activity,
      },
      {
        href: "/imaging/visits",
        title: "Tiến trình lượt khám",
        icon: Layers,
      },
    ],
  },
  {
    label: "Thông tin công việc",
    items: [
      {
        href: "/imaging/rooms",
        title: "Phòng dịch vụ",
        icon: Hospital,
      },
      {
        href: "/imaging/schedule",
        title: "Lịch làm việc",
        icon: CalendarDays,
      },
      {
        href: "/imaging/notifications",
        title: "Thông báo",
        icon: Bell,
      },
    ],
  },
  {
    label: "Cá nhân",
    items: [
      {
        href: "/imaging/profile",
        title: "Tài khoản của tôi",
        icon: UserRound,
      },
      {
        href: "/imaging/change-password",
        title: "Đổi mật khẩu",
        icon: LockKeyhole,
      },
    ],
  },
];
export function isImagingItemActive(pathname: string, href: string) {
  return href === "/imaging" ? pathname === href : pathname.startsWith(href);
}
export function findActiveImagingItem(pathname: string) {
  return imagingNavGroups
    .flatMap((g) => g.items)
    .filter((item) => isImagingItemActive(pathname, item.href))
    .at(-1);
}
