"use client";

import { useEffect, useState } from "react";
import {
  CalendarDays,
  CalendarRange,
  ChevronLeft,
  ChevronRight,
  Clock3,
  MapPin,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { apiRequest } from "@/lib/api/client";

type Shift = {
  id: string;
  date: string;
  startTime: string;
  endTime: string;
  roomName: string;
  roomCategory: string;
};

function localDateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function addDays(dateKey: string, days: number) {
  const date = new Date(`${dateKey}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function formatDate(dateKey: string) {
  const date = new Date(`${dateKey}T00:00:00`);
  return new Intl.DateTimeFormat("vi-VN", { dateStyle: "full" }).format(date);
}

function shiftStatus(dateKey: string, today: string) {
  if (dateKey < today) return "Đã qua";
  if (dateKey === today) return "Hôm nay";
  return "Sắp tới";
}

export function LaboratorySchedule() {
  const [from, setFrom] = useState(() => localDateKey(new Date()));
  const [shifts, setShifts] = useState<Shift[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [revision, setRevision] = useState(0);
  const to = addDays(from, 6);
  const today = localDateKey(new Date());
  const orderedShifts = [...shifts].sort((first, second) =>
    `${first.date} ${first.startTime}`.localeCompare(
      `${second.date} ${second.startTime}`,
    ),
  );

  useEffect(() => {
    const controller = new AbortController();
    const query = new URLSearchParams({ from, to });
    apiRequest<{ items: Shift[] }>(`/api/v1/me/staff-shifts?${query}`, {
      signal: controller.signal,
    })
      .then((result) => {
        if (!controller.signal.aborted) setShifts(result.items);
      })
      .catch(() => {
        if (!controller.signal.aborted) setError(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [from, to, revision]);

  return (
    <div className="space-y-5">
      <section className="flex flex-col gap-4 rounded-xl border bg-card p-5 md:flex-row md:items-center md:justify-between md:p-6">
        <div className="flex gap-4">
          <div className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <CalendarRange aria-hidden className="size-6" />
          </div>
          <div>
            <h2 className="font-semibold">Xem ca được phân công</h2>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">
              Lịch hiển thị trong 7 ngày kể từ ngày bắt đầu. Chọn ngày để tra
              cứu ca và phòng xét nghiệm tương ứng.
            </p>
          </div>
        </div>
        <div className="flex flex-wrap items-end gap-2 md:shrink-0">
          <Button
            aria-label="Xem 7 ngày trước"
            variant="outline"
            size="icon"
            onClick={() => {
              setLoading(true);
              setError(false);
              setFrom((value) => addDays(value, -7));
            }}
          >
            <ChevronLeft aria-hidden />
          </Button>
          <label className="min-w-44 space-y-1 text-sm">
            <span>Ngày bắt đầu</span>
            <Input
              type="date"
              value={from}
              onChange={(event) => {
                if (event.target.value) {
                  setLoading(true);
                  setError(false);
                  setFrom(event.target.value);
                }
              }}
            />
          </label>
          <Button
            aria-label="Xem 7 ngày tiếp theo"
            variant="outline"
            size="icon"
            onClick={() => {
              setLoading(true);
              setError(false);
              setFrom((value) => addDays(value, 7));
            }}
          >
            <ChevronRight aria-hidden />
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              setLoading(true);
              setError(false);
              if (from === today) setRevision((value) => value + 1);
              else setFrom(today);
            }}
          >
            Hôm nay
          </Button>
        </div>
      </section>

      <section className="grid gap-3 sm:grid-cols-2">
        <div className="rounded-xl border bg-card p-4">
          <p className="text-sm text-muted-foreground">Ca trong khoảng đang xem</p>
          <p className="mt-1 text-2xl font-semibold">
            {loading || error ? "—" : orderedShifts.length}
          </p>
        </div>
        <div className="rounded-xl border bg-card p-4">
          <p className="text-sm text-muted-foreground">Khoảng thời gian</p>
          <p className="mt-1 font-medium">
            {formatDate(from)} – {formatDate(to)}
          </p>
        </div>
      </section>

      <section className="overflow-hidden rounded-xl border bg-card">
        <div className="flex items-center gap-3 border-b p-5">
          <CalendarDays aria-hidden className="size-5 text-primary" />
          <div>
            <h2 className="font-semibold">Lịch làm việc</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {loading
                ? "Đang lấy lịch trong khoảng ngày đã chọn..."
                : `${orderedShifts.length} ca trong 7 ngày đang xem`}
            </p>
          </div>
        </div>
        {loading ? (
          <p className="p-5" role="status">Đang tải lịch làm việc...</p>
        ) : error ? (
          <div
            className="flex flex-wrap items-center justify-between gap-3 p-5"
            role="alert"
          >
            <p>Không thể tải lịch trong khoảng ngày đã chọn.</p>
            <Button
              variant="outline"
              onClick={() => {
                setLoading(true);
                setError(false);
                setRevision((value) => value + 1);
              }}
            >
              Thử lại
            </Button>
          </div>
        ) : orderedShifts.length === 0 ? (
          <div className="p-8 text-center">
            <CalendarDays
              aria-hidden
              className="mx-auto size-8 text-muted-foreground"
            />
            <h3 className="mt-3 font-semibold">Chưa có ca được phân công</h3>
            <p className="mx-auto mt-1 max-w-lg text-sm text-muted-foreground">
              Không có ca nào từ {formatDate(from)} đến {formatDate(to)}. Hãy
              chọn khoảng ngày khác hoặc quay lại hôm nay để tra cứu.
            </p>
          </div>
        ) : (
          <ul className="divide-y">
            {orderedShifts.map((shift) => {
              const status = shiftStatus(shift.date, today);
              return (
                <li
                  key={shift.id}
                  className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="flex min-w-0 items-start gap-3">
                    <div className="mt-0.5 flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
                      <Clock3 aria-hidden className="size-5" />
                    </div>
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="font-medium">{formatDate(shift.date)}</h3>
                        <Badge
                          variant={status === "Hôm nay" ? "default" : "outline"}
                        >
                          {status}
                        </Badge>
                      </div>
                      <p className="mt-1 text-sm text-muted-foreground">
                        {shift.startTime} – {shift.endTime}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 pl-13 text-sm sm:pl-0">
                    <MapPin
                      aria-hidden
                      className="size-4 shrink-0 text-muted-foreground"
                    />
                    <div>
                      <p className="font-medium">{shift.roomName}</p>
                      <p className="text-muted-foreground">Phòng xét nghiệm</p>
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}
