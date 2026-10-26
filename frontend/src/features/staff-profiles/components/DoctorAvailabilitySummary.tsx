import { StatusBadge } from "@/components/admin/StatusBadge";
import type { DoctorAvailabilityDay } from "../types/publicDoctor";
import { dayMonthVi, weekdayShortVi } from "../utils/availabilityDayLabel";

// Tóm tắt giờ nhận khám 7 ngày tới (UC16 bước 4, BR-20): mỗi ngày chỉ ghi "Còn giờ" hoặc
// "Hết giờ" bằng chữ, không liệt kê từng giờ ở trang công khai.
export function DoctorAvailabilitySummary({
  availability,
}: {
  availability: DoctorAvailabilityDay[];
}) {
  return (
    <div>
      <ul className="divide-y divide-border overflow-hidden rounded-2xl border bg-card">
        {availability.map((day) => (
          <li
            className="flex items-center justify-between gap-4 px-5 py-4"
            key={day.date}
          >
            <span className="text-lg font-medium tabular-nums">
              {weekdayShortVi(day.date)} {dayMonthVi(day.date)}
            </span>
            <StatusBadge
              className="h-7 px-3 text-sm"
              tone={day.hasOpenTime ? "success" : "neutral"}
            >
              {day.hasOpenTime ? "Còn giờ" : "Hết giờ"}
            </StatusBadge>
          </li>
        ))}
      </ul>
      <p className="mt-3 text-base leading-7 text-muted-foreground">
        Giờ cụ thể hiện khi bạn chọn bác sĩ và giờ trên màn đặt lịch.
      </p>
    </div>
  );
}
