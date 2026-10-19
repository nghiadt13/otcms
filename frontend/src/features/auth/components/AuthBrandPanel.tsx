import { Activity, CalendarCheck, FileText } from "lucide-react";
import Image from "next/image";
import { OtcmsLogo } from "@/components/brand/OtcmsLogo";

// Ba lợi ích cho bệnh nhân, bám đúng phạm vi UC (đặt lịch, theo dõi lượt khám,
// xem kết quả đã được phòng khám phát hành). Không phải trình tự nên không đánh số.
const benefits = [
  {
    icon: CalendarCheck,
    title: "Đặt lịch khám trực tuyến",
    detail: "Chọn bác sĩ và giờ trống phù hợp, không cần gọi điện.",
  },
  {
    icon: Activity,
    title: "Theo dõi lượt khám",
    detail: "Biết mình đang ở bước nào và phòng cần đến tiếp theo.",
  },
  {
    icon: FileText,
    title: "Xem kết quả và đơn thuốc",
    detail: "Báo cáo khám, ảnh chẩn đoán và đơn thuốc khi bác sĩ phát hành.",
  },
];

// Panel bên trái của màn xác thực (chỉ hiện từ màn lg trở lên). Nền teal, chữ
// trắng (5.5:1) và brand-muted (≈4.7:1). Ảnh thật cùng bộ với trang chủ (Unsplash,
// xem ui-guidelines mục 4) thay cho minh họa vẽ tay, để màn đăng nhập trông như
// cùng một phòng khám. Khẩu hiệu là <p> chứ không phải heading để trang vẫn chỉ
// có một h1 (tiêu đề form).
export function AuthBrandPanel() {
  return (
    <aside className="hidden bg-brand text-white lg:sticky lg:top-0 lg:flex lg:h-dvh lg:flex-col lg:overflow-y-auto">
      <div className="mx-auto flex w-full max-w-lg flex-1 flex-col gap-8 px-10 py-8">
        <OtcmsLogo tone="onBrand" />

        {/* Màn thấp (laptop 768–800px) ẩn ảnh để chữ không phải cuộn. */}
        <div className="relative aspect-[16/9] overflow-hidden rounded-xl bg-brand-hover [@media(max-height:800px)]:hidden">
          <Image
            alt=""
            className="object-cover"
            fill
            sizes="440px"
            src="/images/home/clinic-consultation.jpg"
          />
        </div>

        <div>
          <p className="text-3xl font-semibold tracking-tight text-balance xl:text-4xl">
            Chăm sóc bạn. Kết nối mọi bước khám.
          </p>
          <p className="mt-4 text-lg leading-8 text-brand-muted">
            OTCMS giúp bệnh nhân và phòng khám chấn thương chỉnh hình theo dõi
            cùng một hành trình: từ đặt lịch, tiếp nhận, khám, chẩn đoán hình
            ảnh đến thanh toán.
          </p>
        </div>

        <ul className="divide-y divide-white/15 border-y border-white/15">
          {benefits.map(({ icon: Icon, title, detail }) => (
            <li className="py-4" key={title}>
              <p className="flex items-center gap-3 text-lg font-semibold">
                <Icon
                  aria-hidden
                  className="size-5 shrink-0 text-brand-muted"
                />
                {title}
              </p>
              <p className="mt-1 pl-8 text-base leading-7 text-brand-muted">
                {detail}
              </p>
            </li>
          ))}
        </ul>

        <p className="mt-auto text-sm text-brand-muted">
          © {new Date().getFullYear()} OTCMS · Hệ thống quản lý phòng khám chấn
          thương chỉnh hình
        </p>
      </div>
    </aside>
  );
}
