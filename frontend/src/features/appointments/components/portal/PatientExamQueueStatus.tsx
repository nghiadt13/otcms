import type { ExamQueueItem } from "@/lib/api/examQueueApi";
import { formatAppMessage } from "@/lib/messages/appMessages";

// Số thứ tự và phòng của lượt trong hàng chờ khám (A6), hiện trên chi tiết lịch sau khi check-in. Câu chữ
// theo MSG-88 của R3; phòng chưa gắn thì ghi "—" (plan mục 6, giả định 1).

export function PatientExamQueueStatus({ entry }: { entry: ExamQueueItem }) {
  if (entry.queueNumber === null || entry.examStatus === "CANCELLED")
    return null;
  return (
    <section
      aria-label="Số thứ tự khám"
      className="space-y-1 rounded-lg border p-4"
    >
      <p className="text-lg font-semibold" role="status">
        {formatAppMessage("MSG-88", {
          number: entry.queueNumber,
          room: entry.roomLabel ?? "—",
        })}
      </p>
      <p className="text-base text-muted-foreground">
        Bác sĩ phụ trách: {entry.doctorName}
      </p>
    </section>
  );
}
