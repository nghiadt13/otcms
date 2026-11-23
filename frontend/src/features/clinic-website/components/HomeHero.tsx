import { CalendarPlus, Stethoscope } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";
import { cn } from "@/lib/utils";
import type { HomeHeroContent } from "../types/websiteContent";

export function HomeHero({ hero }: { hero: HomeHeroContent }) {
  return (
    <section className="overflow-hidden bg-gradient-to-br from-secondary via-background to-secondary/60">
      <div
        className={cn(
          publicContainerClass,
          "grid items-center gap-8 py-8 sm:py-12 lg:grid-cols-2 lg:gap-12 lg:py-14",
        )}
      >
        <div className="min-w-0">
          <h1 className="text-4xl leading-tight font-semibold tracking-tight text-balance text-brand-hover sm:text-5xl xl:text-6xl">
            {hero.title}
          </h1>
          <p className="mt-5 max-w-xl text-base leading-7 text-pretty text-muted-foreground sm:text-lg sm:leading-8">
            {hero.description}
          </p>
          <div className="mt-7 grid gap-3 sm:flex sm:flex-wrap lg:mt-8">
            <PublicBookAppointmentLink className={publicButtonClass("primary")}>
              <CalendarPlus aria-hidden />
              Đặt lịch khám
            </PublicBookAppointmentLink>
            <Link className={publicButtonClass("secondary")} href="/doctors">
              <Stethoscope aria-hidden />
              Xem đội ngũ bác sĩ
            </Link>
          </div>
        </div>
        <div className="relative aspect-[4/3] min-w-0 overflow-hidden rounded-xl bg-secondary lg:aspect-[5/4]">
          <Image
            alt="Bác sĩ tư vấn và giải thích phim chụp cho bệnh nhân"
            className="object-cover object-[60%_35%]"
            fill
            preload
            sizes="(min-width: 1280px) 584px, (min-width: 1024px) 50vw, 100vw"
            src="/images/home/hero-xray-consultation.jpg"
          />
        </div>
      </div>
    </section>
  );
}
