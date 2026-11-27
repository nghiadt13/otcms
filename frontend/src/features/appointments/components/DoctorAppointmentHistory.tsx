"use client";

import { useEffect, useState } from "react";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge, type StatusTone } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  NativeSelect,
  NativeSelectOption,
} from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import { apiRequest } from "@/lib/api/client";
import {
  addDays,
  formatDate,
  formatDateTime,
  todayIsoDate,
} from "@/lib/format/formatDate";
import { messageFromError } from "@/lib/messages/appMessages";
import { DoctorAppointmentDetailSheet } from "./DoctorAppointmentDetailSheet";

type Appointment = {
  id: string;
  patientName: string | null;
  patientCode?: string | null;
  serviceName: string | null;
  doctorDisplayName: string | null;
  startTime: string;
  status: string;
};
type AppointmentPage = {
  items: Appointment[];
  totalElements: number;
  totalPages: number;
};

const STATUS_OPTIONS = [
  ["CONFIRMED", "Đã đặt"],
  ["CHECKED_IN", "Đã tiếp nhận"],
  ["IN_PROGRESS", "Đang khám"],
  ["COMPLETED", "Đã hoàn thành"],
  ["CANCELLED", "Đã hủy"],
  ["NO_SHOW", "Không đến"],
] as const;

const STATUS_VIEW: Record<string, { label: string; tone: StatusTone }> = {
  CONFIRMED: { label: "Đã đặt", tone: "info" },
  BOOKED: { label: "Đã đặt", tone: "info" },
  CHECKED_IN: { label: "Đã tiếp nhận", tone: "success" },
  IN_PROGRESS: { label: "Đang khám", tone: "success" },
  COMPLETED: { label: "Đã hoàn thành", tone: "success" },
  CANCELLED: { label: "Đã hủy", tone: "neutral" },
  NO_SHOW: { label: "Không đến", tone: "warning" },
};

function doctorName(value: string | null): string {
  if (!value) return "Chưa phân công bác sĩ";
  return /^BS\.?\s*/i.test(value) ? value : `BS. ${value}`;
}

