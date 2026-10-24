import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";
import { STAFF_ROUTES } from "@/lib/routes/staffRoutes";
import { ineligibleMessage } from "../schema/checkInPresentation";
import type { IneligibleAppointment } from "../types/checkIn";

interface IneligibleNoticesProps {
  items: IneligibleAppointment[];
  notice: "PREVIOUS_VISIT_INCOMPLETE" | null;
  today: string;
}

// Không đủ điều kiện hiện ngay trong panel, không toast: nói lý do và việc tiếp theo.
export function IneligibleNotices({ items, notice, today }: IneligibleNoticesProps) {
  if (items.length === 0 && notice === null) return null;
  return (
    <div className="space-y-2">
      {notice === "PREVIOUS_VISIT_INCOMPLETE" ? (
        <p className="rounded-md border bg-muted p-3 text-sm">
          Lượt khám trước chưa hoàn tất và đã quá 7 ngày, nên lần này là lượt khám mới và cần thu
          phí khám.
        </p>
      ) : null}
      {items.map((item) => (
        <div
          key={item.appointmentId}
          className="flex flex-wrap items-center justify-between gap-2 rounded-md border bg-muted p-3 text-sm"
        >
          <p>{ineligibleMessage(item, today)}</p>
          {item.reason === "ALREADY_CHECKED_IN" ? (
            <Link
              href={STAFF_ROUTES.receptionQueue}
              className={buttonVariants({ variant: "link", size: "sm" })}
            >
              Xem hàng đợi tiếp nhận
            </Link>
          ) : null}
        </div>
      ))}
    </div>
  );
}
