"use client";

import { usePublicDoctorOpenTimes } from "../hooks/usePublicDoctorOpenTimes";
import { OPEN_TIME_WINDOW_DAYS } from "../api/publicDoctorOpenTimesApi";
import { openDaysFromToday } from "../utils/doctorOpenDays";
import { DoctorOpenTimesByDay } from "./DoctorOpenTimesByDay";
import {
  DoctorOpenTimesFailed,
  DoctorOpenTimesLoading,
} from "./DoctorOpenTimesStatus";

// "Next 7 days" block on the doctor profile page. Free times load in the browser from the public
// booking availability API, so this block is a client component.
export function DoctorOpenTimesSection({ doctorId }: { doctorId: string }) {
  const { openTimes, loading, failed, reload } = usePublicDoctorOpenTimes();
  return (
    <section aria-labelledby="doctor-availability-title" className="mt-10">
      <h2 className="text-2xl font-semibold" id="doctor-availability-title">
        Lịch nhận khám 7 ngày tới
      </h2>
      <p className="mt-2 text-base leading-7 text-muted-foreground">
        Chọn một giờ còn trống để đặt lịch với bác sĩ này.
      </p>
      <div className="mt-4">
        {loading ? <DoctorOpenTimesLoading /> : null}
        {failed ? <DoctorOpenTimesFailed onRetry={reload} /> : null}
        {openTimes ? (
          <DoctorOpenTimesByDay
            days={openDaysFromToday(
              openTimes.get(doctorId) ?? [],
              OPEN_TIME_WINDOW_DAYS,
            )}
            doctorId={doctorId}
          />
        ) : null}
      </div>
    </section>
  );
}
