"use client";

import type { ReactNode } from "react";
import { StatusBadge, type StatusTone } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import type { ExamQueueItem } from "@/lib/api/examQueueApi";
import {
  EXAM_QUEUE_STATUS_LABELS,
  EXAM_QUEUE_STATUS_TONE,
  type ExamQueueStatus,
} from "@/lib/domain/clinicVisit";
import type { RoomQueueEntry } from "../api/receptionLiveApi";
import { splitRoomEntries } from "../schema/roomQueueModel";
import { DataTable } from "./ReceptionUi";

// Hàng của một phòng khám hôm nay: đang phục vụ, đang chờ, vắng, chờ kết luận. Nút theo trạng thái:
// Gọi tiếp (khi phòng trống), Vắng (người đang được gọi), Đưa lại (người vắng). Người đang có mặt
// không có nút đổi vị trí (UC-33 chỉ áp cho người vắng).

const SOURCE_LABELS: Record<ExamQueueItem["source"], string> = {
  SCHEDULED: "Lịch hẹn",
  WALK_IN: "Trực tiếp",
};

function sourceLabel(source: ExamQueueItem["source"] | undefined) {
  return source ? SOURCE_LABELS[source] : "—";
}

function statusView(status: string): { label: string; tone: StatusTone } {
  if (status in EXAM_QUEUE_STATUS_LABELS) {
    const key = status as ExamQueueStatus;
    return { label: EXAM_QUEUE_STATUS_LABELS[key], tone: EXAM_QUEUE_STATUS_TONE[key] };
  }
  return { label: status, tone: "neutral" };
}

function Section({
  title,
  count,
  children,
}: {
  title: string;
  count: number;
  children: ReactNode;
}) {
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">
        {title} <span className="text-sm font-normal text-muted-foreground tabular-nums">({count})</span>
      </h2>
      {children}
    </section>
  );
}

export function QueueRoomPanel({
  roomLabel,
  entries,
  itemOf,
  busy,
  onCallNext,
  onAbsent,
  onReturn,
  onTransferDoctor,
}: {
  roomLabel: string;
  entries: RoomQueueEntry[];
  itemOf: (visitId: string) => ExamQueueItem | undefined;
  busy: boolean;
  onCallNext: () => void;
  onAbsent: (entry: RoomQueueEntry) => void;
  onReturn: (entry: RoomQueueEntry) => void;
  /** Đổi bác sĩ phụ trách cho người đang chờ (UC-30). Không truyền thì không hiện nút. */
  onTransferDoctor?: (entry: RoomQueueEntry) => void;
}) {
  const groups = splitRoomEntries(entries);
  const nameOf = (entry: RoomQueueEntry) => itemOf(entry.visitId)?.patientName ?? "—";
  const canCallNext =
    !busy && groups.serving.length === 0 && groups.waiting.length > 0;

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          {roomLabel} · đang phục vụ {groups.serving.length} · đang chờ {groups.waiting.length} · vắng {groups.absent.length}
        </p>
        <Button disabled={!canCallNext} onClick={onCallNext}>
          Gọi tiếp
        </Button>
      </div>

      <Section title="Đang phục vụ" count={groups.serving.length}>
        <DataTable
          label={`Đang phục vụ ở ${roomLabel}`}
          headers={["Số", "Bệnh nhân", "Trạng thái", "Thao tác"]}
          empty="Chưa có ai được gọi hoặc đang khám."
          rows={groups.serving.map((entry) => {
            const view = statusView(entry.status);
            return {
              id: entry.id,
              cells: [
                <span key="no" className="font-medium tabular-nums">
                  {entry.queueNumber ?? "—"}
                </span>,
                nameOf(entry),
                <StatusBadge key="st" tone={view.tone}>{view.label}</StatusBadge>,
                entry.status === "CALLED" ? (
                  <Button
                    key="absent"
                    size="sm"
                    variant="outline"
                    disabled={busy}
                    onClick={() => onAbsent(entry)}
                  >
                    Vắng
                  </Button>
                ) : (
                  "—"
                ),
              ],
            };
          })}
        />
      </Section>

      <Section title="Đang chờ" count={groups.waiting.length}>
        <DataTable
          label={`Đang chờ ở ${roomLabel}`}
          headers={
            onTransferDoctor
              ? ["Thứ tự", "Số", "Bệnh nhân", "Nguồn", "Thao tác"]
              : ["Thứ tự", "Số", "Bệnh nhân", "Nguồn"]
          }
          empty="Không có bệnh nhân đang chờ."
          rows={groups.waiting.map((entry, index) => ({
            id: entry.id,
            cells: [
              index + 1,
              <span key="no" className="tabular-nums">
                {entry.queueNumber ?? "—"}
              </span>,
              nameOf(entry),
              sourceLabel(itemOf(entry.visitId)?.source),
              ...(onTransferDoctor
                ? [
                    <Button
                      key="transfer"
                      size="sm"
                      variant="outline"
                      disabled={busy}
                      onClick={() => onTransferDoctor(entry)}
                    >
                      Đổi bác sĩ
                    </Button>,
                  ]
                : []),
            ],
          }))}
        />
      </Section>

      <Section title="Vắng mặt" count={groups.absent.length}>
        <DataTable
          label={`Vắng mặt ở ${roomLabel}`}
          headers={["Số", "Bệnh nhân", "Thao tác"]}
          empty="Không có bệnh nhân vắng."
          rows={groups.absent.map((entry) => ({
            id: entry.id,
            cells: [
              <span key="no" className="font-medium tabular-nums">
                {entry.queueNumber ?? "—"}
              </span>,
              nameOf(entry),
              <Button
                key="return"
                size="sm"
                variant="outline"
                disabled={busy}
                onClick={() => onReturn(entry)}
              >
                Đưa lại
              </Button>,
            ],
          }))}
        />
      </Section>

      <Section title="Chờ kết luận" count={groups.awaiting.length}>
        <DataTable
          label={`Chờ kết luận ở ${roomLabel}`}
          headers={["Số", "Bệnh nhân", "Trạng thái"]}
          empty="Không có lượt chờ kết luận."
          rows={groups.awaiting.map((entry) => {
            const view = statusView(entry.status);
            return {
              id: entry.id,
              cells: [
                entry.queueNumber ?? "—",
                nameOf(entry),
                <StatusBadge key="st" tone={view.tone}>{view.label}</StatusBadge>,
              ],
            };
          })}
        />
      </Section>
    </div>
  );
}
