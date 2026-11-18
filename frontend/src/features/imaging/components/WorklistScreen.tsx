"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";
import {
  callNextService,
  getServiceWorklist,
  markServiceEntryAbsent,
  reportCannotPerform,
  returnServiceEntry,
  type ServiceQueueStatus,
  type ServiceWorklistEntry,
} from "../api/imagingQueueApi";
import { listImagingRooms, type ImagingRoom } from "../api/imagingRoomApi";
import { Badge } from "./Badge";
import { Empty } from "./Empty";
import { Icon } from "./Icon";
import { PageHeading } from "./PageHeading";
import { Panel } from "./Panel";
import { PatientAvatar } from "./PatientAvatar";

const REFRESH_MS = 10_000;

const statusLabels: Record<ServiceQueueStatus, string> = {
  WAITING: "Đang chờ",
  CALLED: "Đã gọi",
  IN_EXAMINATION: "Đang thực hiện",
  ABSENT: "Vắng mặt",
};

const statusTones: Record<ServiceQueueStatus, string> = {
  WAITING: "neutral",
  CALLED: "blue",
  IN_EXAMINATION: "blue",
  ABSENT: "amber",
};

/** UC-46: hàng chờ thật của phòng chụp (stage SERVICE), thứ tự do hệ thống xếp theo giờ vào hàng. */
export function WorklistScreen() {
  const [rooms, setRooms] = useState<ImagingRoom[]>([]);
  const [roomId, setRoomId] = useState<string | null>(null);
  const [entries, setEntries] = useState<ServiceWorklistEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [tab, setTab] = useState<"active" | "absent">("active");
  // One small reason form serves both "return to queue" and "cannot perform" (both need a written reason).
  const [returning, setReturning] = useState<{
    mode: "return" | "unable";
    id: string;
    reason: string;
  } | null>(null);
  const controller = useRef<AbortController | null>(null);

  const refresh = useCallback(async (room: string) => {
    controller.current?.abort();
    const current = new AbortController();
    controller.current = current;
    try {
      const worklist = await getServiceWorklist(room, current.signal);
      if (!current.signal.aborted) {
        setEntries(worklist.entries);
        setError(null);
      }
    } catch (cause) {
      if (!current.signal.aborted) setError(messageFromError(cause));
    } finally {
      if (!current.signal.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    listImagingRooms()
      .then((items) => {
        if (!active) return;
        const usable = items.filter((room) => room.availability === "Hoạt động");
        setRooms(usable);
        setRoomId(usable[0]?.id ?? null);
        if (usable.length === 0) setLoading(false);
      })
      .catch((cause: unknown) => {
        if (!active) return;
        setError(messageFromError(cause));
        setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!roomId) return;
    const room = roomId;
    queueMicrotask(() => void refresh(room));
    const timer = setInterval(() => void refresh(room), REFRESH_MS);
    return () => {
      clearInterval(timer);
      controller.current?.abort();
    };
  }, [roomId, refresh]);

  async function run(action: () => Promise<unknown>, success: string) {
    if (!roomId) return;
    setBusy(true);
    setNotice(null);
    try {
      await action();
      setNotice(success);
      setError(null);
      await refresh(roomId);
    } catch (cause) {
      setError(messageFromError(cause));
    } finally {
      setBusy(false);
    }
  }

  const active = entries.filter((entry) => entry.status !== "ABSENT");
  const absent = entries.filter((entry) => entry.status === "ABSENT");
  const shown = tab === "active" ? active : absent;
  const room = rooms.find((item) => item.id === roomId);

  return (
    <>
      <PageHeading
        title="Danh sách chờ chẩn đoán hình ảnh"
        description="Bệnh nhân đã thanh toán chỉ định, xếp theo giờ vào hàng của phòng bạn."
      >
        <Button
          className="im-button"
          disabled={!roomId || busy}
          onClick={() => roomId && void run(() => callNextService(roomId), formatAppMessage("MSG-43"))}
        >
          <Icon name="user" />
          Gọi tiếp theo
        </Button>
      </PageHeading>
      {rooms.length > 1 && (
        <label className="im-small">
          Phòng{" "}
          <select
            aria-label="Chọn phòng"
            value={roomId ?? ""}
            onChange={(event) => {
              setLoading(true);
              setRoomId(event.target.value);
            }}
          >
            {rooms.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name} ({item.code})
              </option>
            ))}
          </select>
        </label>
      )}
      {room && (
        <div className="im-room-banner">
          <span className="im-room-icon">
            <Icon name="room" />
          </span>
          <div>
            <strong>
              {room.name} <span>{room.code}</span>
            </strong>
          </div>
          <Badge tone="green">Đang nhận bệnh nhân</Badge>
        </div>
      )}
      {error && (
        <p role="alert" className="im-form-error">
          {error}
        </p>
      )}
      {notice && <p role="status">{notice}</p>}
      <Panel>
        <div className="im-tabs" role="tablist" aria-label="Nhóm hàng đợi">
          {(
            [
              ["active", "Đang chờ & phục vụ", active.length],
              ["absent", "Vắng mặt", absent.length],
            ] as const
          ).map(([id, label, count]) => (
            <Button
              variant="ghost"
              key={id}
              role="tab"
              aria-selected={tab === id}
              className={tab === id ? "im-tab-active" : ""}
              onClick={() => setTab(id)}
            >
              {label}
              <span>{count}</span>
            </Button>
          ))}
        </div>
        {loading ? (
          <p role="status" className="p-5">
            Đang tải hàng đợi…
          </p>
        ) : shown.length === 0 ? (
          <Empty
            title={tab === "absent" ? "Không có bệnh nhân vắng mặt" : "Chưa có bệnh nhân đang chờ"}
            description="Bệnh nhân vào hàng ngay khi chỉ định được thanh toán."
          />
        ) : (
          <div className="im-table-scroll">
            <Table className="im-table">
              <TableHeader>
                <TableRow>
                  <TableHead>Số thứ tự</TableHead>
                  <TableHead>Bệnh nhân</TableHead>
                  <TableHead>Chỉ định tại phòng</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead>Thao tác</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {shown.map((entry) => (
                  <TableRow key={entry.entryId} className={entry.status === "IN_EXAMINATION" ? "im-row-serving" : ""}>
                    <TableCell>
                      <strong className="im-number-text">{entry.queueNumber ?? "—"}</strong>
                    </TableCell>
                    <TableCell>
                      <div className="im-patient-cell">
                        <PatientAvatar name={entry.patient.fullName ?? "?"} />
                        <div>
                          <strong>{entry.patient.fullName ?? "Không rõ tên"}</strong>
                          <small>
                            {entry.patient.age != null ? `${entry.patient.age} tuổi` : "—"}
                            {entry.patient.gender ? ` · ${entry.patient.gender}` : ""}
                          </small>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      {entry.orders.map((order) => (
                        <div key={order.orderId}>
                          {order.paid ? (
                            <Link className="im-order-line" href={`/imaging/orders/${order.orderId}`}>
                              {order.serviceName}
                            </Link>
                          ) : (
                            <span className="im-order-line" title={formatAppMessage("MSG-95")}>
                              {order.serviceName} <Badge tone="red">Chưa thanh toán</Badge>
                            </span>
                          )}
                          <small>
                            {order.orderCode}
                            {order.bodyArea ? ` · ${order.bodyArea}` : ""}
                            {order.instructions ? ` · ${order.instructions}` : ""}
                          </small>
                          {!order.paid && <small role="alert">{formatAppMessage("MSG-95")}</small>}
                        </div>
                      ))}
                    </TableCell>
                    <TableCell>
                      <Badge tone={statusTones[entry.status]}>{statusLabels[entry.status]}</Badge>
                    </TableCell>
                    <TableCell>
                      <div className="im-row-actions">
                        {entry.status === "CALLED" && (
                          <>
                            <Button
                              variant="ghost"
                              className="im-text-button im-danger"
                              disabled={busy}
                              onClick={() => void run(() => markServiceEntryAbsent(entry.entryId), formatAppMessage("MSG-43"))}
                            >
                              Đánh dấu vắng
                            </Button>
                            <Button
                              variant="ghost"
                              className="im-text-button"
                              disabled={busy || entry.orders.length === 0}
                              onClick={() => setReturning({ mode: "unable", id: entry.orders[0]?.orderId ?? "", reason: "" })}
                            >
                              Không thực hiện được
                            </Button>
                          </>
                        )}
                        {entry.status === "ABSENT" && (
                          <Button
                            variant="ghost"
                            className="im-text-button"
                            disabled={busy}
                            onClick={() => setReturning({ mode: "return", id: entry.entryId, reason: "" })}
                          >
                            Trở lại hàng đợi
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
        {returning && (
          <form
            className="im-toolbar"
            onSubmit={(event) => {
              event.preventDefault();
              const target = returning;
              void run(
                () =>
                  target.mode === "unable"
                    ? reportCannotPerform(target.id, target.reason)
                    : returnServiceEntry(target.id, target.reason),
                formatAppMessage("MSG-43"),
              ).then(() => setReturning(null));
            }}
          >
            <label>
              {returning.mode === "unable" ? "Lý do không thực hiện được" : "Lý do đưa trở lại hàng"}
              <input
                aria-label={returning.mode === "unable" ? "Lý do không thực hiện được" : "Lý do đưa trở lại hàng"}
                value={returning.reason}
                onChange={(event) => setReturning({ ...returning, reason: event.target.value })}
              />
            </label>
            <Button type="submit" disabled={busy || !returning.reason.trim()}>
              Xác nhận
            </Button>
            <Button type="button" variant="ghost" onClick={() => setReturning(null)}>
              Hủy
            </Button>
          </form>
        )}
      </Panel>
      <p className="im-footnote">
        <Icon name="shield" size={16} />
        Thứ tự theo giờ vào hàng đợi. Chỉ định chưa thanh toán không được tiếp nhận.
      </p>
    </>
  );
}
