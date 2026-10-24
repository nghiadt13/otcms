import {
  CalendarDays,
  CalendarPlus,
  ListOrdered,
  type LucideIcon,
  UserCheck,
} from "lucide-react";
import type { AccountType, StaffRole } from "@/lib/domain/clinicRoles";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";

// Nguồn duy nhất cho menu khu Lễ tân (sidebar và tiêu đề header). Chỉ khai báo mục khi
// trang của mục đó đã code xong, để không có liên kết chết. Thêm mục = thêm một dòng
// vào STAFF_NAV_ITEMS; nhóm mới thì thêm vào STAFF_NAV_GROUPS trước.

export const STAFF_NAV_GROUPS = ["Tiếp nhận", "Lịch hẹn"] as const;
export type StaffNavGroup = (typeof STAFF_NAV_GROUPS)[number];

export interface StaffNavItem {
  title: string;
  href: string;
  icon: LucideIcon;
  group: StaffNavGroup;
  /** Vai trò thấy mục này trên menu. Quyền thật do BE kiểm từng thao tác. */
  roles: StaffRole[];
}

export interface StaffNavGroupView {
  group: StaffNavGroup;
  items: StaffNavItem[];
}

/**
 * Mục menu của khu Lễ tân. Chỉ có mục khi trang đã có: tiếp nhận (phase 04), hàng đợi tiếp nhận
 * (phase 05), lịch hẹn và đặt lịch (phase 02).
 */
export const STAFF_NAV_ITEMS: readonly StaffNavItem[] = [
  {
    title: "Tiếp nhận bệnh nhân",
    href: STAFF_ROUTES.checkIn,
    icon: UserCheck,
    group: "Tiếp nhận",
    roles: ["RECEPTIONIST"],
  },
  {
    title: "Hàng đợi tiếp nhận",
    href: STAFF_ROUTES.receptionQueue,
    icon: ListOrdered,
    group: "Tiếp nhận",
    roles: ["RECEPTIONIST"],
  },
  {
    title: "Lịch hẹn",
    href: STAFF_ROUTES.appointments,
    icon: CalendarDays,
    group: "Lịch hẹn",
    roles: ["RECEPTIONIST"],
  },
  {
    title: "Đặt lịch",
    href: STAFF_ROUTES.newAppointment,
    icon: CalendarPlus,
    group: "Lịch hẹn",
    roles: ["RECEPTIONIST"],
  },
];

/** Lọc mục theo vai trò hiện tại; giữ nguyên thứ tự khai báo. */
export function filterStaffNavItemsByRole(
  items: readonly StaffNavItem[],
  role: AccountType,
): StaffNavItem[] {
  return items.filter((item) => {
    // StaffRole là tập con của AccountType nên có thể nhìn mảng như AccountType[].
    const allowed: readonly AccountType[] = item.roles;
    return allowed.includes(role);
  });
}

/** Nhóm các mục theo thứ tự cố định, bỏ nhóm không có mục nào. */
export function groupStaffNavItems(
  items: readonly StaffNavItem[] = STAFF_NAV_ITEMS,
): StaffNavGroupView[] {
  return STAFF_NAV_GROUPS.flatMap((group) => {
    const groupItems = items.filter((item) => item.group === group);
    return groupItems.length > 0 ? [{ group, items: groupItems }] : [];
  });
}

/**
 * Mục đang mở theo đường dẫn. Khớp đúng hoặc đường dẫn con (`/staff/x/abc` thuộc `/staff/x`),
 * khi nhiều mục cùng khớp thì lấy mục dài nhất (`/staff/appointments/new` không thuộc
 * `/staff/appointments` mà phải chọn "Đặt lịch").
 */
export function findActiveStaffNavItem(
  pathname: string | null | undefined,
  items: readonly StaffNavItem[] = STAFF_NAV_ITEMS,
): StaffNavItem | undefined {
  if (!pathname) return undefined;
  let best: StaffNavItem | undefined;
  for (const item of items) {
    const matches =
      pathname === item.href || pathname.startsWith(`${item.href}/`);
    if (matches && (!best || item.href.length > best.href.length)) {
      best = item;
    }
  }
  return best;
}
