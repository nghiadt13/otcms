import Image from "next/image";
import type { FeaturedDoctor } from "../types/staffProfile";

// Thẻ một bác sĩ: ảnh chân dung 4:5, trình độ, tên, chuyên khoa. Không bọc Card:
// ảnh là bề mặt chính, chữ nằm thẳng trên nền trang. Chưa có ảnh thì hiện chữ cái đầu.
export function DoctorCard({ doctor }: { doctor: FeaturedDoctor }) {
  const initial = doctor.displayName.trim().slice(-1).toUpperCase();
  return (
    <article className="group">
      <div className="relative aspect-[4/5] overflow-hidden rounded-2xl bg-secondary">
        {doctor.photoUrl ? (
          <Image
            alt={`Chân dung ${doctor.displayName}`}
            className="object-cover transition-transform duration-300 ease-out group-hover:scale-[1.03] motion-reduce:transition-none"
            fill
            sizes="(min-width: 1024px) 22vw, (min-width: 640px) 45vw, 90vw"
            src={doctor.photoUrl}
          />
        ) : (
          <span
            aria-hidden
            className="absolute inset-0 flex items-center justify-center text-6xl font-semibold text-secondary-foreground"
          >
            {initial}
          </span>
        )}
      </div>
      {doctor.qualifications ? (
        <p className="mt-5 text-sm font-medium text-primary">
          {doctor.qualifications}
        </p>
      ) : null}
      <h3 className="mt-1 text-xl font-semibold tracking-tight">
        {doctor.displayName}
      </h3>
      {doctor.specialties.length > 0 ? (
        <p className="mt-2 text-base leading-7 text-muted-foreground">
          {doctor.specialties.join(" · ")}
        </p>
      ) : null}
    </article>
  );
}
