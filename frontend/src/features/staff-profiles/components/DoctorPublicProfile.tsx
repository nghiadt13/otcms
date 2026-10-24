import { CalendarPlus } from "lucide-react";
import Image from "next/image";
import { PublicBookAppointmentLink } from "@/components/brand/PublicBookAppointmentLink";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import type { PublicDoctorDetail } from "../types/publicDoctor";
import { DoctorAvailabilitySummary } from "./DoctorAvailabilitySummary";

// Hồ sơ bác sĩ công khai (UC16 bước 3–4): ảnh và chuyên môn, giới thiệu, thông tin khám,
// tóm tắt giờ nhận 7 ngày và nút đặt lịch. Đặt lịch bắt đầu từ thời gian, không chọn sẵn bác sĩ.
export function DoctorPublicProfile({ doctor }: { doctor: PublicDoctorDetail }) {
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
        {doctor.qualifications ? (
          <p className="text-lg font-medium text-primary">{doctor.qualifications}</p>
        ) : null}
        <h1 className="mt-2 text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
          {doctor.fullName}
        </h1>
        {doctor.specialties.length > 0 ? (
          <p className="mt-4 text-lg text-muted-foreground">
            {doctor.specialties.join(" · ")}
          </p>
        ) : null}

        <section aria-labelledby="doctor-bio-title" className="mt-10">
          <h2 className="text-2xl font-semibold" id="doctor-bio-title">
            Giới thiệu
          </h2>
          <p className="mt-3 text-lg leading-8 text-pretty whitespace-pre-line">
            {doctor.biography}
          </p>
        </section>

        <section aria-labelledby="doctor-consult-title" className="mt-10">
          <h2 className="text-2xl font-semibold" id="doctor-consult-title">
            Thông tin khám
          </h2>
          <p className="mt-3 text-lg leading-8 text-pretty whitespace-pre-line">
            {doctor.consultationInfo}
          </p>
        </section>

        <section aria-labelledby="doctor-availability-title" className="mt-10">
          <h2 className="text-2xl font-semibold" id="doctor-availability-title">
            Lịch nhận khám 7 ngày tới
          </h2>
          <div className="mt-4">
            <DoctorAvailabilitySummary availability={doctor.availability} />
          </div>
        </section>

        <PublicBookAppointmentLink className={publicButtonClass("primary", "lg") + " mt-10"}>
          <CalendarPlus aria-hidden />
          Đặt lịch khám
        </PublicBookAppointmentLink>
      </div>
    </article>
  );
}
