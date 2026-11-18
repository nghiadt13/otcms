"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import {
  listReceptionExamQueue,
  type ExamQueueItem,
} from "@/lib/api/examQueueApi";
import {
  EXAM_QUEUE_STATUS_LABELS,
  EXAM_QUEUE_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { messageFromError } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import {
  callNextInRoom,
  listRoomQueue,
  markQueueEntryAbsent,
  type RoomQueueEntry,
} from "../api/receptionLiveApi";
import {
  itemsByVisit,
  roomOptionsOf,
  splitRoomEntries,
} from "../schema/roomQueueModel";
import { AttendingDoctorTransferDialog } from "./AttendingDoctorTransferDialog";
import { QueueReturnDialog } from "./QueueReturnDialog";
import { QueueRoomPanel } from "./QueueRoomPanel";
import { WalkInFeeStep } from "./WalkInFeeStep";
import { DataLoading, DataTable, SelectField } from "./ReceptionUi";

const REFRESH_MS = 15_000;

/**
 * Hàng chờ khám hôm nay theo phòng (UC-32, UC-33): gọi người kế, đánh vắng, đưa người vắng quay lại.
 * Lượt chờ thu phí nằm riêng để lễ tân thu tiếp. Thứ tự hàng và trạng thái lấy từ BE.
 */
export function QueueScreen() {
  const [items, setItems] = useState<ExamQueueItem[]>([]);
  const [roomId, setRoomId] = useState<string | null>(null);
  const [transferring, setTransferring] = useState<RoomQueueEntry | null>(null);
  const [entries, setEntries] = useState<RoomQueueEntry[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [paying, setPaying] = useState<ExamQueueItem | null>(null);
  const [returning, setReturning] = useState<RoomQueueEntry | null>(null);
  const mounted = useRef(false);
  // Phòng đang chọn (đọc được trong bộ hẹn giờ) và số thứ tự yêu cầu: chỉ kết quả của yêu cầu mới nhất được hiện.
  const selectedRoom = useRef<string | null>(null);
  const latestRequest = useRef(0);

  const load = useCallback(async (wantedRoom?: string) => {
    const request = ++latestRequest.current;
    setLoading(true);
    try {
      const page = await listReceptionExamQueue();
      const rooms = roomOptionsOf(page.items);
      const wanted = wantedRoom ?? selectedRoom.current;
      const next = rooms.some((room) => room.id === wanted)
        ? wanted
        : (rooms[0]?.id ?? null);
      const rows = next ? await listRoomQueue(next, "EXAM") : [];
      if (!mounted.current || request !== latestRequest.current) return;
      selectedRoom.current = next;
      setItems(page.items);
      setRoomId(next);
      setEntries(rows);
      setError("");
    } catch (cause) {
      if (mounted.current && request === latestRequest.current)
        setError(messageFromError(cause));
    } finally {
      if (mounted.current && request === latestRequest.current) {
        setLoaded(true);
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    const initialLoad = window.setTimeout(() => void load(), 0);
    const interval = window.setInterval(() => void load(), REFRESH_MS);
    return () => {
      mounted.current = false;
      window.clearTimeout(initialLoad);
      window.clearInterval(interval);
    };
  }, [load]);

  // Một thao tác gọi/vắng tại một lúc; xong thì tải lại hàng dù thành công hay lỗi (lỗi có thể do hàng đã đổi).
  async function runRoomAction(action: () => Promise<unknown>) {
    setBusy(true);
    try {
      await action();
      notifySuccess("MSG-43");
    } catch (cause) {
      notifyError(cause);
    } finally {
      if (mounted.current) setBusy(false);
    }
    await load();
  }

  const rooms = roomOptionsOf(items);
  const byVisit = itemsByVisit(items);
  const itemOf = (visitId: string) => byVisit.get(visitId);
  const awaitingPayment = items.filter(
    (item) => item.examStatus === "PENDING_PAYMENT",
  );
  const unassigned = items.filter(
    (item) => !item.roomId && item.examStatus !== "PENDING_PAYMENT",
  );
  const waitingForReturn = splitRoomEntries(entries).waiting;

  return (
    <>
      <PageHeader
        title="Hàng chờ khám"
        description="Chọn phòng để gọi bệnh nhân kế tiếp, đánh dấu vắng hoặc đưa người vắng quay lại hàng. Danh sách tự làm mới mỗi 15 giây."
        actions={
          <Button
            variant="outline"
            disabled={loading}
            onClick={() => void load()}
          >
            {loading ? "Đang tải..." : "Làm mới"}
          </Button>
        }
      />
      {error && (
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
          <Button variant="outline" onClick={() => void load()}>
            Thử lại
          </Button>
        </div>
      )}
      {!loaded ? (
        <DataLoading />
      ) : items.length === 0 ? (
        <p className="rounded-lg border p-6 text-center text-sm text-muted-foreground">
          Chưa có bệnh nhân trong hàng chờ hôm nay. Bệnh nhân có lịch hẹn được
          đưa vào hàng chờ khi tiếp nhận; khách đến trực tiếp thì tạo lượt khám
          ở mục Tiếp nhận bệnh nhân.
        </p>
      ) : (
        <div className="space-y-8">
          {awaitingPayment.length > 0 && (
            <section className="space-y-3">
              <h2 className="text-lg font-semibold">Chờ thu phí</h2>
              <DataTable
                label="Lượt chờ thu phí"
                headers={["Bệnh nhân", "Bác sĩ", "Thao tác"]}
                empty="Không có lượt chờ thu phí."
                rows={awaitingPayment.map((item) => ({
                  id: item.visitId,
                  cells: [
                    item.patientName,
                    item.doctorName,
                    item.serviceId ? (
                      <Button
                        key="pay"
                        variant="outline"
                        onClick={() => setPaying(item)}
                      >
                        Thu phí
                      </Button>
                    ) : (
                      "Chưa có dịch vụ"
                    ),
                  ],
                }))}
              />
            </section>
          )}

          {rooms.length > 0 && roomId ? (
            <section className="space-y-6">
              <div className="max-w-sm">
                <SelectField
                  label="Phòng"
                  value={roomId}
                  onChange={(id) => void load(id)}
                  options={rooms.map((room) => ({
                    value: room.id,
                    label: room.label,
                  }))}
                  disabled={busy}
                />
              </div>
              <QueueRoomPanel
                roomLabel={
                  rooms.find((room) => room.id === roomId)?.label ?? ""
                }
                entries={entries}
                itemOf={itemOf}
                busy={busy}
                onCallNext={() =>
                  void runRoomAction(() => callNextInRoom(roomId, "EXAM"))
                }
                onAbsent={(entry) =>
                  void runRoomAction(() => markQueueEntryAbsent(entry.id))
                }
                onReturn={setReturning}
                onTransferDoctor={setTransferring}
              />
            </section>
          ) : null}

          {unassigned.length > 0 && (
            <section className="space-y-3">
              <h2 className="text-lg font-semibold">Chưa có phòng</h2>
              <DataTable
                label="Lượt chưa có phòng"
                headers={["Số", "Bệnh nhân", "Trạng thái"]}
                empty="Không có lượt chưa có phòng."
                rows={unassigned.map((item) => ({
                  id: item.visitId,
                  cells: [
                    item.queueNumber ?? "—",
                    item.patientName,
                    <StatusBadge
                      key="status"
                      tone={EXAM_QUEUE_STATUS_TONE[item.examStatus]}
                    >
                      {EXAM_QUEUE_STATUS_LABELS[item.examStatus]}
                    </StatusBadge>,
                  ],
                }))}
              />
            </section>
          )}
        </div>
      )}
      {paying?.serviceId && (
        <WalkInFeeStep
          visitId={paying.visitId}
          serviceId={paying.serviceId}
          onClose={() => setPaying(null)}
          onQueued={() => {
            setPaying(null);
            void load();
          }}
        />
      )}
      {transferring && (
        <AttendingDoctorTransferDialog
          visitId={transferring.visitId}
          patientName={itemOf(transferring.visitId)?.patientName ?? "—"}
          currentDoctorProfileId={
            itemOf(transferring.visitId)?.doctorProfileId ?? ""
          }
          onClose={() => setTransferring(null)}
          onSaved={() => {
            setTransferring(null);
            void load();
          }}
          onFailed={() => void load()}
        />
      )}
      {returning && roomId && (
        <QueueReturnDialog
          entry={returning}
          waiting={waitingForReturn}
          nameOf={(entry) => itemOf(entry.visitId)?.patientName ?? "—"}
          onClose={() => setReturning(null)}
          onSaved={() => {
            setReturning(null);
            void load();
          }}
          onFailed={() => void load()}
        />
      )}
    </>
  );
}
