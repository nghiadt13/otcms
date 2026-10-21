import { CalendarPlus, Stethoscope } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { cn } from "@/lib/utils";
import { bookAppointmentHref } from "../siteNavigation";
import type { HomeHeroContent } from "../types/websiteContent";

// Khối đầu trang chủ: ảnh thật (bác sĩ giải thích phim X-quang cho bệnh nhân lớn
// tuổi, Unsplash) phủ toàn chiều ngang; bác sĩ và phim chụp nằm nửa phải.
// Từ lg, ảnh chỉ chiếm 3/4 bên phải để bác sĩ và phim chụp lùi khỏi vùng chữ; mép
// trái là nền tối trung tính (foreground, không ám xanh) chuyển mượt vào ảnh và
// trong suốt từ 60% chiều ngang, nên phần ảnh còn lại giữ màu gốc. Màn hẹp phủ đều
// vì chữ trải hết ngang. Bóng chữ nhẹ đỡ thêm ở vùng ảnh sáng.
// Chỉ 3 phần chữ: h1 duy nhất của trang, mô tả, hai nút; không đặt nhãn kicker
// phía trên h1, tiêu đề tự mang trọng lượng. Đáy chừa `pb-36`
// để hàng truy cập nhanh (HomeQuickAccess) đè lên mép dưới.
export function HomeHero({ hero }: { hero: HomeHeroContent }) {
  return (
    <section className="relative isolate overflow-hidden bg-foreground text-white">
      <div className="absolute inset-y-0 right-0 -z-20 w-full lg:w-3/4">
        <Image
          alt=""
          className="object-cover object-[60%_35%]"
          fill
          priority
          sizes="(min-width: 1024px) 75vw, 100vw"
          src="/images/home/hero-xray-consultation.jpg"
        />
      </div>
      <div
        aria-hidden
        className="absolute inset-0 -z-10 bg-foreground/65 lg:bg-transparent lg:bg-gradient-to-r lg:from-foreground lg:from-25% lg:via-foreground/60 lg:via-45% lg:to-transparent lg:to-60%"
      />

      <div
        className={cn(
          publicContainerClass,
          "flex min-h-[600px] flex-col justify-center py-20 lg:min-h-[680px] lg:pb-36",
        )}
      >
        <div className="max-w-2xl [text-shadow:0_1px_12px_rgb(0_0_0/0.35)]">
          <h1 className="text-4xl font-semibold tracking-tight text-balance sm:text-5xl xl:text-6xl xl:leading-[1.08]">
            {hero.title}
          </h1>
          <p className="mt-6 max-w-xl text-lg leading-8 text-pretty text-white/90 xl:text-xl xl:leading-9">
            {hero.description}
          </p>

          <div className="mt-10 flex flex-wrap gap-4">
            <Link
              className={cn(
                publicButtonClass("primary", "lg"),
                "bg-white text-brand-hover hover:bg-brand-soft",
              )}
              href={bookAppointmentHref}
            >
              <CalendarPlus aria-hidden />
              Đặt lịch khám
            </Link>
            <Link
              className={cn(
                publicButtonClass("secondary", "lg"),
                "border-white/60 bg-transparent text-white hover:border-white hover:bg-white/10 hover:text-white",
              )}
              href="/doctors"
            >
              <Stethoscope aria-hidden />
              Xem đội ngũ bác sĩ
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
