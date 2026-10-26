import {
  Activity,
  CalendarDays,
  FileText,
  Pill,
  Receipt,
  ScanLine,
  Star,
  UserCog,
  Users,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

// Nguồn duy nhất cho menu khu bệnh nhân (header và Sheet màn hẹp). Chỉ khai báo mục khi
// trang của phase đó đã code xong (bảng §4.2 của plan 261024-0512): mục chưa có không
// hiện, tránh link chết. Thêm mục = thêm một dòng vào đúng danh sách bên dưới.

export const PATIENT_NAV_GROUPS = ["Khám bệnh", "Thanh toán", "Hồ sơ"] as const;
export type PatientNavGroup = (typeof PATIENT_NAV_GROUPS)[number];

export interface PatientNavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  group: PatientNavGroup;
}

export interface PatientNavGroupView {
  group: PatientNavGroup;
  items: PatientNavItem[];
}

export interface PatientAccountLink {
  label: string;
  href: string;
  icon: LucideIcon;
}

/** Mục menu theo nhóm. Mỗi mục khai báo khi trang của nó đã code xong. */
export const PATIENT_NAV_ITEMS: readonly PatientNavItem[] = [
  {
    label: "Lịch hẹn",
    href: PATIENT_ROUTES.appointments,
    icon: CalendarDays,
    group: "Khám bệnh",
  },
  {
    label: "Lượt khám hôm nay",
    href: PATIENT_ROUTES.visits,
    icon: Activity,
    group: "Khám bệnh",
  },
  {
    label: "Kết quả khám",
    href: "/portal/results",
    icon: FileText,
    group: "Khám bệnh",
  },
  {
    label: "Đơn thuốc",
    href: "/portal/prescriptions",
    icon: Pill,
    group: "Khám bệnh",
  },
  {
    label: "Ảnh chụp",
    href: "/portal/images",
    icon: ScanLine,
    group: "Khám bệnh",
  },
  {
    label: "Đánh giá",
    href: PATIENT_ROUTES.reviews,
    icon: Star,
    group: "Khám bệnh",
  },
  {
    label: "Khoản cần thanh toán",
    href: "/portal/payments",
    icon: Wallet,
    group: "Thanh toán",
  },
  {
    label: "Lịch sử thanh toán",
    href: "/portal/payments/history",
    icon: Receipt,
    group: "Thanh toán",
  },
  {
    label: "Hồ sơ bệnh nhân",
    href: "/portal/profiles",
    icon: Users,
    group: "Hồ sơ",
  },
];

/**
 * Mục trong menu tài khoản, đứng trên "Đăng xuất". "Tài khoản" thuộc nhóm Hồ sơ trong
 * bảng §4.2 nhưng nằm ở đây để menu tài khoản luôn có lối vào hồ sơ đăng nhập.
 */
export const PATIENT_ACCOUNT_LINKS: readonly PatientAccountLink[] = [
  { label: "Tài khoản", href: PATIENT_ROUTES.account, icon: UserCog },
];

/** Nhóm các mục theo thứ tự cố định, bỏ nhóm không có mục nào; giữ thứ tự khai báo. */
export function groupPatientNavItems(
  items: readonly PatientNavItem[] = PATIENT_NAV_ITEMS,
): PatientNavGroupView[] {
  return PATIENT_NAV_GROUPS.flatMap((group) => {
    const groupItems = items.filter((item) => item.group === group);
    return groupItems.length > 0 ? [{ group, items: groupItems }] : [];
  });
}
