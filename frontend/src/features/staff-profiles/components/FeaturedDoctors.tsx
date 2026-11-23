import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { PublicSectionHeading } from "@/components/brand/PublicSectionHeading";
import type { PublicDoctor } from "../types/publicDoctor";
import { DoctorCard } from "./DoctorCard";

// Khối "Đội ngũ bác sĩ" trên trang chủ: tối đa 4 bác sĩ đã công khai hồ sơ
// (BR-09), nút "Xem tất cả bác sĩ" dẫn tới /doctors. Không có ai thì ẩn khối.
export function FeaturedDoctors({ doctors }: { doctors: PublicDoctor[] }) {
  if (doctors.length === 0) return null;
  return (
    <section
      aria-labelledby="featured-doctors-title"
      className="bg-secondary/40 py-12 sm:py-16 lg:py-20"
    >
      <div className={publicContainerClass}>
        <PublicSectionHeading
          action={
            <Link className={publicButtonClass("secondary")} href="/doctors">
              Xem tất cả bác sĩ
              <ArrowRight aria-hidden />
            </Link>
          }
          description="Tìm hiểu hồ sơ bác sĩ và lịch khám để lựa chọn phù hợp với bạn."
          id="featured-doctors-title"
          title="Đội ngũ bác sĩ"
        />
        <ul className="mt-8 grid gap-x-6 gap-y-8 sm:grid-cols-2 lg:grid-cols-4">
          {doctors.map((doctor) => (
            <li key={doctor.id}>
              <DoctorCard doctor={doctor} headingLevel={3} />
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
