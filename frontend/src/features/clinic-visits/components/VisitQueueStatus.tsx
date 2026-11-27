import { DoorOpen } from "lucide-react";
import { cn } from "@/lib/utils";
import type { QueueTicket } from "../types/visitProgress";
import { queueAheadText } from "./visitPresentation";

// Vị trí của bệnh nhân trong một hàng, dạng "phiếu số": ô số thứ tự bên trái (nội dung chính, có nhãn chữ,
// chữ số đều nhau), bên phải là câu còn bao nhiêu người trước, phòng và lời nhắc. Khi đang được gọi, vùng
// này đổi sang nền xanh thành công và nhắc vào phòng ngay. Dùng cho thẻ tóm tắt ở trang chủ (trang chi tiết
// dùng VisitNextAction và VisitTimeline).

const WAIT_HINT = {
  EXAM: "Bạn đang xếp hàng chờ bác sĩ khám.",
  SERVICE: "Bạn đang xếp hàng chờ làm chỉ định tại phòng này.",
  NONE: "Bạn đang xếp hàng chờ.",
} as const;

export function VisitQueueStatus({ ticket }: { ticket: QueueTicket }) {
  const text = queueAheadText(ticket);
  const called = ticket.beingCalled;
  const room = ticket.room ?? "Chưa có phòng";
  return (
    <div
      className={cn(
        "flex items-stretch gap-4 rounded-xl p-4 sm:gap-5 sm:p-5",
        called ? "bg-success-soft" : "bg-secondary",
      )}
    >
      <div className="flex w-24 shrink-0 flex-col items-center justify-center rounded-lg bg-card px-3 py-4 sm:w-28">
        <p className="text-sm text-muted-foreground">Số thứ tự</p>
        <p className="text-5xl font-semibold tabular-nums">
          {ticket.queueNumber}
        </p>
      </div>
      <div className="flex min-w-0 flex-1 flex-col justify-center gap-1.5">
        {text ? (
          <p
            aria-live="polite"
            className={cn(
              "text-xl font-semibold break-words",
              called && "text-success",
            )}
          >
            {text}
          </p>
        ) : null}
        <p className="flex items-center gap-2 text-base font-medium break-words">
          <DoorOpen
            aria-hidden
            className="size-5 shrink-0 text-muted-foreground"
          />
          <span>{room}</span>
        </p>
        <p className="text-sm text-muted-foreground">
          {called
            ? "Vui lòng vào phòng ngay để không lỡ lượt."
            : `${WAIT_HINT[ticket.stage ?? "NONE"]} Hãy ở gần khu vực chờ để không lỡ lượt.`}
        </p>
      </div>
    </div>
  );
}
