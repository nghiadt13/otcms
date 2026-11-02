import {
  ArrowRight,
  CalendarPlus,
  ClipboardList,
  FileSearch,
  Stethoscope,
} from "lucide-react";
import Link from "next/link";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";

const quickLinkClass =
  "group flex h-full flex-col gap-2 px-6 py-5 outline-none transition-colors duration-150 hover:bg-secondary/60 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:ring-inset motion-reduce:transition-none lg:px-7 lg:py-7";

// Bốn việc khách và bệnh nhân hay làm nhất. Xem kết quả cần đăng nhập vì kết
// quả chỉ hiện với bệnh nhân sau khi bác sĩ phát hành.
const quickLinks = [
  {
    icon: CalendarPlus,
    title: "Đặt lịch khám",
    detail: "Chọn bác sĩ và giờ trống phù hợp, không cần gọi điện.",
    href: null,
  },
  {
    icon: Stethoscope,
    title: "Tìm bác sĩ",
    detail: "Xem chuyên môn và kinh nghiệm của từng bác sĩ.",
    href: "/doctors",
  },
  {
    icon: ClipboardList,
    title: "Dịch vụ và bảng giá",
    detail: "Giá công khai cho khám, chụp chiếu và điều trị.",
    href: "/services",
  },
  {
    icon: FileSearch,
    title: "Xem kết quả khám",
    detail: "Đăng nhập để xem báo cáo và ảnh chẩn đoán.",
    href: "/login",
  },
];

// Một thanh lối tắt liền khối (không phải bốn thẻ rời): các ô ngăn bằng vạch
// mảnh, icon nhỏ đi cùng tiêu đề. Từ lg nằm ngang và đè lên mép dưới hero
// (`-mt-20`); màn hẹp là danh sách dọc gọn để không đẩy nội dung chính xuống xa.
export function HomeQuickAccess() {
  return (
    <section
      aria-label="Truy cập nhanh"
      className="relative z-10 pb-16 lg:-mt-20 lg:pb-24"
    >
      <div className={`${publicContainerClass} pt-8 lg:pt-0`}>
        <ul className="divide-y divide-border overflow-hidden rounded-2xl border bg-card shadow-lg shadow-foreground/5 lg:grid lg:grid-cols-4 lg:divide-x lg:divide-y-0">
          {quickLinks.map(({ icon: Icon, title, detail, href }) => (
            <li key={title}>
              {(() => {
                const content = (
                  <>
                    <span className="flex items-center gap-3 text-lg font-semibold">
                      <Icon
                        aria-hidden
                        className="size-5 shrink-0 text-primary"
                      />
                      {title}
                      <ArrowRight
                        aria-hidden
                        className="ml-auto size-5 text-primary transition-transform duration-150 group-hover:translate-x-1 motion-reduce:transition-none"
                      />
                    </span>
                    <span className="text-base leading-7 text-muted-foreground">
                      {detail}
                    </span>
                  </>
                );
                // Ô đặt lịch chọn đích theo phiên; các ô còn lại là liên kết cố định.
                return href ? (
                  <Link className={quickLinkClass} href={href}>
                    {content}
                  </Link>
                ) : (
                  <PublicBookAppointmentLink className={quickLinkClass}>
                    {content}
                  </PublicBookAppointmentLink>
                );
              })()}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
