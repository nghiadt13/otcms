import { CalendarDays, UsersRound } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";
import { cn } from "@/lib/utils";

export function HomeHero() {
  return (
    <section className="relative isolate overflow-hidden bg-secondary/55">
      <div
        className={cn(
          publicContainerClass,
          "relative z-10 flex items-center pt-6 pb-4 sm:pt-8 md:min-h-[27rem] md:py-12 lg:min-h-[clamp(28rem,42vw,35rem)]",
        )}
      >
        <div className="w-full md:w-[47%]">
          <h1 className="text-[clamp(1.5rem,6.8vw,2rem)] leading-tight font-bold tracking-tight text-balance text-brand-hover sm:text-4xl/tight md:text-[clamp(2.25rem,3.6vw,3.25rem)] md:leading-tight">
            Chăm sóc vận động,{" "}
            <span className="block md:inline lg:block">vững bước mỗi ngày</span>
          </h1>
          <p className="mt-3 max-w-lg text-base leading-relaxed text-pretty md:mt-4 lg:text-xl">
            <span className="md:hidden">
              Đặt lịch khám và theo dõi kết quả thuận tiện.
            </span>
            <span className="hidden md:inline">
              Đặt lịch khám chấn thương chỉnh hình và theo dõi kết quả thuận tiện.
            </span>
          </p>
          <div className="mt-4 grid gap-2 md:mt-6 md:flex md:flex-wrap md:gap-3">
            <PublicBookAppointmentLink className={publicButtonClass("primary")}>
              <CalendarDays aria-hidden />
              Đặt lịch khám
            </PublicBookAppointmentLink>
            <Link
              className={cn(
                publicButtonClass("secondary"),
                "border-primary text-primary",
              )}
              href="/doctors"
            >
              <UsersRound aria-hidden />
              Xem đội ngũ bác sĩ
            </Link>
          </div>
        </div>
      </div>
      <div className="relative mx-4 mb-3 aspect-[2/1] overflow-hidden rounded-xl sm:mx-6 md:absolute md:inset-y-0 md:right-0 md:mx-0 md:mb-0 md:aspect-auto md:w-[58%] md:rounded-none">
        <Image
          alt="Ảnh minh họa bác sĩ tư vấn về khớp gối"
          className="object-cover object-[60%_43%] md:object-[60%_center]"
          fill
          preload
          sizes="(min-width: 768px) 58vw, 100vw"
          src="/images/home/hero-knee-consultation.webp"
        />
      </div>
      <div
        aria-hidden
        className="pointer-events-none absolute inset-y-[-24%] left-[-12%] hidden w-[63%] rounded-r-[48%] bg-gradient-to-br from-background via-background to-secondary md:block"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -bottom-28 -left-16 hidden size-56 rounded-full bg-primary/10 md:block"
      />
    </section>
  );
}
