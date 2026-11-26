"use client";

import { useEffect, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { apiRequest } from "@/lib/api/client";
import { addDays, formatDateTime, todayIsoDate } from "@/lib/format/formatDate";
import { messageFromError } from "@/lib/messages/appMessages";

type Appointment = {
  id: string;
  patientName: string | null;
  serviceName: string | null;
  doctorDisplayName: string | null;
  startTime: string;
  status: string;
};
type AppointmentPage = { items: Appointment[]; totalPages: number };

const STATUS_LABELS: Record<string, string> = {
  CONFIRMED: "Đã đặt",
  BOOKED: "Đã đặt",
  CHECKED_IN: "Đã tiếp nhận",
  IN_PROGRESS: "Đang khám",
  COMPLETED: "Đã hoàn thành",
  CANCELLED: "Đã hủy",
  NO_SHOW: "Không đến",
};

export function DoctorAppointmentHistory() {
  const today = todayIsoDate();
  const [from, setFrom] = useState(addDays(today, -30));
  const [to, setTo] = useState(today);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<AppointmentPage | null>(null);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const invalidRange = !from || !to || from > to || to >= addDays(from, 90);

  useEffect(() => {
    if (invalidRange) return;
    const controller = new AbortController();
    const params = new URLSearchParams({ from, to, page: String(page), size: "20" });
    apiRequest<AppointmentPage>(`/api/v1/appointments?${params}`, {
      signal: controller.signal,
    })
      .then((data) => {
        setResult(data);
        setError("");
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      });
    return () => controller.abort();
  }, [from, to, page, reload, invalidRange]);

  return (
    <div className="space-y-4">
      <PageHeader
        title="Lịch sử lịch hẹn"
        description="Tra cứu lịch hẹn của bạn và trạng thái từng lịch."
      />
      <div className="flex flex-wrap gap-3">
        <label>
          Từ ngày{" "}
          <Input
            type="date"
            value={from}
            onChange={(event) => {
              setFrom(event.target.value);
              setPage(0);
              setResult(null);
            }}
          />
        </label>
        <label>
          Đến ngày{" "}
          <Input
            type="date"
            value={to}
            onChange={(event) => {
              setTo(event.target.value);
              setPage(0);
              setResult(null);
            }}
          />
        </label>
      </div>
      {invalidRange ? (
        <p role="alert">Khoảng ngày không hợp lệ hoặc vượt quá 90 ngày.</p>
      ) : error ? (
        <div role="alert" className="space-y-2">
          <p>{error}</p>
          <Button variant="outline" onClick={() => setReload((value) => value + 1)}>
            Thử lại
          </Button>
        </div>
      ) : !result ? (
        <p role="status">Đang tải lịch hẹn…</p>
      ) : result.items.length === 0 ? (
        <p className="rounded-lg border bg-card px-4 py-8 text-center text-sm text-muted-foreground">
          Không có lịch hẹn trong khoảng ngày này.
        </p>
      ) : (
        <>
          <ul className="space-y-2">
            {result.items.map((appointment) => (
              <li className="rounded-lg border bg-card p-4" key={appointment.id}>
                <p className="font-medium">
                  {formatDateTime(appointment.startTime)} · {appointment.patientName ?? "—"}
                </p>
                <p className="text-sm text-muted-foreground">
                  {appointment.serviceName ?? "—"} · {appointment.doctorDisplayName ?? "—"} ·{" "}
                  {STATUS_LABELS[appointment.status] ?? appointment.status}
                </p>
              </li>
            ))}
          </ul>
          {result.totalPages > 1 ? (
            <div className="flex items-center gap-3">
              <Button variant="outline" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>
                Trước
              </Button>
              <span>Trang {page + 1} / {result.totalPages}</span>
              <Button variant="outline" disabled={page + 1 >= result.totalPages} onClick={() => setPage((value) => value + 1)}>
                Sau
              </Button>
            </div>
          ) : null}
        </>
      )}
    </div>
  );
}
