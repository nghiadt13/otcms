import {
  CalendarClock,
  DoorOpen,
  LayoutDashboard,
  type LucideIcon,
  ScanLine,
  Stethoscope,
} from "lucide-react";
import { DOCTOR_ROUTES } from "@/lib/routes/doctorRoutes";

export type DoctorNavItem = {
  title: string;
  href: string;
  icon: LucideIcon;
  /** UC mà màn hình phục vụ theo Report 3 (không ghi nếu R3 không gắn UC riêng). */
  ucs?: string;
  description: string;
};

export type DoctorNavGroup = {
  label: string;
  /** true: hiện mục như liên kết cấp 1, không có tiêu đề nhóm và không thu gọn. */
  standalone?: boolean;
  items: DoctorNavItem[];
};

// Nguồn duy nhất cho sidebar, breadcrumb và trang chờ của khu bác sĩ.
// Chỉ gồm các màn không cần chọn bệnh nhân trước (Report 3, mục 1.4.2.5). Các màn khám
// (hồ sơ, phiếu khám, chỉ định, đơn thuốc, hoàn tất lượt khám...) là tab của workspace
// `/doctor/visits/[visitId]`, mở từ "Bệnh nhân hôm nay", nên không có mục ở đây.
// Thêm màn: thêm một mục ở đây rồi tạo `app/(doctor)/doctor/<đường-dẫn>/page.tsx`.
export const doctorNavGroups: DoctorNavGroup[] = [
  {
    label: "Tổng quan",
    standalone: true,
    items: [
      {
        title: "Bảng điều khiển",
        href: DOCTOR_ROUTES.dashboard,
        icon: LayoutDashboard,
        description:
          "Tóm tắt công việc trong ngày và lối vào bệnh nhân hôm nay, workspace khám, kết quả ảnh.",
      },
    ],
  },
  {
    label: "Khám bệnh",
    items: [
      {
        title: "Bệnh nhân hôm nay",
        href: DOCTOR_ROUTES.todayPatients,
        icon: Stethoscope,
        ucs: "UC35",
        description:
          "Bệnh nhân đặt hẹn và vãng lai được giao cho bạn, cùng các lượt khám đang chờ kết luận.",
      },
    ],
  },
  {
    label: "Chẩn đoán hình ảnh",
    items: [
      {
        title: "Ảnh chẩn đoán",
        href: DOCTOR_ROUTES.imagingStudies,
        icon: ScanLine,
        ucs: "UC49–UC50",
        description:
          "Các ca chụp do kỹ thuật viên gửi: xem ảnh DICOM và ghi kết luận hình ảnh.",
      },
    ],
  },
  {
    label: "Lịch làm việc",
    items: [
      {
        title: "Lịch làm việc",
        href: DOCTOR_ROUTES.schedule,
        icon: CalendarClock,
        ucs: "UC25",
        description: "Ca trực của bạn và các giờ còn nhận lịch khám.",
      },
      {
        title: "Danh sách phòng",
        href: DOCTOR_ROUTES.rooms,
        icon: DoorOpen,
        ucs: "UC64",
        description: "Các phòng khám và tình trạng sử dụng (chỉ xem).",
      },
    ],
  },
];

export const doctorNavItems: DoctorNavItem[] = doctorNavGroups.flatMap(
  (group) => group.items,
);

function matchesPath(href: string, pathname: string): boolean {
  return href === DOCTOR_ROUTES.dashboard
    ? pathname === href
    : pathname === href || pathname.startsWith(`${href}/`);
}

/** Chọn mục khớp nhất với đường dẫn hiện tại (ưu tiên href dài nhất). */
export function findActiveNavItem(pathname: string): DoctorNavItem | undefined {
  return doctorNavItems
    .filter((item) => matchesPath(item.href, pathname))
    .sort((a, b) => b.href.length - a.href.length)[0];
}

export type DoctorBreadcrumb = { groupLabel: string; itemTitle: string };

/** Nhóm cha và tên trang cho breadcrumb trên header. */
export function findActiveBreadcrumb(
  pathname: string,
): DoctorBreadcrumb | undefined {
  const item = findActiveNavItem(pathname);
  if (!item) return undefined;
  const group = doctorNavGroups.find((g) => g.items.includes(item));
  return group && { groupLabel: group.label, itemTitle: item.title };
}
