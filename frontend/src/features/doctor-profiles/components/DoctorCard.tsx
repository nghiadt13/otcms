import Image from "next/image";
import type { DoctorProfile } from "../types/doctorProfile";

// Thẻ một bác sĩ: ảnh chân dung 4:5 + học vị, tên, chuyên khoa, thế mạnh, số năm
// kinh nghiệm. Dùng lại cho trang /doctors. Không bọc Card: ảnh là bề mặt chính,
// chữ nằm thẳng trên nền trang để lưới nhìn như ảnh hồ sơ, không như ô dữ liệu.
export function DoctorCard({ doctor }: { doctor: DoctorProfile }) {
  return (
    <article className="group">
      <div className="relative aspect-[4/5] overflow-hidden rounded-2xl bg-secondary">
        <Image
          alt={doctor.photo.alt}
          className="object-cover transition-transform duration-300 ease-out group-hover:scale-[1.03] motion-reduce:transition-none"
          fill
          sizes="(min-width: 1024px) 22vw, (min-width: 640px) 45vw, 90vw"
          src={doctor.photo.src}
        />
        <span className="absolute bottom-4 left-4 rounded-full bg-card/95 px-3 py-1 text-sm font-semibold text-foreground tabular-nums shadow-sm">
          {doctor.yearsOfExperience} năm kinh nghiệm
        </span>
      </div>
      <p className="mt-5 text-sm font-medium text-primary">{doctor.degree}</p>
      <h3 className="mt-1 text-xl font-semibold tracking-tight">
        {doctor.fullName}
      </h3>
      <p className="mt-2 text-base font-medium text-foreground/90">
        {doctor.specialty}
      </p>
      <p className="mt-1 text-base leading-7 text-muted-foreground">
        {doctor.focus}
      </p>
    </article>
  );
}
