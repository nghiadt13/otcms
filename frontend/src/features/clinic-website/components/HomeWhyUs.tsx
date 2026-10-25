import { CircleCheck, UserPlus } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { cn } from "@/lib/utils";
import type { HomeHeroContent } from "../types/websiteContent";

// Khối hai cột ảnh + chữ: vì sao khám ở đây, và lời mời tạo tài khoản bệnh nhân
// để xem lại hồ sơ. Ảnh thật (Unsplash) bác sĩ tư vấn tại bàn khám. Ba điểm mạnh
// lấy từ nội dung website (`highlights`) để Quản trị viên sửa được (BR-68).
export function HomeWhyUs({ highlights }: Pick<HomeHeroContent, "highlights">) {
  return (
    <section aria-labelledby="why-us-title" className="bg-card py-20 lg:py-28">
      <div
        className={cn(
          publicContainerClass,
          "grid items-center gap-12 lg:grid-cols-2 lg:gap-20",
        )}
      >
        <div className="relative aspect-[4/3] overflow-hidden rounded-2xl bg-secondary">
          <Image
            alt="Bác sĩ trao đổi kết quả với bệnh nhân tại bàn khám"
            className="object-cover"
            fill
            sizes="(min-width: 1024px) 50vw, 100vw"
            src="/images/home/clinic-consultation.jpg"
          />
        </div>

        <div>
          <h2
            className="text-3xl font-semibold tracking-tight text-balance xl:text-4xl"
            id="why-us-title"
          >
            Một hồ sơ cho cả hành trình điều trị
          </h2>
          <p className="mt-4 text-lg leading-8 text-pretty text-muted-foreground">
            Mỗi lần khám, chụp và tái khám đều được lưu vào hồ sơ của bạn. Bác
            sĩ lần sau nhìn thấy toàn bộ diễn tiến, bạn không phải mang theo
            phim cũ.
          </p>
          <ul className="mt-8 space-y-4">
            {highlights.map((item) => (
              <li className="flex items-start gap-3 text-lg" key={item}>
                <CircleCheck
                  aria-hidden
                  className="mt-1 size-6 shrink-0 text-primary"
                />
                {item}
              </li>
            ))}
          </ul>
          <Link
            className={cn(publicButtonClass("secondary"), "mt-10")}
            href="/register"
          >
            <UserPlus aria-hidden />
            Tạo tài khoản bệnh nhân
          </Link>
        </div>
      </div>
    </section>
  );
}
