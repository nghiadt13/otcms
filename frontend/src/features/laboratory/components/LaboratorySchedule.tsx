"use client";

import { useEffect, useState } from "react";
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

export function LaboratorySchedule() {
  const [from, setFrom] = useState(() => localDateKey(new Date()));
  const [shifts, setShifts] = useState<Shift[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [revision, setRevision] = useState(0);
  const to = addDays(from, 6);

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
    <section className="overflow-hidden rounded-xl border bg-card shadow-sm">
      <div className="flex flex-wrap items-end justify-between gap-4 border-b p-5">
        <div>
          <h2 className="font-semibold">Ca được phân công</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Từ {formatDate(from)} đến {formatDate(to)}
          </p>
        </div>
        <label className="space-y-1 text-sm">
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
      </div>
      {loading ? (
        <p className="p-5" role="status">Đang tải lịch làm việc...</p>
      ) : error ? (
        <div className="flex flex-wrap items-center justify-between gap-3 p-5" role="alert">
          <p>Không thể tải lịch làm việc.</p>
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
      ) : shifts.length === 0 ? (
        <p className="p-8 text-center text-sm text-muted-foreground">
          Không có ca làm việc được phân công trong khoảng ngày này.
        </p>
      ) : (
        <ul className="divide-y">
          {shifts.map((shift) => (
            <li key={shift.id} className="flex flex-wrap items-center gap-4 p-5">
              <div className="min-w-48 flex-1">
                <p className="font-medium">{formatDate(shift.date)}</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {shift.startTime}–{shift.endTime}
                </p>
              </div>
              <div className="text-sm">
                <p className="font-medium">{shift.roomName}</p>
                <p className="text-muted-foreground">Phòng xét nghiệm</p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
