"use client";

import { useState, type KeyboardEvent } from "react";
import { StatusBadge } from "@/components/admin/StatusBadge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  APPOINTMENT_STATUS_LABELS,
  APPOINTMENT_STATUS_TONE,
  EXAM_FEE_STATUS_LABELS,
  EXAM_FEE_STATUS_TONE,
} from "@/lib/domain/clinicVisit";
import { formatTime } from "@/lib/format/formatDate";
import { matchesAppointmentQuery } from "../schema/appointmentRowFilter";
import type { TodayAppointmentRow, TodayAppointmentsResponse, TodayTab } from "../types/checkIn";

interface TodayAppointmentListProps {
  tab: TodayTab;
  onTabChange: (tab: TodayTab) => void;
  data: TodayAppointmentsResponse | undefined;
  loading: boolean;
  error: unknown;
  onRetry: () => void;
  selectedAppointmentId: string | null;
  onSelect: (appointmentId: string) => void;
}

const TAB_LABELS: Record<TodayTab, string> = {
  BOOKED: "Chờ tiếp nhận",
  CHECKED_IN: "Đã tiếp nhận",
};

const EMPTY_TEXT: Record<TodayTab, string> = {
  BOOKED:
    "Đã tiếp nhận hết lịch hẹn hôm nay. Bệnh nhân vãng lai: tìm bằng số điện thoại và ngày sinh.",
  CHECKED_IN: "Chưa có bệnh nhân nào được tiếp nhận hôm nay.",
};

/** Lịch hẹn hôm nay: hai tab đếm số, ô lọc tại chỗ và danh sách nút-dòng (↑↓ để di chuyển). */
export function TodayAppointmentList(props: TodayAppointmentListProps) {
  const { tab, onTabChange, data, loading, error, onRetry, selectedAppointmentId, onSelect } = props;
  const [query, setQuery] = useState("");
  const rows = data?.items ?? [];
  const visible = rows.filter((row) => matchesAppointmentQuery(row, query));

  function moveFocus(event: KeyboardEvent<HTMLUListElement>) {
    if (event.key !== "ArrowDown" && event.key !== "ArrowUp") return;
    const buttons = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>("button[data-row]"));
    const current = buttons.indexOf(event.target as HTMLButtonElement);
    if (current === -1) return;
    event.preventDefault();
    const next =
      event.key === "ArrowDown" ? Math.min(current + 1, buttons.length - 1) : Math.max(current - 1, 0);
    buttons[next]?.focus();
  }

  return (
    <section aria-labelledby="today-appointments-title" className="space-y-3">
      <h2 id="today-appointments-title" className="text-lg font-semibold">
        Lịch hẹn hôm nay
      </h2>
      <Tabs value={tab} onValueChange={(value) => onTabChange(value as TodayTab)}>
        <TabsList>
          {(Object.keys(TAB_LABELS) as TodayTab[]).map((key) => (
            <TabsTrigger key={key} value={key}>
              {TAB_LABELS[key]}
              {data ? <span className="tabular-nums">{key === "BOOKED" ? data.counts.waiting : data.counts.checkedIn}</span> : null}
            </TabsTrigger>
          ))}
        </TabsList>
        <TabsContent value={tab} className="space-y-3">
          <Input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Lọc tên hoặc số điện thoại"
            aria-label="Lọc tên hoặc số điện thoại"
          />
          {loading ? (
            <div className="divide-y rounded-lg border" aria-busy="true">
              {Array.from({ length: 6 }, (_, index) => (
                <div key={index} className="flex min-h-11 items-center px-3">
                  <Skeleton className="h-4 w-full" />
                </div>
              ))}
            </div>
          ) : error ? (
            <div className="space-y-3 rounded-lg border p-4">
              <p role="alert" className="text-sm text-destructive">
                Không tải được lịch hẹn hôm nay. Kiểm tra kết nối rồi bấm Thử lại.
              </p>
              <Button variant="outline" size="sm" onClick={onRetry}>
                Thử lại
              </Button>
            </div>
          ) : visible.length === 0 ? (
            <p className="rounded-lg border px-4 py-6 text-center text-sm text-muted-foreground">
              {query.trim() ? `Không có lịch nào khớp "${query.trim()}".` : EMPTY_TEXT[tab]}
            </p>
          ) : (
            <ul role="list" onKeyDown={moveFocus} className="divide-y overflow-hidden rounded-lg border">
              {visible.map((row) => (
                <li key={row.appointmentId}>
                  <RowButton row={row} selected={row.appointmentId === selectedAppointmentId} onSelect={onSelect} />
                </li>
              ))}
            </ul>
          )}
        </TabsContent>
      </Tabs>
    </section>
  );
}

function RowButton({
  row,
  selected,
  onSelect,
}: {
  row: TodayAppointmentRow;
  selected: boolean;
  onSelect: (appointmentId: string) => void;
}) {
  return (
    <button
      type="button"
      data-row
      aria-current={selected ? "true" : undefined}
      onClick={() => onSelect(row.appointmentId)}
      className="flex min-h-11 w-full items-center gap-3 px-3 py-2 text-left text-sm outline-none hover:bg-muted focus-visible:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50 data-[selected=true]:bg-secondary"
      data-selected={selected ? "true" : undefined}
    >
      <span className="w-12 shrink-0 font-medium tabular-nums">{formatTime(row.startAt)}</span>
      <span className="min-w-0 flex-1">
        <span className="block truncate font-medium">{row.patientName}</span>
        <span className="block truncate text-xs text-muted-foreground">
          {row.patientCode}
          {row.isFollowUp ? " · Tái khám" : ""}
        </span>
      </span>
      <StatusBadge tone={EXAM_FEE_STATUS_TONE[row.examFeeStatus]}>
        {EXAM_FEE_STATUS_LABELS[row.examFeeStatus]}
      </StatusBadge>
      <StatusBadge tone={APPOINTMENT_STATUS_TONE[row.status]}>
        {APPOINTMENT_STATUS_LABELS[row.status]}
      </StatusBadge>
    </button>
  );
}
