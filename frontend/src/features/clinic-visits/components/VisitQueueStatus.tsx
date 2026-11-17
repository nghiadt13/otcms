import type { VisitProgress } from "../types/visitProgress";
import { queueAheadText } from "./visitPresentation";

// Vị trí của bệnh nhân trong hàng đang chờ: phòng, câu nói còn bao nhiêu người trước và số thứ tự.
// Số thứ tự là nội dung chính, có nhãn chữ và dùng chữ số đều nhau. Chỉ gọi khi lượt đã vào hàng.

export function VisitQueueStatus({ progress }: { progress: VisitProgress }) {
  const text = queueAheadText(progress);
  return (
    <div className="flex flex-col gap-3 rounded-lg border bg-card px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0 space-y-2 text-base">
        <p className="font-medium break-words">
          {progress.currentRoom ?? "Chưa có phòng"}
        </p>
        {text ? <p className="break-words">{text}</p> : null}
      </div>
      <div className="shrink-0 sm:text-right">
        <p className="text-sm text-muted-foreground">Số thứ tự</p>
        <p className="text-4xl font-semibold tabular-nums">
          {progress.queueNumber}
        </p>
      </div>
    </div>
  );
}
