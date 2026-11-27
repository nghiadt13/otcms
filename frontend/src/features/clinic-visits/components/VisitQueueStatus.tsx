import { DoorOpen } from "lucide-react";
import { cn } from "@/lib/utils";
import type { VisitProgress } from "../types/visitProgress";
import { queueAheadText } from "./visitPresentation";

// Vị trí của bệnh nhân trong hàng đang chờ, dạng "phiếu số": ô số thứ tự bên trái (nội dung chính, có nhãn
// chữ, chữ số đều nhau), bên phải là phòng, câu nói còn bao nhiêu người trước và lời nhắc việc cần làm.
// Khi đang được gọi, vùng này đổi sang nền xanh thành công và nhắc vào phòng ngay. Chỉ gọi khi lượt đã vào hàng.

export function VisitQueueStatus({ progress }: { progress: VisitProgress }) {
  const text = queueAheadText(progress);
  const called = progress.beingCalled;
  const room = progress.currentRoom ?? "Chưa có phòng";
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
          {progress.queueNumber}
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
            : "Hãy ở gần khu vực chờ để không lỡ lượt. Số người trước bạn tự cập nhật."}
        </p>
      </div>
    </div>
  );
}
