"use client";

import { useEffect, useMemo, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { Button } from "@/components/ui/button";
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
import { addDays, formatDate, formatTime, todayIsoDate } from "@/lib/format/formatDate";
import { messageFromError } from "@/lib/messages/appMessages";

type Shift = {
  id: string;
  startsAt: string;
  endsAt: string;
  roomId: string | null;
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
  const byType = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${byType.year}-${byType.month}-${byType.day}`;
}

function weekdayShort(date: string): string {
  const [year, month, day] = date.split("-").map(Number);
  return WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
}

function shiftTime(shift: Shift): string {
  return `${formatTime(shift.startsAt)}–${formatTime(shift.endsAt)}`;
}

export function DoctorSchedule() {
  const today = todayIsoDate();
  const [selectedDate, setSelectedDate] = useState(today);
  const [period, setPeriod] = useState<Period>("week");
  const [view, setView] = useState<View>("grid");
  const [shifts, setShifts] = useState<Shift[] | null>(null);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);

  const days = useMemo(() => {
    const firstDay = period === "week" ? startOfWeek(selectedDate) : selectedDate;
    return Array.from({ length: period === "week" ? 7 : 1 }, (_, index) =>
      addDays(firstDay, index),
    );
  }, [period, selectedDate]);
  const firstDay = days[0];
  const lastDay = days[days.length - 1];

  useEffect(() => {
    const controller = new AbortController();
    setShifts(null);
    setError("");
    const shiftParams = new URLSearchParams({
      from: new Date(`${firstDay}T00:00:00+07:00`).toISOString(),
      to: new Date(`${addDays(lastDay, 1)}T00:00:00+07:00`).toISOString(),
    });
    apiRequest<{ items: Shift[] }>(
      `/api/v1/doctors/me/shifts?${shiftParams}`,
      { signal: controller.signal },
    )
      .then((schedule) => setShifts(schedule.items))
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(messageFromError(cause));
      });
    return () => controller.abort();
  }, [firstDay, lastDay, reload]);

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
          <Button variant="outline" onClick={() => setReload((value) => value + 1)}>
            Thử lại
          </Button>
        </div>
      ) : view === "grid" ? (
        <div className="overflow-x-auto rounded-xl border bg-card">
          <Table
            className="min-w-[58rem] table-fixed"
            aria-label={
              period === "week" ? "Lịch làm việc theo tuần" : "Lịch làm việc trong ngày"
            }
          >
            <TableHeader>
              <TableRow>
                <TableHead scope="col" className="sticky left-0 z-10 w-52 bg-card">
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
                    <span className="block font-medium text-foreground">Lịch của tôi</span>
                    <span className="block text-xs text-muted-foreground">
                      Bác sĩ chỉnh hình
                    </span>
                  </TableHead>
                  {days.map((day) => (
                    <TableCell key={day} className="p-2 align-top whitespace-normal">
                      <div className="flex min-h-14 flex-col gap-1">
                        {(shiftsByDay.get(day) ?? []).map((shift) => (
                          <span
                            key={shift.id}
                            className="rounded-lg bg-muted px-2.5 py-2 text-center text-sm font-medium tabular-nums text-foreground"
                          >
                            {shiftTime(shift)}
                          </span>
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
            <p className="p-5 text-sm text-muted-foreground">Đang tải lịch làm việc...</p>
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
                  <li key={shift.id} className="flex flex-wrap items-center gap-x-6 gap-y-1 p-4">
                    <span className="min-w-40 text-sm font-medium tabular-nums">
                      {weekdayShort(localDateKey(shift.startsAt))},{" "}
                      {formatDate(shift.startsAt)}
                    </span>
                    <span className="rounded-lg bg-muted px-3 py-2 text-sm font-medium tabular-nums">
                      {shiftTime(shift)}
                    </span>
                  </li>
                ))}
            </ul>
          )}
        </div>
      )}

    </div>
  );
}
