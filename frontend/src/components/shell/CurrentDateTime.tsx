"use client";

import { useSyncExternalStore } from "react";

const dateFormat = new Intl.DateTimeFormat("vi-VN", {
  timeZone: "Asia/Ho_Chi_Minh",
  weekday: "long",
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
});
const timeFormat = new Intl.DateTimeFormat("vi-VN", {
  timeZone: "Asia/Ho_Chi_Minh",
  hour: "2-digit",
  minute: "2-digit",
});

// Đồng hồ đọc qua useSyncExternalStore: snapshot là số phút nên chỉ vẽ lại khi
// sang phút mới, và phía server trả null để không lệch giờ khi hydrate.
function subscribe(onChange: () => void) {
  const id = setInterval(onChange, 15_000);
  return () => clearInterval(id);
}
const getMinute = () => Math.floor(Date.now() / 60_000);
const getServerMinute = () => null;

/** Ngày giờ ở header khu nhân viên. `value` (ISO): hiện thời điểm cố định, ví dụ giờ mô phỏng của bản demo. */
export function CurrentDateTime({ value }: { value?: string } = {}) {
  const minute = useSyncExternalStore(subscribe, getMinute, getServerMinute);
  if (minute === null) return null;

  const now = value ? new Date(value) : new Date(minute * 60_000);
  return (
    <time
      dateTime={now.toISOString()}
      className="flex items-center gap-2 text-sm text-muted-foreground"
    >
      <span className="hidden capitalize md:inline">
        {dateFormat.format(now)}
      </span>
      <span className="hidden text-border md:inline">|</span>
      <span className="font-medium text-foreground tabular-nums">
        {timeFormat.format(now)}
      </span>
    </time>
  );
}
