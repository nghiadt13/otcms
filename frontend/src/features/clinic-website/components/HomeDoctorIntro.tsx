import { ArrowRight, UsersRound } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { cn } from "@/lib/utils";

export function HomeDoctorIntro() {
  return (
    <section
      aria-labelledby="home-doctors-title"
      className="bg-secondary/45 py-6 md:py-8"
    >
      <div
        className={cn(
          publicContainerClass,
          "grid items-center gap-6 md:grid-cols-2 lg:gap-11",
        )}
      >
        <div className="relative aspect-[16/10] overflow-hidden rounded-xl bg-secondary">
          <Image
            alt="Ảnh minh họa bác sĩ trao đổi với bệnh nhân"
            className="object-cover"
            fill
            sizes="(min-width: 768px) 50vw, 100vw"
            src="/images/home/clinic-consultation.jpg"
          />
        </div>
        <div>
          <h2
            id="home-doctors-title"
            className="text-2xl font-bold tracking-tight text-brand-hover lg:text-4xl"
          >
            Đội ngũ bác sĩ
          </h2>
          <p className="mt-3 max-w-xl text-base leading-relaxed lg:text-lg">
            Xem chuyên môn và lịch khám của đội ngũ bác sĩ chấn thương chỉnh
            hình để chọn bác sĩ phù hợp với nhu cầu của bạn.
          </p>
          <Link
            href="/doctors"
            className={cn(
              publicButtonClass("secondary"),
              "mt-5 border-primary text-primary",
            )}
          >
            <UsersRound aria-hidden /> Xem đội ngũ bác sĩ{" "}
            <ArrowRight aria-hidden />
          </Link>
        </div>
      </div>
    </section>
  );
}
