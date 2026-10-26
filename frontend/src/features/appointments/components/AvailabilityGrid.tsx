import { StatusBadge } from "@/components/admin/StatusBadge";
import { TableFrame } from "@/components/admin/TableFrame";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { weekdayLabel } from "../schema/appointmentDates";
import type { DoctorAvailability } from "../types/appointment";

/** Lưới chỉ đọc: hàng = giờ bắt đầu, cột = ngày. Ô ngoài ca ghi "Không có ca". */
export function AvailabilityGrid({ data }: { data: DoctorAvailability }) {
  const times = [
    ...new Set(
      data.days.flatMap((day) =>
        day.slots.map((slot) => slot.startAt.slice(11, 16)),
      ),
    ),
  ].sort();
  const lookup = new Map(
    data.days.flatMap((day) =>
      day.slots.map(
        (slot) =>
          [`${day.date}|${slot.startAt.slice(11, 16)}`, slot.status] as const,
      ),
    ),
  );

  return (
    <TableFrame>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="sticky left-0 z-10 bg-card">Giờ</TableHead>
            {data.days.map((day) => (
              <TableHead key={day.date} className="whitespace-nowrap">
                {weekdayLabel(day.date)}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {times.map((time) => (
            <TableRow key={time}>
              <TableCell className="sticky left-0 z-10 bg-card font-medium tabular-nums">
                {time}
              </TableCell>
              {data.days.map((day) => {
                const status = lookup.get(`${day.date}|${time}`);
                return (
                  <TableCell key={day.date}>
                    {status === "FREE" ? (
                      <StatusBadge tone="success">Trống</StatusBadge>
                    ) : status === "BOOKED" ? (
                      <StatusBadge tone="neutral">Đã đặt</StatusBadge>
                    ) : (
                      <span className="text-muted-foreground">Không có ca</span>
                    )}
                  </TableCell>
                );
              })}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableFrame>
  );
}
