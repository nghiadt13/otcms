"use client";

import { useEffect, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { apiRequest } from "@/lib/api/client";
import { addDays, formatDateTime, todayIsoDate } from "@/lib/format/formatDate";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";

type Appointment = { id: string; patientName: string | null; serviceName: string | null; doctorDisplayName: string | null; startTime: string; status: string };
type Shift = { id: string; startsAt: string; endsAt: string; roomId: string | null };
type Page = { items: Appointment[]; totalPages: number };
const statuses: Record<string, string> = { CONFIRMED: "Đã đặt", CHECKED_IN: "Đã tiếp nhận", IN_PROGRESS: "Đang khám", COMPLETED: "Đã hoàn thành", CANCELLED: "Đã hủy", NO_SHOW: "Không đến" };

export function DoctorSchedule() {
  const today = todayIsoDate();
  const [from, setFrom] = useState(addDays(today, -30));
  const [to, setTo] = useState(addDays(today, 30));
  const [page, setPage] = useState(0);
  const [appointments, setAppointments] = useState<Page | null>(null);
  const [shifts, setShifts] = useState<Shift[] | null>(null);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const invalidRange = !from || !to || from > to || to >= addDays(from, 90);

  useEffect(() => {
    if (invalidRange) return;
    const controller = new AbortController();
    const params = new URLSearchParams({ from, to });
    const shiftParams = new URLSearchParams({
      from: new Date(`${from}T00:00:00+07:00`).toISOString(),
      to: new Date(`${addDays(to, 1)}T00:00:00+07:00`).toISOString(),
    });
    Promise.all([
      apiRequest<Page>(`/api/v1/appointments?${params}&page=${page}&size=20`, { signal: controller.signal }),
      apiRequest<{ items: Shift[] }>(`/api/v1/doctors/me/shifts?${shiftParams}`, { signal: controller.signal }),
    ]).then(([result, schedule]) => {
      setAppointments(result); setShifts(schedule.items); setError("");
    }).catch((cause: unknown) => { if (!controller.signal.aborted) setError(messageFromError(cause)); });
    return () => controller.abort();
  }, [from, to, page, reload, invalidRange]);

  return <div className="space-y-4">
    <PageHeader title="Lịch và ca làm việc" description="Xem lịch hẹn và ca làm việc của bạn." />
    <div className="flex flex-wrap gap-3">
      <label>Từ ngày <Input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); setAppointments(null); setShifts(null); }} /></label>
      <label>Đến ngày <Input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); setAppointments(null); setShifts(null); }} /></label>
    </div>
    {invalidRange ? <p role="alert">{formatAppMessage("MSG-54")}</p> : error ? <div role="alert">{error} <Button variant="outline" onClick={() => setReload((n) => n + 1)}>Thử lại</Button></div> :
      <Tabs defaultValue="appointments">
        <TabsList><TabsTrigger value="appointments">Lịch hẹn</TabsTrigger><TabsTrigger value="shifts">Ca làm việc</TabsTrigger></TabsList>
        <TabsContent value="appointments">
          {!appointments ? <p>Đang tải...</p> : appointments.items.length === 0 ? <p>Không có lịch hẹn trong khoảng ngày này.</p> : <ul className="space-y-2">{appointments.items.map((a) => <li className="rounded-lg border p-4" key={a.id}><p>{formatDateTime(a.startTime)} · {a.patientName ?? "—"}</p><p>{a.serviceName ?? "—"} · {a.doctorDisplayName ?? "—"} · {statuses[a.status] ?? a.status}</p></li>)}</ul>}
          {appointments && appointments.totalPages > 1 && <div className="mt-3 flex gap-2"><Button variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Trước</Button><span>Trang {page + 1} / {appointments.totalPages}</span><Button variant="outline" disabled={page + 1 >= appointments.totalPages} onClick={() => setPage(page + 1)}>Sau</Button></div>}
        </TabsContent>
        <TabsContent value="shifts">{!shifts ? <p>Đang tải...</p> : shifts.length === 0 ? <p>Không có ca làm việc trong khoảng ngày này.</p> : <ul className="space-y-2">{shifts.map((s) => <li className="rounded-lg border p-4" key={s.id}>{formatDateTime(s.startsAt)} – {formatDateTime(s.endsAt)}</li>)}</ul>}</TabsContent>
      </Tabs>}
  </div>;
}
