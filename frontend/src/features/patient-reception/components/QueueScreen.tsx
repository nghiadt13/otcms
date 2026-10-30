"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { Megaphone } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { PRIORITIES, type Priority, type QueueEntry } from "../types";
import {
  queueSort,
  agePriority,
  receptionPriority,
} from "../schema/receptionRules";
import { useReception } from "./ReceptionProvider";
import {
  DataTable,
  FormActions,
  FormModal,
  PatientName,
  RouteLink,
  SelectField,
  Status,
  TextField,
  timeText,
} from "./ReceptionUi";

function QueueAdjustment({
  entry,
  onClose,
}: {
  entry: QueueEntry;
  onClose: () => void;
}) {
  const { data: s, run, pending } = useReception();
  const [mode, setMode] = useState("order");
  const [reason, setReason] = useState("");
  const [position, setPosition] = useState(String(entry.position));
  const [selectedPriority, setPriority] = useState<Priority>(entry.priority);
  const patient = s!.patients.find(
    (p) => p.id === s!.visits.find((v) => v.id === entry.visitId)?.patientId,
  );
  const automaticPriority = agePriority(patient?.dob ?? "", entry.date);
  const priority = receptionPriority(
    patient?.dob ?? "",
    entry.date,
    selectedPriority,
  );
  const [roomId, setRoom] = useState("");
  const maxPosition = s!.queues.filter(
    (q) =>
      q.roomId === entry.roomId &&
      q.kind === entry.kind &&
      q.date === entry.date &&
      q.status === "waiting",
  ).length;
  const validPosition =
    Number.isInteger(Number(position)) &&
    Number(position) >= 1 &&
    Number(position) <= maxPosition;
  const positionError = !validPosition
    ? `Nhập vị trí nguyên từ 1 đến ${maxPosition}.`
    : undefined;
  const reasonError =
    reason.length > 0 && reason.trim().length < 5
      ? "Lý do cần ít nhất 5 ký tự. Hãy mô tả căn cứ điều chỉnh."
      : undefined;
  const canSave =
    reason.trim().length >= 5 && (mode === "order" ? validPosition : !!roomId);
  const orders = s!.orders.filter(
    (o) =>
      o.visitId === entry.visitId &&
      o.roomId === entry.roomId &&
      o.status === "paid",
  );
  const rooms = s!.rooms.filter(
    (r) =>
      r.id !== entry.roomId &&
      r.available &&
      orders.length > 0 &&
      orders.every((o) => r.serviceIds.includes(o.serviceId)),
  );
  return (
    <FormModal
      title={`Điều chỉnh ${entry.number}`}
      description="Chỉ điều chỉnh bệnh nhân đang chờ. Mọi thay đổi phải có lý do và lưu lại lịch sử."
      onClose={onClose}
    >
      <form
        className="space-y-4"
        onSubmit={async (e) => {
          e.preventDefault();
          if (!canSave || pending) return;
          const command =
            mode === "order"
              ? {
                  type: "reorder" as const,
                  id: entry.id,
                  position: Number(position),
                  priority,
                  reason,
                }
              : { type: "move-room" as const, id: entry.id, roomId, reason };
          if (await run(command)) onClose();
        }}
      >
        <SelectField
          label="Thao tác"
          value={mode}
          onChange={setMode}
          disabled={pending}
          options={[
            { value: "order", label: "Đổi vị trí hoặc nhóm ưu tiên" },
            ...(entry.kind === "service"
              ? [{ value: "room", label: "Chuyển phòng dịch vụ" }]
              : []),
          ]}
        />
        {mode === "order" ? (
          <>
            <TextField
              label="Vị trí mới"
              type="number"
              value={position}
              onChange={setPosition}
              min="1"
              error={positionError}
              hint={`Có ${maxPosition} người đang chờ; vị trí hợp lệ từ 1 đến ${maxPosition}.`}
              max={String(
                s!.queues.filter(
                  (q) =>
                    q.roomId === entry.roomId &&
                    q.kind === entry.kind &&
                    q.date === entry.date &&
                    q.status === "waiting",
                ).length,
              )}
              disabled={pending}
            />
            <SelectField
              label="Nhóm ưu tiên"
              value={priority}
              onChange={(v) => setPriority(v as Priority)}
              disabled={pending}
              options={Object.entries(PRIORITIES)
                .filter(
                  ([value]) => value !== "pregnant" || patient?.sex === "Nữ",
                )
                .filter(
                  ([value]) =>
                    !["normal", "child", "elderly"].includes(value) ||
                    value === automaticPriority,
                )
                .map(([value, label]) => ({
                  value,
                  label,
                }))}
            />
          </>
        ) : (
          <SelectField
            label="Phòng phù hợp"
            value={roomId}
            onChange={setRoom}
            disabled={pending}
            options={[
              {
                value: "",
                label: rooms.length
                  ? "Chọn phòng"
                  : "Không có phòng khác phù hợp",
              },
              ...rooms.map((r) => ({ value: r.id, label: r.name })),
            ]}
          />
        )}
        <TextField
          label="Lý do điều chỉnh *"
          value={reason}
          onChange={setReason}
          disabled={pending}
          error={reasonError}
          hint="Tối thiểu 5 ký tự. Ghi căn cứ ưu tiên hoặc lý do đổi vị trí để đối chiếu."
        />
        <FormActions
          onClose={onClose}
          label="Lưu điều chỉnh"
          disabled={!canSave}
        />
      </form>
    </FormModal>
  );
}
export function QueueScreen() {
  const { data: s, pending, run } = useReception();
  const params = useSearchParams();
  const initial =
    s!.rooms.find((r) => r.id === params.get("roomId"))?.id ?? "p101";
  const [roomId, setRoomId] = useState(initial);
  const room = s!.rooms.find((r) => r.id === roomId)!;
  const [kind, setKind] = useState<"exam" | "review">("exam");
  const [adjustment, setAdjustment] = useState<QueueEntry | null>(null);
  const day = s!.clock.slice(0, 10);
  const entries = s!.queues
    .filter(
      (q) =>
        q.roomId === roomId &&
        q.date === day &&
        (room.category !== "exam" || q.kind === kind) &&
        ["waiting", "serving", "absent"].includes(q.status),
    )
    .sort(queueSort);
  const occupied = s!.queues.find(
    (q) =>
      q.roomId === roomId &&
      q.date === day &&
      (q.status === "serving" || (q.status === "waiting" && q.calledAt)),
  );
  const patientOf = (q: QueueEntry) =>
    s!.patients.find(
      (p) => p.id === s!.visits.find((v) => v.id === q.visitId)!.patientId,
    )!;
  return (
    <>
      <PageHeader
        title="Hàng đợi phòng"
        description="Chọn phòng, kiểm tra người đang phục vụ trước khi gọi bệnh nhân tiếp theo."
        actions={
          room.category === "exam" ? (
            <Button
              disabled={
                pending ||
                !!occupied ||
                !room.available ||
                !entries.some((q) => q.status === "waiting")
              }
              onClick={() => void run({ type: "call-next", roomId, kind })}
            >
              <Megaphone />
              Gọi bệnh nhân tiếp theo
            </Button>
          ) : undefined
        }
      />
      <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
        <div className="w-full sm:w-80">
          <SelectField
            label="Phòng"
            value={roomId}
            onChange={setRoomId}
            options={s!.rooms.map((r) => ({ value: r.id, label: r.name }))}
          />
        </div>
        {room.category === "exam" && (
          <div
            className="flex flex-wrap gap-2"
            role="group"
            aria-label="Loại hàng đợi"
          >
            <Button
              variant={kind === "exam" ? "secondary" : "outline"}
              aria-pressed={kind === "exam"}
              onClick={() => setKind("exam")}
            >
              Chờ khám
            </Button>
            <Button
              variant={kind === "review" ? "secondary" : "outline"}
              aria-pressed={kind === "review"}
              onClick={() => setKind("review")}
            >
              Chờ đọc kết quả
            </Button>
          </div>
        )}
      </div>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-4 rounded-lg border bg-card p-4">
        <div>
          <p className="text-xs text-muted-foreground">
            {occupied
              ? occupied.status === "serving"
                ? "Đang phục vụ"
                : "Đang gọi vào phòng"
              : "Tình trạng hiện tại"}
          </p>
          <p className="mt-1 text-lg font-semibold">
            {occupied
              ? `${occupied.number} · ${patientOf(occupied).name}`
              : room.available
                ? "Phòng chưa có bệnh nhân đang phục vụ"
                : "Phòng tạm ngừng tiếp nhận"}
          </p>
          {occupied && (
            <p className="mt-1 text-xs text-muted-foreground">
              {occupied.kind === "review"
                ? "Đọc kết quả"
                : occupied.kind === "exam"
                  ? "Khám ban đầu"
                  : "Dịch vụ cận lâm sàng"}
            </p>
          )}
        </div>
        {occupied?.status === "serving" && (
          <p className="text-sm text-muted-foreground">
            Chờ bác sĩ hoặc bộ phận thực hiện xác nhận kết thúc bước phục vụ. Lễ
            tân không tự xác nhận khám xong; nút gọi tiếp sẽ mở khi phòng được
            giải phóng. Bản mô phỏng hiện chưa có thao tác hoàn tất từ màn bác
            sĩ.
          </p>
        )}
        {occupied?.status === "waiting" &&
          occupied.callerId === s!.account.id &&
          room.category === "exam" && (
            <div className="flex flex-wrap gap-2">
              <Button
                variant="outline"
                disabled={pending}
                onClick={() =>
                  void run({
                    type: "queue-action",
                    id: occupied.id,
                    action: "arrived",
                  })
                }
              >
                Đã vào phòng
              </Button>
              <Button
                variant="outline"
                disabled={pending}
                onClick={() =>
                  void run({
                    type: "queue-action",
                    id: occupied.id,
                    action: "absent",
                  })
                }
              >
                Ghi vắng mặt
              </Button>
            </div>
          )}
      </div>
      {room.category !== "exam" && (
        <p className="mb-4 text-muted-foreground">
          Kỹ thuật viên gọi bệnh nhân tại phòng dịch vụ. Lễ tân có thể điều
          chỉnh vị trí hoặc chuyển sang phòng phù hợp.
        </p>
      )}
      <DataTable
        key={`${roomId}-${kind}`}
        label="Hàng đợi phòng"
        headers={[
          "STT",
          "Bệnh nhân",
          "Ưu tiên",
          "Vào hàng đợi",
          "Trạng thái",
          "Thao tác",
        ]}
        rows={entries.map((q) => {
          const p = patientOf(q);
          return {
            id: q.id,
            cells: [
              <span key="number" className="font-semibold tabular-nums">
                {q.number}
              </span>,
              <PatientName
                key="patient"
                name={p.name}
                code={p.id}
                secondary={q.visitId}
              />,
              PRIORITIES[q.priority],
              q.joinedAt.slice(11, 16),
              <Status
                key="status"
                value={
                  q.status === "waiting" && q.calledAt ? "called" : q.status
                }
              />,
              <div key="actions" className="flex flex-wrap gap-2">
                {q.status === "waiting" && !q.calledAt && (
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={pending}
                    onClick={() => setAdjustment(q)}
                  >
                    Điều chỉnh
                  </Button>
                )}
                {q.status === "absent" &&
                  q.callerId === s!.account.id &&
                  q.kind !== "service" && (
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={pending}
                      onClick={() =>
                        void run({
                          type: "queue-action",
                          id: q.id,
                          action: "return",
                        })
                      }
                    >
                      Đã quay lại
                    </Button>
                  )}
                <RouteLink href={`/reception/visits?visitId=${q.visitId}`}>
                  Tiến trình
                </RouteLink>
              </div>,
            ],
          };
        })}
        empty="Chưa có bệnh nhân trong hàng đợi này."
      />
      <details className="mt-6 rounded-lg border bg-card p-4">
        <summary className="cursor-pointer font-medium">
          Lịch sử điều phối ({s!.audit.length})
        </summary>
        <div className="mt-4 space-y-3">
          {s!.audit.length ? (
            s!.audit.map((log) => (
              <div key={log.id} className="border-b pb-3 last:border-0">
                <p className="font-medium">
                  {log.action} · {log.actor}
                </p>
                <p className="mt-1 text-muted-foreground">
                  {log.before} → {log.after}
                </p>
                <p>{log.reason}</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {timeText(log.at)}
                </p>
              </div>
            ))
          ) : (
            <p className="text-muted-foreground">
              Chưa có thay đổi điều phối trong phiên xem thử.
            </p>
          )}
        </div>
      </details>
      {adjustment && (
        <QueueAdjustment
          entry={adjustment}
          onClose={() => setAdjustment(null)}
        />
      )}
    </>
  );
}
