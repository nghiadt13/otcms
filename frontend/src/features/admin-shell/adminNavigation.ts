import {
  ChartColumn,
  CalendarClock,
  CalendarDays,
  CreditCard,
  DoorOpen,
  LayoutDashboard,
  type LucideIcon,
  Megaphone,
  MessageSquareText,
  Newspaper,
  Receipt,
  ScrollText,
  Stethoscope,
  Users,
  Wrench,
} from "lucide-react";

export type AdminNavItem = {
  title: string;
  href: string;
  icon: LucideIcon;
  /** UC mà màn hình này phục vụ. Tạm theo bản UC đang soạn; đối chiếu lại khi UC_description.html được thêm vào docs/requirements. */
  ucs: string;
  description: string;
};

export type AdminNavGroup = {
  label: string;
  /** true: hiện mục như liên kết cấp 1, không có tiêu đề nhóm và không thu gọn. */
  standalone?: boolean;
  items: AdminNavItem[];
};

// Nguồn duy nhất cho sidebar, tiêu đề header, thẻ ở trang tổng quan và trang chờ.
// Thêm màn hình mới: thêm một mục ở đây rồi tạo `app/admin/<đường-dẫn>/page.tsx`.
export const adminNavGroups: AdminNavGroup[] = [
  {
    label: "Tổng quan",
    standalone: true,
    items: [
      {
        title: "Bảng điều khiển",
        href: "/admin",
        icon: LayoutDashboard,
        ucs: "UC77",
        description:
          "Tóm tắt lịch hẹn, hàng đợi, thanh toán và phản hồi theo kỳ.",
      },
    ],
  },
  {
    label: "Tài khoản và nhân sự",
    items: [
      {
        title: "Tài khoản người dùng",
        href: "/admin/accounts",
        icon: Users,
        ucs: "UC07–UC09",
        description:
          "Tạo tài khoản nhân viên, bật/tắt trạng thái, tra cứu người dùng.",
      },
      {
        title: "Hồ sơ nhân viên",
        href: "/admin/staff-profiles",
        icon: Stethoscope,
        ucs: "UC17–UC19",
        description: "Hồ sơ chuyên môn của nhân viên; bác sĩ có thêm phần hiển thị công khai.",
      },
      {
        title: "Ca làm việc",
        href: "/admin/staff-shifts",
        icon: CalendarClock,
        ucs: "UC25–UC28",
        description: "Xếp, sửa, xóa ca của lễ tân, bác sĩ, kỹ thuật viên.",
      },
    ],
  },
  {
    label: "Vận hành phòng khám",
    items: [
      {
        title: "Lịch hẹn",
        href: "/admin/appointments",
        icon: CalendarDays,
        ucs: "UC20, UC22, UC24",
        description: "Xem lịch hẹn, giờ trống của bác sĩ và chuyển lịch sang bác sĩ khác.",
      },
      {
        title: "Phòng khám",
        href: "/admin/rooms",
        icon: DoorOpen,
        ucs: "UC64–UC67",
        description: "Phòng, loại phòng, dịch vụ hỗ trợ và trạng thái sử dụng.",
      },
      {
        title: "Dịch vụ và giá",
        href: "/admin/services",
        icon: Wrench,
        ucs: "UC68–UC71",
        description: "Danh mục dịch vụ khám, chẩn đoán hình ảnh và bảng giá.",
      },
    ],
  },
  {
    label: "Nội dung và phản hồi",
    items: [
      {
        title: "Đánh giá bệnh nhân",
        href: "/admin/reviews",
        icon: MessageSquareText,
        ucs: "UC61–UC62",
        description: "Duyệt, ẩn đánh giá và phản hồi bệnh nhân.",
      },
      {
        title: "Nội dung website",
        href: "/admin/website-content",
        icon: Newspaper,
        ucs: "UC72–UC73",
        description:
          "Thông tin phòng khám, bài viết sức khỏe, trang giới thiệu.",
      },
      {
        title: "Thông báo phòng khám",
        href: "/admin/announcements",
        icon: Megaphone,
        ucs: "UC74",
        description: "Đăng thông báo cho nhóm người nhận được chọn.",
      },
    ],
  },
  {
    label: "Tài chính và báo cáo",
    items: [
      {
        title: "Lịch sử thanh toán",
        href: "/admin/payments",
        icon: CreditCard,
        ucs: "UC56",
        description: "Các phiếu thu đã hoàn tất.",
      },
      {
        title: "Khoản chưa thanh toán",
        href: "/admin/unpaid-items",
        icon: Receipt,
        ucs: "UC57",
        description: "Phí khám và chỉ định đang chờ thanh toán.",
      },
      {
        title: "Báo cáo",
        href: "/admin/reports",
        icon: ChartColumn,
        ucs: "UC78",
        description: "Tạo báo cáo vận hành theo khoảng thời gian.",
      },
    ],
  },
  {
    label: "Hệ thống",
    items: [
      {
        title: "Nhật ký hoạt động",
        href: "/admin/activity-logs",
        icon: ScrollText,
        ucs: "UC76",
        description: "Ai đã làm gì, lúc nào, trên bản ghi nào.",
      },
    ],
  },
];

export const adminNavItems: AdminNavItem[] = adminNavGroups.flatMap(
  (group) => group.items,
);

/** Chọn mục khớp nhất với đường dẫn hiện tại (ưu tiên href dài nhất). */
export function findActiveNavItem(pathname: string): AdminNavItem | undefined {
  return adminNavItems
    .filter((item) =>
      item.href === "/admin"
        ? pathname === "/admin"
        : pathname === item.href || pathname.startsWith(`${item.href}/`),
    )
    .sort((a, b) => b.href.length - a.href.length)[0];
}

export type AdminBreadcrumb = {
  groupLabel: string;
  itemTitle: string;
};

/** Tìm thông tin breadcrumb gồm nhóm cha và tên trang từ cấu hình menu admin. */
export function findActiveBreadcrumb(
  pathname: string,
): AdminBreadcrumb | undefined {
  const matches: { groupLabel: string; item: AdminNavItem }[] = [];

  for (const group of adminNavGroups) {
    for (const item of group.items) {
      const isMatch =
        item.href === "/admin"
          ? pathname === "/admin"
          : pathname === item.href || pathname.startsWith(`${item.href}/`);
      if (isMatch) {
        matches.push({ groupLabel: group.label, item });
      }
    }
  }

  if (matches.length === 0) return undefined;

  matches.sort((a, b) => b.item.href.length - a.item.href.length);
  const best = matches[0];

  return {
    groupLabel: best.groupLabel,
    itemTitle: best.item.title,
  };
}
