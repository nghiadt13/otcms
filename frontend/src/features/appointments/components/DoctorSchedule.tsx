"use client";

import { useEffect, useMemo, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
import { StatusBadge, type StatusTone } from "@/components/admin/StatusBadge";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { apiRequest } from "@/lib/api/client";
import {
  addDays,
  formatDate,
  formatTime,
  todayIsoDate,
} from "@/lib/format/formatDate";
import { messageFromError } from "@/lib/messages/appMessages";
import { DoctorAppointmentDetailSheet } from "./DoctorAppointmentDetailSheet";

type Shift = {
  id: string;
  startsAt: string;
  endsAt: string;
  roomId: string | null;
};

type ShiftAppointment = {
  id: string;
  patientName: string | null;
  patientCode?: string | null;
  serviceName: string | null;
  startTime: string;
  endTime: string;
  status: string;
};

type AppointmentPage = {
  items: ShiftAppointment[];
  totalPages: number;
};

type Period = "week" | "day";
type View = "grid" | "list";

const TIME_ZONE = "Asia/Ho_Chi_Minh";
const WEEKDAYS = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];

function startOfWeek(date: string): string {
  const [year, month, day] = date.split("-").map(Number);
  const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  return addDays(date, weekday === 0 ? -6 : 1 - weekday);
}

function localDateKey(value: string): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date(value));
  const byType = Object.fromEntries(
    parts.map((part) => [part.type, part.value]),
  );
  return `${byType.year}-${byType.month}-${byType.day}`;
}

function weekdayShort(date: string): string {
  const [year, month, day] = date.split("-").map(Number);
  return WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
}

function shiftTime(shift: Shift): string {
  return `${formatTime(shift.startsAt)}–${formatTime(shift.endsAt)}`;
}

const APPOINTMENT_STATUS: Record<string, { label: string; tone: StatusTone }> =
  {
    CONFIRMED: { label: "Đã đặt", tone: "info" },
    CHECKED_IN: { label: "Đã tiếp nhận", tone: "success" },
    IN_PROGRESS: { label: "Đang khám", tone: "success" },
    COMPLETED: { label: "Đã hoàn thành", tone: "success" },
    CANCELLED: { label: "Đã hủy", tone: "neutral" },
    NO_SHOW: { label: "Không đến", tone: "warning" },
  };

