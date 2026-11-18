"use client";

import Link from "next/link";
import { publicButtonClass } from "@/components/brand/publicButtonStyles";
import { OPEN_TIME_WINDOW_DAYS } from "../api/publicDoctorOpenTimesApi";
import { usePublicDoctorOpenTimes } from "../hooks/usePublicDoctorOpenTimes";
import type { PublicDoctor } from "../types/publicDoctor";
import { openDaysFromToday } from "../utils/doctorOpenDays";
import { DoctorCard } from "./DoctorCard";
import { DoctorNextOpenTimes } from "./DoctorNextOpenTimes";
import {
  DoctorOpenTimesFailed,
  DoctorOpenTimesLoading,
} from "./DoctorOpenTimesStatus";

// Danh bạ bác sĩ công khai: lưới thẻ 1 cột trên điện thoại, 3 cột từ lg. Chưa có bác sĩ
// nào được công khai thì câu trống nói rõ và gợi ý xem dịch vụ.
// Giờ còn trống của mọi bác sĩ được tải một lần cho cả danh bạ rồi gắn vào từng thẻ. Không có
// bác sĩ nào thì không gọi API giờ trống.
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
        <Link
          className={publicButtonClass("secondary") + " mt-6"}
          href="/services"
        >
          Xem dịch vụ và bảng giá
        </Link>
      </div>
    );
  }
  return <DoctorCards doctors={doctors} />;
}

function DoctorCards({ doctors }: { doctors: PublicDoctor[] }) {
  const { openTimes, loading, failed, reload } = usePublicDoctorOpenTimes();
  return (
    <>
      {loading ? (
        <div className="mt-12">
          <DoctorOpenTimesLoading />
        </div>
      ) : null}
      {failed ? (
        <div className="mt-12">
          <DoctorOpenTimesFailed onRetry={reload} />
        </div>
      ) : null}
      <ul className="mt-12 grid gap-x-8 gap-y-12 sm:grid-cols-2 lg:grid-cols-3">
        {doctors.map((doctor) => (
          <li key={doctor.id}>
            <DoctorCard doctor={doctor} />
            {openTimes ? (
              <DoctorNextOpenTimes
                days={openDaysFromToday(
                  openTimes.get(doctor.id) ?? [],
                  OPEN_TIME_WINDOW_DAYS,
                )}
                doctorId={doctor.id}
              />
            ) : null}
          </li>
        ))}
      </ul>
    </>
  );
}
