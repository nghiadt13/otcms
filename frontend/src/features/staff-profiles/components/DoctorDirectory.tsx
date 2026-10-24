import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import type { PublicDoctor } from "../types/publicDoctor";
import { DoctorCard } from "./DoctorCard";

// Danh bạ bác sĩ công khai: lưới thẻ 1 cột trên điện thoại, 3 cột từ lg. Chưa có bác sĩ
// nào được công khai thì câu trống nói rõ và gợi ý xem dịch vụ.
export function DoctorDirectory({ doctors }: { doctors: PublicDoctor[] }) {
  if (doctors.length === 0) {
    return (
      <div className="mt-12 max-w-2xl rounded-2xl border bg-card p-8">
        <p className="text-lg font-semibold">
          Chưa có bác sĩ nào được công khai hồ sơ.
        </p>
        <p className="mt-2 text-base leading-7 text-muted-foreground">
          Trong lúc chờ, bạn có thể xem dịch vụ và bảng giá của phòng khám.
        </p>
        <Link className={publicButtonClass("secondary") + " mt-6"} href="/services">
          Xem dịch vụ và bảng giá
        </Link>
      </div>
    );
  }

  return (
    <ul className="mt-12 grid gap-x-8 gap-y-12 sm:grid-cols-2 lg:grid-cols-3">
      {doctors.map((doctor) => (
        <li key={doctor.id}>
          <DoctorCard doctor={doctor} />
        </li>
      ))}
    </ul>
  );
}
