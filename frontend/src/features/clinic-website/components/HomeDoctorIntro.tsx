import { ArrowRight, UsersRound } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { publicAssetPath } from "@/lib/publicAssetPath";
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
        <div
          aria-label="Ảnh minh họa đội ngũ bác sĩ"
          className="relative aspect-[16/10] overflow-hidden rounded-xl bg-secondary p-2 sm:p-3"
          role="img"
        >
          <div className="grid size-full grid-cols-3 gap-2 sm:gap-3">
            {[
              ["doctor-senior-orthopedic.jpg", "Bác sĩ chấn thương chỉnh hình"],
              ["doctor-female-tablet.jpg", "Bác sĩ chẩn đoán hình ảnh"],
              ["doctor-male-glasses.jpg", "Bác sĩ chuyên khoa"],
            ].map(([image, alt]) => (
              <div
                className="relative min-w-0 overflow-hidden rounded-lg bg-background"
                key={image}
              >
                <Image
                  alt={alt}
                  className="object-cover object-top"
                  fill
                  sizes="(min-width: 768px) 17vw, 30vw"
                  src={publicAssetPath(`/images/home/${image}`)}
                />
              </div>
            ))}
          </div>
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
