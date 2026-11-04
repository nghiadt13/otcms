import {
  ClipboardPlus,
  FileHeart,
  Flag,
  LayoutList,
  type LucideIcon,
  Pill,
  Stethoscope,
} from "lucide-react";
import type { SidebarNavGroupData } from "@/components/shell/SidebarNavGroup";
import { DOCTOR_ROUTES } from "@/lib/routes/doctorRoutes";
import type { DoctorScreenPart } from "./doctorNavigation";

export type DoctorVisitNavItem = {
  title: string;
  /** Đoạn cuối của đường dẫn `/doctor/visits/<visitId>/<section>`; rỗng là tab tổng quan. */
  section: string;
  icon: LucideIcon;
  description: string;
  /** Các phần liên quan nằm chung trong tab này, mỗi phần ứng với một màn/UC của R3. */
  parts: DoctorScreenPart[];
};

// Workspace khám của MỘT lượt khám (Report 3 mục 1.4.2.5 màn 8–27 và UC34–45, 49–50).
// Màn nào liên quan nhau thì gom vào cùng một tab, theo cách các UC tham chiếu nhau:
//  - UC37 ghi nhận khám, khám lâm sàng và chẩn đoán là một luồng, nên chung tab "Khám bệnh".
//  - UC42 chọn hướng điều trị "thuốc" dẫn sang UC43 và đặt tái khám qua UC21, nên chung tab.
//  - UC45 chỉ định, trạng thái chỉ định, kết quả xét nghiệm và ảnh (UC49–50) là một vòng
//    cận lâm sàng: bác sĩ chỉ định rồi đọc kết quả rồi chỉ định thêm đợt.
//  - UC36 hoàn tất, phiếu kết quả UC39 và sửa kết quả UC38 là giai đoạn kết thúc lượt khám.
// Sidebar chuyển sang danh sách này khi bác sĩ mở một lượt khám từ "Bệnh nhân hôm nay".
// Khi dựng màn thật, tạo `visits/[visitId]/<section>/page.tsx` để thay trang chờ chung.
export const doctorVisitNavItems: DoctorVisitNavItem[] = [
  {
    title: "Tổng quan",
    section: "",
    icon: LayoutList,
    description:
      "Thông tin tối thiểu của bệnh nhân, tiến trình lượt khám và lối vào các tab khám.",
    parts: [
      {
        title: "Workspace lượt khám",
        description:
          "Họ tên, tuổi, giới, mã bệnh nhân, dị ứng, tiền sử; không có số điện thoại hay địa chỉ.",
        ucs: "UC35",
      },
      {
        title: "Tiến trình lượt khám",
        description:
          "Giai đoạn của lượt khám, phòng đang chờ và trạng thái từng chỉ định (không hiện kết quả).",
        ucs: "UC34",
      },
    ],
  },
  {
    title: "Hồ sơ bệnh nhân",
    section: "records",
    icon: FileHeart,
    description:
      "Tiền sử, hồ sơ các lượt khám trước và lịch hẹn của bệnh nhân để tham khảo khi khám.",
    parts: [
      {
        title: "Tiền sử và dị ứng",
        description:
          "Ghi tiền sử bệnh chung; cập nhật dị ứng, thuốc đang dùng, tiền sử chỉnh hình và kết quả từ cơ sở khác đã xem.",
        ucs: "UC12",
      },
      {
        title: "Hồ sơ bệnh án cũ",
        description:
          "Các lượt khám trước, kể cả lượt chưa kết luận: chẩn đoán, lời dặn, đơn thuốc, kết quả hình ảnh và xét nghiệm.",
        ucs: "UC40",
      },
      {
        title: "Lịch sử lịch hẹn",
        description: "Lịch hẹn đã đặt của bệnh nhân và trạng thái từng lịch.",
        ucs: "UC24",
      },
    ],
  },
  {
    title: "Khám bệnh",
    section: "examination",
    icon: Stethoscope,
    description:
      "Ghi nhận khám, khám lâm sàng và chẩn đoán trong một luồng, xem kèm kết quả cận lâm sàng đã có.",
    parts: [
      {
        title: "Ghi nhận khám",
        description: "Lý do khám và triệu chứng.",
        ucs: "UC37",
      },
      {
        title: "Khám lâm sàng",
        description:
          "Phát hiện khi khám và loại chấn thương (vị trí, bên, cơ chế) kèm mô tả.",
        ucs: "UC37",
      },
      {
        title: "Chẩn đoán ICD-10",
        description: "Một chẩn đoán chính và các chẩn đoán phụ từ nhóm ICD-10 M và S.",
        ucs: "UC37",
      },
    ],
  },
  {
    title: "Điều trị và đơn thuốc",
    section: "treatment",
    icon: Pill,
    description:
      "Hướng điều trị, lời dặn, kê đơn điện tử và hẹn tái khám của lượt khám.",
    parts: [
      {
        title: "Hướng điều trị và lời dặn",
        description:
          "Chăm sóc tại nhà, dùng thuốc, chuyển vật lý trị liệu hoặc chuyển cơ sở khác; lời dặn vận động, phục hồi, dấu hiệu cần quay lại.",
        ucs: "UC42",
      },
      {
        title: "Kê đơn điện tử",
        description: "Thuốc và hướng dẫn sử dụng, có thể lưu nháp.",
        ucs: "UC43",
      },
      {
        title: "Hẹn tái khám",
        description: "Đặt lịch tái khám liên kết với lượt khám.",
        ucs: "UC21",
      },
    ],
  },
  {
    title: "Cận lâm sàng",
    section: "paraclinical",
    icon: ClipboardPlus,
    description:
      "Chỉ định, theo dõi chỉ định, đọc kết quả xét nghiệm và ảnh rồi chỉ định thêm nếu cần.",
    parts: [
      {
        title: "Chỉ định và trạng thái chỉ định",
        description:
          "Chỉ định chẩn đoán hình ảnh và xét nghiệm kèm lý do, thêm đợt sau khi xem kết quả, hủy hoặc thay chỉ định; theo dõi từng chỉ định đến khi có kết quả.",
        ucs: "UC45",
      },
      {
        title: "Kết quả xét nghiệm",
        description:
          "Giá trị, đơn vị, khoảng tham chiếu và cờ bất thường ngay khi kỹ thuật viên duyệt (chỉ xem).",
      },
      {
        title: "Ảnh và kết luận hình ảnh",
        description:
          "Ca chụp của lượt khám, xem ảnh DICOM và ghi kết luận hình ảnh.",
        ucs: "UC49–UC50",
      },
    ],
  },
  {
    title: "Hoàn tất lượt khám",
    section: "closing",
    icon: Flag,
    description:
      "Kiểm tra điều kiện và hoàn tất lượt khám, xem phiếu kết quả, sửa kết quả sau khi hoàn tất.",
    parts: [
      {
        title: "Hoàn tất khám",
        description:
          "Kiểm tra đã có chẩn đoán chính và mọi chỉ định có kết quả hoặc đã hủy kèm lý do, rồi tạo phiếu kết quả.",
        ucs: "UC36",
      },
      {
        title: "Phiếu kết quả khám",
        description: "Mở phiếu kết quả (PDF) và xem phiên bản hiện hành hay đã thay thế.",
        ucs: "UC39",
      },
      {
        title: "Sửa kết quả khám",
        description:
          "Chỉ với lượt đã hoàn tất: sửa chẩn đoán, phát hiện hoặc hướng điều trị, bắt buộc có lý do, lưu lịch sử phiên bản.",
        ucs: "UC38",
      },
    ],
  },
];

