import {
  CalendarClock,
  LayoutDashboard,
  type LucideIcon,
  ScanLine,
  Stethoscope,
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
// Chỉ gồm các màn không cần chọn bệnh nhân trước (Report 3, mục 1.4.2.5). Các màn khám là
// tab của workspace `/doctor/visits/[visitId]` (xem doctorVisitNavigation.ts). Màn liên
// quan nhau gom vào một mục; mỗi phần bên trong khai báo ở `parts`.
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
        title: "Ảnh chẩn đoán",
        href: DOCTOR_ROUTES.imagingStudies,
        icon: ScanLine,
        ucs: "UC49–UC50",
        description:
          "Các ca chụp do kỹ thuật viên gửi: xem ảnh DICOM và ghi kết luận hình ảnh.",
        parts: [
          {
            title: "Danh sách ca chụp",
            description: "Các ca chụp thuộc phạm vi chăm sóc của bác sĩ.",
            ucs: "UC49",
          },
          {
            title: "Xem ảnh DICOM",
            description: "Mở ca chụp, chuyển giữa các series.",
            ucs: "UC49",
          },
          {
            title: "Kết luận hình ảnh",
            description: "Ghi kết luận cho ca chụp đã xem, có thể lưu nháp.",
            ucs: "UC50",
          },
        ],
      },
    ],
  },
  {
    label: "Làm việc",
    standalone: true,
    items: [
      {
        title: "Lịch và phòng",
        href: DOCTOR_ROUTES.schedule,
        icon: CalendarClock,
        ucs: "UC25, UC64",
        description:
          "Ca trực của bạn, các giờ còn nhận lịch khám và tình trạng các phòng khám.",
        parts: [
          {
            title: "Lịch làm việc",
            description: "Ca trực của bạn và các giờ còn nhận lịch khám.",
            ucs: "UC25",
          },
          {
            title: "Danh sách phòng",
            description: "Các phòng khám và tình trạng sử dụng (chỉ xem).",
            ucs: "UC64",
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
