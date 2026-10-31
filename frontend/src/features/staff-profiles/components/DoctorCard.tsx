import Image from "next/image";
import Link from "next/link";
import type { PublicDoctor } from "../types/publicDoctor";

// Thẻ một bác sĩ: ảnh chân dung 4:5, trình độ, tên, chuyên khoa; cả thẻ là liên kết tới hồ
// sơ. Không bọc Card: ảnh là bề mặt chính, chữ nằm thẳng trên nền trang. Chưa có ảnh thì
// hiện chữ cái cuối của tên. `headingLevel` giữ thứ tự tiêu đề: h2 trong danh bạ (dưới h1),
// h3 trong khối trang chủ (dưới h2 của khối).
export function DoctorCard({
  doctor,
  headingLevel = 2,
}: {
  doctor: PublicDoctor;
  headingLevel?: 2 | 3;
}) {
  const initial = doctor.fullName.trim().slice(-1).toUpperCase();
  const Heading = headingLevel === 3 ? "h3" : "h2";
  return (
    <Link
      className="group block rounded-2xl outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
      href={`/doctors/${encodeURIComponent(doctor.id)}`}
    >
      <article>
        <div className="relative aspect-[4/5] overflow-hidden rounded-2xl bg-secondary">
          {doctor.photoUrl ? (
            <Image
              alt={`Chân dung ${doctor.fullName}`}
              className="object-cover transition-transform duration-300 ease-out group-hover:scale-[1.03] motion-reduce:transition-none"
              fill
              sizes="(min-width: 1024px) 30vw, (min-width: 640px) 45vw, 90vw"
              src={doctor.photoUrl}
              unoptimized
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
          <p className="mt-5 text-base font-medium text-primary">
            {doctor.qualifications}
          </p>
        ) : null}
        <Heading className="mt-1 text-xl font-semibold tracking-tight group-hover:text-primary">
          {doctor.fullName}
        </Heading>
        {doctor.specialties.length > 0 ? (
          <p className="mt-2 text-base leading-7 text-muted-foreground">
            {doctor.specialties.join(" · ")}
          </p>
        ) : null}
      </article>
    </Link>
  );
}
