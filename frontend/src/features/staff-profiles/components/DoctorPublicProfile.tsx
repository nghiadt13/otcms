import { CalendarPlus } from "lucide-react";
import Image from "next/image";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import type { PublicDoctor } from "../types/publicDoctor";
import { DoctorOpenTimesSection } from "./DoctorOpenTimesSection";

// Hồ sơ bác sĩ công khai: ảnh, chuyên khoa, bằng cấp, tiểu sử,
// lịch nhận khám 7 ngày với giờ còn trống và nút đặt lịch.
// Each free time opens booking with this doctor preselected; the generic booking button does not.
export function DoctorPublicProfile({ doctor }: { doctor: PublicDoctor }) {
  const initial = doctor.fullName.trim().slice(-1).toUpperCase();
  return (
    <article className="grid gap-12 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)] lg:gap-16">
      <div className="relative aspect-[4/5] w-full max-w-sm overflow-hidden rounded-2xl bg-secondary">
        {doctor.photoUrl ? (
          <Image
            alt={`Chân dung ${doctor.fullName}`}
            className="object-cover"
            fill
            priority
            sizes="(min-width: 1024px) 30vw, 90vw"
            src={doctor.photoUrl}
            unoptimized
          />
        ) : (
          <span
            aria-hidden
            className="absolute inset-0 flex items-center justify-center text-7xl font-semibold text-secondary-foreground"
          >
            {initial}
          </span>
        )}
      </div>

      <div className="min-w-0">
        <h1 className="text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
          {doctor.fullName}
        </h1>

        <dl className="mt-6 grid gap-5 rounded-2xl border bg-card p-6 sm:grid-cols-2">
          <div>
            <dt className="text-sm font-semibold text-muted-foreground">Chuyên khoa</dt>
            <dd className="mt-2 leading-7">
              {doctor.specialties.join(" · ") || "Chưa cập nhật"}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-semibold text-muted-foreground">
              Bằng cấp, trình độ
            </dt>
            <dd className="mt-2 leading-7 whitespace-pre-line">
              {doctor.qualifications.trim() || "Chưa cập nhật"}
            </dd>
          </div>
        </dl>

        <section aria-labelledby="doctor-bio-title" className="mt-8">
          <h2 className="text-2xl font-semibold" id="doctor-bio-title">
            Tiểu sử, giới thiệu
          </h2>
          <p className="mt-3 leading-8 whitespace-pre-line">
            {doctor.biography.trim() || "Chưa cập nhật"}
          </p>
        </section>

        <DoctorOpenTimesSection doctorId={doctor.id} />

        <PublicBookAppointmentLink
          className={publicButtonClass("primary", "lg") + " mt-10"}
        >
          <CalendarPlus aria-hidden />
          Đặt lịch khám
        </PublicBookAppointmentLink>
      </div>
    </article>
  );
}
