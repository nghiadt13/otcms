import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";

// Mục menu của các trang giới thiệu (header và footer dùng chung một danh sách).
// "Liên hệ" là neo tới footer nên dùng được ở mọi trang có footer.
export type SiteNavItem = { label: string; href: string };

export const siteNavItems: SiteNavItem[] = [
  { label: "Trang chủ", href: "/" },
  { label: "Giới thiệu", href: "/gioi-thieu" },
  { label: "Dịch vụ", href: "/services" },
  { label: "Bác sĩ", href: "/doctors" },
  { label: "Đánh giá", href: "/reviews" },
  { label: "Thông báo", href: "/announcements" },
  { label: "Liên hệ", href: "#lien-he" },
];

// Đặt lịch cần tài khoản bệnh nhân (BR-21): khách chưa đăng nhập dừng ở bước 1 của màn đặt lịch,
// nơi có Đăng nhập và Đăng ký rồi quay lại chính màn đó.
export const bookAppointmentHref = PATIENT_ROUTES.newAppointment;

export const contactSectionId = "lien-he";

/** Mục đang mở: khớp đúng đường dẫn (trang chủ) hoặc đường dẫn con. Neo không bao giờ "đang mở". */
export function isActiveNavItem(href: string, pathname: string) {
  if (href.startsWith("#")) return false;
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}

/** Link gọi điện từ số hotline hiển thị (bỏ khoảng trắng, ví dụ "1900 0000" → "tel:19000000"). */
export function hotlineTelHref(hotline: string) {
  return `tel:${hotline.replace(/\s/g, "")}`;
}
