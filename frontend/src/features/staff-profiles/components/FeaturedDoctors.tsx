import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { publicContainerClass } from "@/components/brand/publicLayout";
import { PublicSectionHeading } from "@/components/brand/PublicSectionHeading";
import type { FeaturedDoctor } from "../types/staffProfile";
import { DoctorCard } from "./DoctorCard";

// Khối "Đội ngũ bác sĩ" trên trang chủ: tối đa 4 bác sĩ đã công khai hồ sơ
// (BR-09), nút "Xem tất cả bác sĩ" dẫn tới /doctors. Không có ai thì ẩn khối.
export function FeaturedDoctors({ doctors }: { doctors: FeaturedDoctor[] }) {
  if (doctors.length === 0) return null;
  return (
    <section
      aria-labelledby="featured-doctors-title"
      className="py-20 lg:py-28"
    >
      <div className={publicContainerClass}>
        <PublicSectionHeading
          action={
            <Link className={publicButtonClass("secondary")} href="/doctors">
              Xem tất cả bác sĩ
              <ArrowRight aria-hidden />
            </Link>
          }
          description="Bác sĩ chuyên sâu từng vùng: khớp gối, cột sống, chấn thương thể thao và thay khớp."
          id="featured-doctors-title"
          title="Đội ngũ bác sĩ"
        />
        <ul className="mt-12 grid gap-x-8 gap-y-12 sm:grid-cols-2 lg:grid-cols-4">
          {doctors.map((doctor) => (
            <li key={doctor.id}>
              <DoctorCard doctor={doctor} />
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
