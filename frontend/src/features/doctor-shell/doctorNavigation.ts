import {
  Bell,
  CalendarDays,
  Clock3,
  DoorOpen,
  LayoutDashboard,
  type LucideIcon,
  Stethoscope,
  UserRound,
} from "lucide-react";
import { DOCTOR_ROUTES } from "@/lib/routes/doctorRoutes";

/** Một phần liên quan nằm chung trong một màn (tương ứng một màn hoặc UC của Report 3). */
export type DoctorScreenPart = {
  title: string;
  description: string;
  /** UC phục vụ phần này theo Report 3 (không ghi nếu R3 không gắn UC riêng). */
  ucs?: string;
};

export type DoctorNavItem = {
  title: string;
  href: string;
  icon: LucideIcon;
  ucs?: string;
  description: string;
  parts: DoctorScreenPart[];
};

export type DoctorNavGroup = {
  label: string;
  /** true: hiện mục như liên kết cấp 1, không có tiêu đề nhóm và không thu gọn. */
  standalone?: boolean;
  items: DoctorNavItem[];
};

// Nguồn duy nhất cho sidebar, breadcrumb và trang chờ của khu bác sĩ.
// Các nhánh từ dashboard bác sĩ trong screen flow. Các màn khám theo một bệnh nhân là
// tab của workspace `/doctor/visits/[visitId]` (xem doctorVisitNavigation.ts).
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
        parts: [
          {
            title: "Tóm tắt công việc",
            description:
              "Số bệnh nhân hôm nay, lượt khám chờ kết luận, ảnh mới cần xem.",
          },
        ],
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
        parts: [
          {
            title: "Danh sách bệnh nhân hôm nay",
            description:
              "Trạng thái từng lượt khám và trạng thái chỉ định; bấm để mở workspace khám.",
            ucs: "UC35",
          },
        ],
      },
      {
        title: "Lịch sử lịch hẹn",
        href: DOCTOR_ROUTES.appointmentHistory,
        icon: CalendarDays,
        ucs: "UC24",
        description: "Xem lịch hẹn và trạng thái các lượt hẹn của bạn.",
        parts: [
          {
            title: "Lịch sử lịch hẹn và trạng thái",
            description: "Tra cứu lịch hẹn theo khoảng ngày và trạng thái.",
            ucs: "UC24",
          },
        ],
      },
    ],
  },
  {
    label: "Làm việc",
    items: [
      {
        title: "Lịch làm việc",
        href: DOCTOR_ROUTES.schedule,
        icon: Clock3,
        ucs: "UC25",
        description: "Xem các ca làm việc của bạn.",
        parts: [
          {
            title: "Lịch làm việc",
            description: "Ca trực của bạn theo khoảng ngày.",
            ucs: "UC25",
          },
        ],
      },
      {
        title: "Danh sách phòng",
        href: DOCTOR_ROUTES.rooms,
        icon: DoorOpen,
        ucs: "UC64",
        description: "Tra cứu phòng khám và tình trạng hoạt động.",
        parts: [
          {
            title: "Danh sách phòng",
            description: "Các phòng khám và tình trạng sử dụng (chỉ xem).",
            ucs: "UC64",
          },
        ],
      },
    ],
  },
  {
    label: "Tài khoản",
    items: [
      {
        title: "Thông báo",
        href: DOCTOR_ROUTES.notifications,
        icon: Bell,
        description: "Xem thông báo và mở chi tiết thông báo.",
        parts: [
          {
            title: "Danh sách thông báo",
            description: "Các thông báo dành cho bạn.",
          },
          {
            title: "Chi tiết thông báo",
            description: "Mở nội dung đầy đủ của một thông báo.",
          },
        ],
      },
      {
        title: "Quản lý tài khoản",
        href: DOCTOR_ROUTES.account,
        icon: UserRound,
        description: "Xem và cập nhật hồ sơ tài khoản cá nhân.",
        parts: [
          {
            title: "Hồ sơ tài khoản",
            description: "Thông tin hồ sơ và cài đặt tài khoản.",
          },
        ],
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
