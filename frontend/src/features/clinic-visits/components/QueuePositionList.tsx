import { StatusBadge } from "@/components/admin/StatusBadge";
import {
  QUEUE_STATUS_LABELS,
  QUEUE_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import type { VisitQueuePosition } from "../types/visitProgress";
import { peopleAheadText } from "./visitPresentation";

// Hàng đợi bệnh nhân đang chờ, mỗi phòng một dòng. Số thứ tự là nội dung chính của dòng, có nhãn chữ
// "Số thứ tự" và dùng chữ số đều nhau (tabular-nums). Đang gọi thì dòng ghi "Đang gọi bạn".

export function QueuePositionList({
  queues,
}: {
  queues: VisitQueuePosition[];
}) {
  return (
    <ul className="divide-y rounded-lg border bg-card">
      {queues.map((entry) => {
        const text = peopleAheadText(entry);
        return (
          <li
            className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between"
            key={`${entry.roomName}-${entry.queueNumber}`}
          >
            <div className="min-w-0 space-y-2 text-base">
              <p className="font-medium break-words">{entry.roomName}</p>
              {text ? <p className="break-words">{text}</p> : null}
              <StatusBadge tone={QUEUE_STATUS_TONE[entry.status]}>
                {QUEUE_STATUS_LABELS[entry.status]}
              </StatusBadge>
            </div>
            <div className="shrink-0 sm:text-right">
              <p className="text-sm text-muted-foreground">Số thứ tự</p>
              <p className="text-4xl font-semibold tabular-nums">
                {entry.queueNumber}
              </p>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
