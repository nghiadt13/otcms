"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { formatTime } from "@/lib/format/formatDate";
import { listAffectedAppointments } from "../api/shiftApi";
import type { AffectedAppointmentsResponse } from "../types/shift";

type AffectedAppointmentsNoticeProps = {
  shiftId: string;
  /** Đóng dialog và mở màn Lịch hẹn đã lọc theo bác sĩ + ngày; không lưu gì. */
  onTransfer: () => void;
};

type State =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; data: AffectedAppointmentsResponse };

/**
 * Khối "Lịch hẹn trong ca này" ở đầu dialog sửa ca bác sĩ. Chỉ để Admin thấy tác động;
 * lỗi tải không chặn Lưu vì BE vẫn chặn khi còn lịch hẹn.
 */
export function AffectedAppointmentsNotice({
  shiftId,
  onTransfer,
}: AffectedAppointmentsNoticeProps) {
  const [state, setState] = useState<State>({ status: "loading" });
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- bắt đầu tải là hệ quả của lần thử lại
    setState({ status: "loading" });
    listAffectedAppointments(shiftId, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setState({ status: "ready", data });
      })
      .catch(() => {
        if (!controller.signal.aborted) setState({ status: "error" });
      });
    return () => controller.abort();
  }, [shiftId, reloadToken]);

  if (state.status === "loading")
    return <Skeleton className="h-16 w-full" aria-label="Đang tải lịch hẹn trong ca" />;

  if (state.status === "error")
    return (
      <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3 text-sm">
        <p role="alert" className="text-destructive">Không tải được lịch hẹn trong ca này.</p>
        <Button variant="outline" size="sm" onClick={() => setReloadToken((n) => n + 1)}>
          Thử lại
        </Button>
      </div>
    );

  const { totalItems, items } = state.data;
  if (totalItems === 0) return null;

  return (
    <section aria-label="Lịch hẹn trong ca này" className="rounded-lg border bg-muted/50 p-3 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-medium">
          Lịch hẹn trong ca này: <span className="tabular-nums">{totalItems}</span>
        </p>
        <Button variant="outline" size="sm" onClick={onTransfer}>
          Chuyển lịch hẹn
        </Button>
      </div>
      <p className="mt-1 text-muted-foreground">
        Chuyển các lịch hẹn sang bác sĩ khác trước khi sửa hoặc xóa ca này.
      </p>
      <ul className="mt-2 space-y-1">
        {items.map((item) => (
          <li key={item.appointmentId} className="flex gap-2">
            <span className="tabular-nums text-muted-foreground">{formatTime(item.startAt)}</span>
            <span className="min-w-0 truncate">
              {item.patientName} · {item.serviceName}
            </span>
          </li>
        ))}
      </ul>
      {totalItems > items.length ? (
        <p className="mt-1 text-xs text-muted-foreground">
          Và {totalItems - items.length} lịch hẹn khác.
        </p>
      ) : null}
    </section>
  );
}
