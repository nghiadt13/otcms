import { CalendarPlus } from "lucide-react";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { cn } from "@/lib/utils";
import { bookAppointmentHref, hotlineTelHref } from "../siteNavigation";

// Lời mời đặt lịch cuối trang: một dải sáng ngăn bằng vạch trên, không phải khối
// màu lớn (footer ngay dưới đã là nền teal). Hai đường: nút đặt lịch trên web,
// hoặc số hotline viết to như một dữ liệu để người lớn tuổi đọc và bấm gọi.
export function HomeBookingCta({ hotline }: { hotline: string }) {
  return (
    <section aria-labelledby="booking-cta-title">
      <div className={publicContainerClass}>
        {/* Vạch trên nằm trong khung nội dung để thẳng mép chữ, không tràn ra lề. */}
        <div className="grid gap-10 border-t py-16 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-end lg:gap-20 lg:py-20">
          <div className="max-w-2xl">
            <h2
              className="text-3xl font-semibold tracking-tight text-balance xl:text-4xl"
              id="booking-cta-title"
            >
              Đau khớp, chấn thương? Đặt lịch khám hôm nay.
            </h2>
            <p className="mt-4 text-lg leading-8 text-pretty text-muted-foreground">
              Chọn giờ còn trống trong hai phút. Cần tư vấn trước, gọi hotline
              trong giờ làm việc.
            </p>
            <Link
              className={cn(publicButtonClass("primary", "lg"), "mt-8")}
              href={bookAppointmentHref}
            >
              <CalendarPlus aria-hidden />
              Đặt lịch khám
            </Link>
          </div>

          <a
            className="group rounded-lg outline-none focus-visible:ring-3 focus-visible:ring-ring/50 lg:text-right"
            href={hotlineTelHref(hotline)}
          >
            <span className="block text-base text-muted-foreground">
              Hoặc gọi hotline
            </span>
            <span className="mt-1 block text-4xl font-semibold tracking-tight text-primary tabular-nums underline-offset-8 group-hover:underline xl:text-5xl">
              {hotline}
            </span>
          </a>
        </div>
      </div>
    </section>
  );
}
