"use client";

import { StatusBadge } from "@/components/admin/StatusBadge";
import type { ExamQueueItem } from "@/lib/api/examQueueApi";
import {
  EXAM_QUEUE_STATUS_LABELS,
  EXAM_QUEUE_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { clinicLocal, DataTable } from "./ReceptionUi";

/** Hàng chờ của một bác sĩ: tên, phòng của ca, số đang chờ, rồi bảng lượt theo số thứ tự. */
export function QueueDoctorGroup({
  doctorName,
  roomLabel,
  items,
}: {
  doctorName: string;
  roomLabel: string | null;
  items: ExamQueueItem[];
}) {
  const waiting = items.filter((item) => item.examStatus === "WAITING").length;
  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-semibold">{doctorName}</h2>
        <p className="text-sm text-muted-foreground">
          {roomLabel ?? "Chưa có phòng"} · {waiting} đang chờ
        </p>
      </div>
      <DataTable
        label={`Hàng chờ của ${doctorName}`}
        headers={["Số", "Bệnh nhân", "Nguồn", "Trạng thái", "Vào hàng chờ"]}
        empty="Chưa có lượt nào."
        rows={items.map((item) => ({
          id: item.visitId,
          cells: [
            <span key="number" className="font-medium tabular-nums">
              {item.queueNumber ?? "—"}
            </span>,
            item.patientName,
            item.source === "SCHEDULED" ? "Lịch hẹn" : "Trực tiếp",
            <StatusBadge
              key="status"
              tone={EXAM_QUEUE_STATUS_TONE[item.examStatus]}
            >
              {EXAM_QUEUE_STATUS_LABELS[item.examStatus]}
            </StatusBadge>,
            item.queuedAt ? clinicLocal(item.queuedAt).slice(11, 16) : "—",
          ],
        }))}
      />
    </section>
  );
}