export function findVisitNavItem(
  section: string,
): DoctorVisitNavItem | undefined {
  return doctorVisitNavItems.find((item) => item.section === section);
}

export function visitHref(visitId: string, section: string): string {
  const base = `${DOCTOR_ROUTES.visits}/${encodeURIComponent(visitId)}`;
  return section ? `${base}/${section}` : base;
}

const VISIT_PATH = new RegExp(`^${DOCTOR_ROUTES.visits}/([^/]+)(?:/([^/]+))?/?$`);

/** Tách `/doctor/visits/<id>[/<section>]`; null nếu không thuộc workspace khám. */
export function parseVisitPath(
  pathname: string,
): { visitId: string; section: string } | null {
  const match = VISIT_PATH.exec(pathname);
  if (!match) return null;
  return { visitId: decodeURIComponent(match[1]), section: match[2] ?? "" };
}

/** Menu sidebar của một lượt khám: một nhóm không tiêu đề, href đã gắn mã lượt khám. */
export function buildVisitSidebarGroups(visitId: string): SidebarNavGroupData[] {
  return [
    {
      label: "Lượt khám",
      standalone: true,
      items: doctorVisitNavItems.map((item) => ({
        title: item.title,
        href: visitHref(visitId, item.section),
        icon: item.icon,
      })),
    },
  ];
}

/** Breadcrumb cho trang trong workspace khám; undefined nếu đường dẫn không thuộc workspace. */
export function findVisitBreadcrumb(
  pathname: string,
): { groupLabel: string; itemTitle: string } | undefined {
  const visit = parseVisitPath(pathname);
  const item = visit && findVisitNavItem(visit.section);
  return item ? { groupLabel: "Lượt khám", itemTitle: item.title } : undefined;
}
