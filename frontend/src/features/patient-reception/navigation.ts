import {
  Bell,
  CalendarDays,
  CalendarClock,
  ClipboardList,
  CreditCard,
  DoorOpen,
  FileCheck2,
  History,
  LayoutDashboard,
  ListOrdered,
  ReceiptText,
  Search,
  UserRound,
  UsersRound,
  Workflow,
} from "lucide-react";

export const receptionNavigation = [
  {
    section: "",
    title: "Tiếp nhận bệnh nhân",
    description:
      "Đối chiếu bệnh nhân, kiểm tra phí khám và tiếp nhận vào phòng.",
    icon: LayoutDashboard,
    group: "Tiếp nhận",
  },
  {
    section: "patients",
    title: "Hồ sơ bệnh nhân",
    description:
      "Tra cứu và cập nhật thông tin định danh, liên hệ của bệnh nhân.",
    icon: UsersRound,
    group: "Tiếp nhận",
  },
  {
    section: "appointments",
    title: "Lịch hẹn",
    description: "Đặt lịch và sắp xếp bác sĩ thay thế khi vắng ca.",
    icon: CalendarDays,
    group: "Tiếp nhận",
  },
  {
    section: "queues",
    title: "Hàng đợi phòng",
    description: "Gọi bệnh nhân, xử lý vắng mặt và điều phối hàng đợi.",
    icon: ListOrdered,
    group: "Điều phối",
  },
  {
    section: "visits",
    title: "Tiến trình lượt khám",
    description:
      "Theo dõi các phòng và trạng thái dịch vụ trong một lượt khám.",
    icon: Workflow,
    group: "Điều phối",
  },
  {
    section: "orders",
    title: "Trạng thái chỉ định",
    description: "Tra cứu tiến độ thực hiện dịch vụ hình ảnh và xét nghiệm.",
    icon: ClipboardList,
    group: "Điều phối",
  },
  {
    section: "payments",
    title: "Thu phí tại quầy",
    description: "Thu trọn từng khoản của cùng lịch hẹn hoặc lượt khám.",
    icon: CreditCard,
    group: "Thanh toán",
  },
  {
    section: "payment-history",
    title: "Lịch sử thanh toán",
    description: "Tra cứu các khoản đã thu và in biên lai tương ứng.",
    icon: History,
    group: "Thanh toán",
  },
  {
    section: "results",
    title: "Phiếu kết quả khám",
    description: "Mở và in phiếu kết quả đã được hệ thống phát hành.",
    icon: FileCheck2,
    group: "Tra cứu",
  },
  {
    section: "rooms",
    title: "Phòng và tình trạng",
    description: "Xem phòng hoạt động, dịch vụ hỗ trợ và số người đang chờ.",
    icon: DoorOpen,
    group: "Tra cứu",
  },
  {
    section: "services",
    title: "Danh mục dịch vụ",
    description: "Tra cứu dịch vụ và giá hiện tại để hướng dẫn bệnh nhân.",
    icon: Search,
    group: "Tra cứu",
  },
  {
    section: "schedule",
    title: "Ca làm việc của tôi",
    description: "Xem lịch làm việc được phân công tại quầy tiếp nhận.",
    icon: CalendarClock,
    group: "Cá nhân",
  },
  {
    section: "notifications",
    title: "Thông báo",
    description: "Đọc các thông báo liên quan đến công việc của bạn.",
    icon: Bell,
    group: "Cá nhân",
  },
  {
    section: "account",
    title: "Tài khoản của tôi",
    description: "Xem tài khoản, đổi ảnh đại diện và mật khẩu.",
    icon: UserRound,
    group: "Cá nhân",
  },
] as const;
export type ReceptionSection = (typeof receptionNavigation)[number]["section"];
export const receptionHref = (section = "") =>
  `/reception${section ? `/${section}` : ""}`;
export const ReceiptIcon = ReceiptText;