export function DoctorAppointmentHistory() {
  const today = todayIsoDate();
  const [from, setFrom] = useState(addDays(today, -30));
  const [to, setTo] = useState(today);
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [loadedPage, setLoadedPage] = useState<{
    key: string;
    result: AppointmentPage | null;
    error: string;
  } | null>(null);
  const [reload, setReload] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const invalidRange = !from || !to || from > to || to >= addDays(from, 90);
  const requestKey = `${from}|${to}|${status}|${page}|${size}|${reload}`;
  const currentPage = loadedPage?.key === requestKey ? loadedPage : null;
  const result = currentPage?.result ?? null;
  const error = currentPage?.error ?? "";
  const loading = !invalidRange && currentPage === null;
  const totalPages = Math.max(1, result?.totalPages ?? 1);
  const firstResult = result?.totalElements ? page * size + 1 : 0;
  const lastResult = result
    ? Math.min((page + 1) * size, result.totalElements)
    : 0;

  useEffect(() => {
    if (invalidRange) return;

    const controller = new AbortController();
    const key = `${from}|${to}|${status}|${page}|${size}|${reload}`;
    const params = new URLSearchParams({
      from,
      to,
      page: String(page),
      size: String(size),
    });
    if (status) params.set("status", status);
    apiRequest<AppointmentPage>(`/api/v1/appointments?${params}`, {
      signal: controller.signal,
    })
      .then((data) => setLoadedPage({ key, result: data, error: "" }))
      .catch((cause: unknown) => {
        if (!controller.signal.aborted)
          setLoadedPage({
            key,
            result: null,
            error: messageFromError(cause),
          });
      });

    return () => controller.abort();
  }, [from, to, status, page, size, reload, invalidRange]);

  function clearFilters() {
    setFrom(addDays(today, -30));
    setTo(today);
    setStatus("");
    setPage(0);
    setSize(20);
    setReload((value) => value + 1);
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="Lịch sử lịch hẹn"
        description="Tra cứu lịch đã đặt, xem trạng thái và mở thông tin chi tiết từng lịch."
      />

      <section
        aria-label="Bộ lọc lịch hẹn"
        className="flex flex-wrap items-end gap-3 rounded-lg border bg-card p-4"
      >
        <label className="grid min-w-44 gap-2 text-sm font-medium">
          <span>Từ ngày</span>
          <Input
            type="date"
            value={from}
            onChange={(event) => {
              setFrom(event.target.value);
              setPage(0);
            }}
          />
        </label>
        <label className="grid min-w-44 gap-2 text-sm font-medium">
          <span>Đến ngày</span>
          <Input
            type="date"
            value={to}
            onChange={(event) => {
              setTo(event.target.value);
              setPage(0);
            }}
          />
        </label>
        <label className="grid min-w-48 gap-2 text-sm font-medium">
          <span>Trạng thái lịch hẹn</span>
          <NativeSelect
            aria-label="Lọc theo trạng thái lịch hẹn"
            className="w-full"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <NativeSelectOption value="">Tất cả trạng thái</NativeSelectOption>
            {STATUS_OPTIONS.map(([value, label]) => (
              <NativeSelectOption key={value} value={value}>
                {label}
              </NativeSelectOption>
            ))}
          </NativeSelect>
        </label>
        <label className="grid min-w-36 gap-2 text-sm font-medium">
          <span>Số lịch mỗi trang</span>
          <NativeSelect
            aria-label="Số lịch hẹn mỗi trang"
            className="w-full"
            value={String(size)}
            onChange={(event) => {
              setSize(Number(event.target.value));
              setPage(0);
            }}
          >
            {[10, 20, 50].map((count) => (
              <NativeSelectOption key={count} value={String(count)}>
                {count} lịch
              </NativeSelectOption>
            ))}
          </NativeSelect>
        </label>
        <Button type="button" variant="outline" onClick={clearFilters}>
          Đặt lại bộ lọc
        </Button>
      </section>

      {invalidRange ? (
        <p
          role="alert"
          className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive"
        >
          Khoảng ngày không hợp lệ. Hãy chọn ngày bắt đầu trước ngày kết thúc và
          giới hạn trong 90 ngày.
        </p>
      ) : error ? (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-destructive/30 bg-card p-4"
        >
          <p className="text-sm text-destructive">{error}</p>
          <Button
            variant="outline"
            onClick={() => setReload((value) => value + 1)}
          >
            Thử tải lại
          </Button>
        </div>
      ) : loading ? (
        <ul
          aria-label="Đang tải lịch hẹn"
          className="divide-y rounded-lg border bg-card"
        >
          {[0, 1, 2].map((item) => (
            <li
              key={item}
              className="flex flex-wrap items-center justify-between gap-4 p-4"
            >
              <div className="grid flex-1 gap-2">
                <Skeleton className="h-5 w-52" />
                <Skeleton className="h-4 w-80 max-w-full" />
              </div>
              <Skeleton className="h-10 w-24" />
            </li>
          ))}
        </ul>
      ) : !result || result.totalElements === 0 ? (
        <div className="rounded-lg border bg-card px-4 py-8 text-center">
          <h2 className="font-semibold">Không tìm thấy lịch hẹn</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {status
              ? "Thử chọn trạng thái khác hoặc mở rộng khoảng ngày."
              : "Thử mở rộng khoảng ngày để xem các lịch hẹn cũ hơn."}
          </p>
        </div>
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <p aria-live="polite" className="text-muted-foreground">
              {result.totalElements} lịch hẹn · Hiển thị {firstResult}–
              {lastResult}
            </p>
            <p className="text-muted-foreground">
              {from ? formatDate(`${from}T00:00:00+07:00`) : ""}
              {from && to ? " đến " : ""}
              {to ? formatDate(`${to}T00:00:00+07:00`) : ""}
            </p>
          </div>
          <ul className="divide-y rounded-lg border bg-card">
            {result.items.map((appointment) => {
              const appointmentStatus = STATUS_VIEW[appointment.status] ?? {
                label: "Chưa xác định",
                tone: "neutral" as const,
              };
              return (
                <li
                  className="grid gap-3 p-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
                  key={appointment.id}
                >
                  <div className="grid gap-2 sm:grid-cols-[12rem_minmax(0,1fr)] sm:items-start sm:gap-4">
                    <time
                      className="font-semibold tabular-nums"
                      dateTime={appointment.startTime}
                    >
                      {formatDateTime(appointment.startTime)}
                    </time>
                    <div className="min-w-0 space-y-1">
                      <p className="font-medium">
                        {appointment.patientName ?? "Chưa có tên bệnh nhân"}
                      </p>
                      <p className="text-sm text-muted-foreground">
                        {appointment.serviceName ?? "Chưa xác định dịch vụ"}
                        {` · ${doctorName(appointment.doctorDisplayName)}`}
                      </p>
                    </div>
                  </div>
                  <div className="flex flex-wrap items-center justify-between gap-3 sm:justify-end">
                    <StatusBadge tone={appointmentStatus.tone}>
                      {appointmentStatus.label}
                    </StatusBadge>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setSelectedId(appointment.id)}
                    >
                      Xem chi tiết
                    </Button>
                  </div>
                </li>
              );
            })}
          </ul>
          <nav
            aria-label="Phân trang lịch hẹn"
            className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-card px-4 py-3"
          >
            <p className="text-sm text-muted-foreground">
              Trang {page + 1} / {totalPages}
            </p>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                disabled={page === 0 || loading}
                onClick={() => setPage((value) => Math.max(0, value - 1))}
              >
                Trước
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={page + 1 >= totalPages || loading}
                onClick={() =>
                  setPage((value) => Math.min(totalPages - 1, value + 1))
                }
              >
                Tiếp
              </Button>
            </div>
          </nav>
        </>
      )}

      <DoctorAppointmentDetailSheet
        appointmentId={selectedId}
        onClose={() => setSelectedId(null)}
      />
    </div>
  );
}