export function DoctorSchedule() {
  const today = todayIsoDate();
  const [selectedDate, setSelectedDate] = useState(today);
  const [period, setPeriod] = useState<Period>("week");
  const [view, setView] = useState<View>("grid");
  const [loadedShifts, setLoadedShifts] = useState<{
    key: string;
    items: Shift[];
    error: string;
  } | null>(null);
  const [reload, setReload] = useState(0);
  const [selectedShift, setSelectedShift] = useState<Shift | null>(null);
  const [loadedShiftAppointments, setLoadedShiftAppointments] = useState<{
    shift: Shift;
    items: ShiftAppointment[] | null;
    error: string;
  } | null>(null);
  const [selectedAppointmentId, setSelectedAppointmentId] = useState<
    string | null
  >(null);

  const days = useMemo(() => {
    const firstDay =
      period === "week" ? startOfWeek(selectedDate) : selectedDate;
    return Array.from({ length: period === "week" ? 7 : 1 }, (_, index) =>
      addDays(firstDay, index),
    );
  }, [period, selectedDate]);
  const firstDay = days[0];
  const lastDay = days[days.length - 1];
  const rangeKey = `${firstDay}|${lastDay}|${reload}`;
  const currentShiftLoad = loadedShifts?.key === rangeKey ? loadedShifts : null;
  const shifts = currentShiftLoad?.items ?? null;
  const error = currentShiftLoad?.error ?? "";
  const currentAppointmentLoad =
    selectedShift && loadedShiftAppointments?.shift === selectedShift
      ? loadedShiftAppointments
      : null;
  const shiftAppointments = currentAppointmentLoad?.items ?? null;
  const shiftAppointmentsError = currentAppointmentLoad?.error ?? "";

  useEffect(() => {
    const controller = new AbortController();
    const key = `${firstDay}|${lastDay}|${reload}`;
    const shiftParams = new URLSearchParams({
      from: new Date(`${firstDay}T00:00:00+07:00`).toISOString(),
      to: new Date(`${addDays(lastDay, 1)}T00:00:00+07:00`).toISOString(),
    });
    apiRequest<{ items: Shift[] }>(`/api/v1/doctors/me/shifts?${shiftParams}`, {
      signal: controller.signal,
    })
      .then((schedule) =>
        setLoadedShifts({ key, items: schedule.items, error: "" }),
      )
      .catch((cause: unknown) => {
        if (!controller.signal.aborted)
          setLoadedShifts({ key, items: [], error: messageFromError(cause) });
      });
    return () => controller.abort();
  }, [firstDay, lastDay, reload]);

  useEffect(() => {
    if (!selectedShift) return;

    const shift = selectedShift;
    const controller = new AbortController();
    const from = localDateKey(shift.startsAt);
    const to = localDateKey(shift.endsAt);
    async function loadShiftAppointments() {
      try {
        const firstPage = await apiRequest<AppointmentPage>(
          `/api/v1/appointments?${new URLSearchParams({ from, to, page: "0", size: "100" })}`,
          { signal: controller.signal },
        );
        let items = firstPage.items;
        for (let page = 1; page < firstPage.totalPages; page += 1) {
          const nextPage = await apiRequest<AppointmentPage>(
            `/api/v1/appointments?${new URLSearchParams({ from, to, page: String(page), size: "100" })}`,
            { signal: controller.signal },
          );
          items = items.concat(nextPage.items);
        }
        if (controller.signal.aborted) return;
        const shiftStart = new Date(shift.startsAt).getTime();
        const shiftEnd = new Date(shift.endsAt).getTime();
        setLoadedShiftAppointments({
          shift,
          items: items.filter(
            (appointment) =>
              new Date(appointment.startTime).getTime() < shiftEnd &&
              new Date(appointment.endTime).getTime() > shiftStart,
          ),
          error: "",
        });
      } catch (cause) {
        if (!controller.signal.aborted)
          setLoadedShiftAppointments({
            shift,
            items: null,
            error: messageFromError(cause),
          });
      }
    }

    void loadShiftAppointments();
    return () => controller.abort();
  }, [selectedShift]);

  const shiftsByDay = useMemo(() => {
    const grouped = new Map<string, Shift[]>();
    for (const shift of shifts ?? []) {
      const date = localDateKey(shift.startsAt);
      grouped.set(date, [...(grouped.get(date) ?? []), shift]);
    }
    for (const dayShifts of grouped.values())
      dayShifts.sort((a, b) => a.startsAt.localeCompare(b.startsAt));
    return grouped;
  }, [shifts]);

  function movePeriod(direction: -1 | 1) {
    setSelectedDate((date) =>
      addDays(date, direction * (period === "week" ? 7 : 1)),
    );
  }

  function goToToday() {
    setSelectedDate(today);
  }

  const rangeLabel =
    period === "week"
      ? `${formatDate(firstDay)} – ${formatDate(lastDay)}`
      : `${weekdayShort(firstDay)} ${formatDate(firstDay)}`;
  const isLoading = shifts === null && !error;

  return (
    <div className="space-y-4">
      <PageHeader
        title="Lịch làm việc"
        description="Xem các ca làm việc của bạn theo tuần hoặc ngày."
      />

      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <div className="flex items-center gap-1.5">
          <Button
            variant="outline"
            size="icon"
            className="size-11"
            aria-label={period === "week" ? "Tuần trước" : "Ngày trước"}
            onClick={() => movePeriod(-1)}
          >
            <ChevronLeft aria-hidden />
          </Button>
          <Button
            variant="outline"
            className="h-11"
            disabled={days.includes(today)}
            onClick={goToToday}
          >
            Hôm nay
          </Button>
          <Button
            variant="outline"
            size="icon"
            className="size-11"
            aria-label={period === "week" ? "Tuần sau" : "Ngày sau"}
            onClick={() => movePeriod(1)}
          >
            <ChevronRight aria-hidden />
          </Button>
        </div>

        <p className="text-base font-medium tabular-nums" aria-live="polite">
          {rangeLabel}
        </p>

        <div className="flex items-center gap-2 sm:ml-auto">
          <ToggleGroup
            variant="outline"
            spacing={0}
            aria-label="Độ rộng lịch"
            value={[period]}
            onValueChange={(next) => {
              const value = next[0] as Period | undefined;
              if (value) setPeriod(value);
            }}
          >
            <ToggleGroupItem value="week" aria-label="Xem theo tuần">
              Tuần
            </ToggleGroupItem>
            <ToggleGroupItem value="day" aria-label="Xem theo ngày">
              Ngày
            </ToggleGroupItem>
          </ToggleGroup>
          <ToggleGroup
            variant="outline"
            spacing={0}
            aria-label="Kiểu hiển thị lịch"
            value={[view]}
            onValueChange={(next) => {
              const value = next[0] as View | undefined;
              if (value) setView(value);
            }}
          >
            <ToggleGroupItem value="grid" aria-label="Xem dạng lưới">
              Lưới
            </ToggleGroupItem>
            <ToggleGroupItem value="list" aria-label="Xem dạng danh sách">
              Danh sách
            </ToggleGroupItem>
          </ToggleGroup>
        </div>
      </div>

      {error ? (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-3 rounded-xl border bg-card p-4 text-sm"
        >
          <span>{error}</span>
          <Button
            variant="outline"
            onClick={() => setReload((value) => value + 1)}
          >
            Thử lại
          </Button>
        </div>
      ) : view === "grid" ? (
        <div className="overflow-x-auto rounded-xl border bg-card">
          <Table
            className="min-w-[58rem] table-fixed"
            aria-label={
              period === "week"
                ? "Lịch làm việc theo tuần"
                : "Lịch làm việc trong ngày"
            }
          >
            <TableHeader>
              <TableRow>
                <TableHead
                  scope="col"
                  className="sticky left-0 z-10 w-52 bg-card"
                >
                  Nhân viên
                </TableHead>
                {days.map((day) => (
                  <TableHead
                    key={day}
                    scope="col"
                    aria-current={day === today ? "date" : undefined}
                    className={`text-center tabular-nums ${day === today ? "text-primary" : ""}`}
                  >
                    {weekdayShort(day)} {day.slice(8, 10)}/{day.slice(5, 7)}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell
                    colSpan={days.length + 1}
                    className="h-24 text-center text-muted-foreground"
                  >
                    Đang tải lịch làm việc...
                  </TableCell>
                </TableRow>
              ) : shifts?.length === 0 ? (
                <TableRow>
                  <TableCell
                    colSpan={days.length + 1}
                    className="h-24 text-center text-muted-foreground"
                  >
                    Không có ca làm việc trong khoảng thời gian này.
                  </TableCell>
                </TableRow>
              ) : (
                <TableRow className="align-top">
                  <TableHead
                    scope="row"
                    className="sticky left-0 z-10 h-auto bg-card py-4 font-normal whitespace-normal"
                  >
                    <span className="block font-medium text-foreground">
                      Lịch của tôi
                    </span>
                    <span className="block text-xs text-muted-foreground">
                      Bác sĩ chỉnh hình
                    </span>
                  </TableHead>
                  {days.map((day) => (
                    <TableCell
                      key={day}
                      className="p-2 align-top whitespace-normal"
                    >
                      <div className="flex min-h-14 flex-col gap-1">
                        {(shiftsByDay.get(day) ?? []).map((shift) => (
                          <button
                            key={shift.id}
                            type="button"
                            className="rounded-lg bg-muted px-2.5 py-2 text-center text-sm font-medium tabular-nums text-foreground transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                            onClick={() => setSelectedShift(shift)}
                            aria-label={`Xem chi tiết ca ${shiftTime(shift)} ngày ${formatDate(shift.startsAt)}`}
                          >
                            {shiftTime(shift)}
                          </button>
                        ))}
                      </div>
                    </TableCell>
                  ))}
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border bg-card">
          {isLoading ? (
            <p className="p-5 text-sm text-muted-foreground">
              Đang tải lịch làm việc...
            </p>
          ) : shifts?.length === 0 ? (
            <p className="p-5 text-sm text-muted-foreground">
              Không có ca làm việc trong khoảng thời gian này.
            </p>
          ) : (
            <ul className="divide-y">
              {(shifts ?? [])
                .slice()
                .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
                .map((shift) => (
                  <li
                    key={shift.id}
                    className="flex flex-wrap items-center gap-x-6 gap-y-1 p-4"
                  >
                    <span className="min-w-40 text-sm font-medium tabular-nums">
                      {weekdayShort(localDateKey(shift.startsAt))},{" "}
                      {formatDate(shift.startsAt)}
                    </span>
                    <button
                      type="button"
                      className="rounded-lg bg-muted px-3 py-2 text-sm font-medium tabular-nums transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      onClick={() => setSelectedShift(shift)}
                      aria-label={`Xem chi tiết ca ${shiftTime(shift)} ngày ${formatDate(shift.startsAt)}`}
                    >
                      {shiftTime(shift)}
                    </button>
                  </li>
                ))}
            </ul>
          )}
        </div>
      )}

      <Sheet
        open={selectedShift !== null}
        onOpenChange={(open) => !open && setSelectedShift(null)}
      >
        <SheetContent className="overflow-y-auto sm:max-w-xl">
          <SheetHeader>
            <SheetTitle>Chi tiết ca làm việc</SheetTitle>
            <SheetDescription>
              {selectedShift
                ? `${weekdayShort(localDateKey(selectedShift.startsAt))}, ${formatDate(selectedShift.startsAt)} · ${shiftTime(selectedShift)}`
                : "Các lịch hẹn thuộc ca làm việc này."}
            </SheetDescription>
          </SheetHeader>
          <section
            className="px-4 pb-6 sm:px-6"
            aria-labelledby="shift-appointments-heading"
          >
            <h2 id="shift-appointments-heading" className="mb-3 font-semibold">
              Lịch hẹn trong ca
            </h2>
            {shiftAppointmentsError ? (
              <div role="alert" className="rounded-lg border p-4 text-sm">
                <p>{shiftAppointmentsError}</p>
                <Button
                  className="mt-3"
                  variant="outline"
                  onClick={() =>
                    setSelectedShift((shift) => (shift ? { ...shift } : null))
                  }
                >
                  Thử lại
                </Button>
              </div>
            ) : shiftAppointments === null ? (
              <p className="text-sm text-muted-foreground" aria-live="polite">
                Đang tải lịch hẹn trong ca...
              </p>
            ) : shiftAppointments.length === 0 ? (
              <p className="rounded-lg border p-4 text-sm text-muted-foreground">
                Không tìm thấy lịch hẹn trùng với thời gian ca này.
              </p>
            ) : (
              <ul className="divide-y rounded-lg border">
                {shiftAppointments.map((appointment) => {
                  const status = APPOINTMENT_STATUS[appointment.status] ?? {
                    label: "Chưa xác định",
                    tone: "neutral" as const,
                  };
                  return (
                    <li
                      key={appointment.id}
                      className="flex flex-wrap items-center justify-between gap-3 p-4"
                    >
                      <div className="min-w-0">
                        <p className="font-medium">
                          {formatTime(appointment.startTime)} ·{" "}
                          {appointment.patientName ?? "Chưa có tên bệnh nhân"}
                        </p>
                        <p className="text-sm text-muted-foreground">
                          {appointment.serviceName ?? "Chưa xác định dịch vụ"}
                          {appointment.patientCode
                            ? ` · ${appointment.patientCode}`
                            : ""}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <StatusBadge tone={status.tone}>
                          {status.label}
                        </StatusBadge>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => {
                            setSelectedAppointmentId(appointment.id);
                            setSelectedShift(null);
                          }}
                        >
                          Xem hồ sơ
                        </Button>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        </SheetContent>
      </Sheet>
      <DoctorAppointmentDetailSheet
        appointmentId={selectedAppointmentId}
        onClose={() => setSelectedAppointmentId(null)}
      />
    </div>
  );
}
