"use client";

import { useEffect, useMemo, useState } from "react";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { usePagedList } from "@/hooks/usePagedList";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import type { MsgCode } from "@/lib/api/apiError";
import { listActivityLogs } from "../api/activityLogApi";
import { validateActivityLogDateRange } from "../schema/activityLogFilterRules";
import type { ActivityLogFilters, ActivityLogItem } from "../types/activityLog";

const PAGE_SIZE = 20;

/** Mặc định 7 ngày tính đến hôm nay (hôm nay − 6 .. hôm nay). */
function defaultFilters(): ActivityLogFilters {
  const today = todayIsoDate();
  return {
    from: addDays(today, -6),
    to: today,
    action: null,
    record: "",
    actor: "",
  };
}

const sameFilters = (a: ActivityLogFilters, b: ActivityLogFilters) =>
  a.from === b.from &&
  a.to === b.to &&
  a.action === b.action &&
  a.record === b.record &&
  a.actor === b.actor;

/**
 * Trạng thái màn danh sách. `draft` là giá trị người dùng đang nhập; chỉ khi khoảng ngày
 * hợp lệ và ô chữ đã đứng yên 250ms mới đẩy sang bộ lọc thật (đổi lọc → về trang đầu).
 * Khoảng ngày sai thì không gọi API và giữ kết quả cũ.
 */
export function useActivityLogList() {
  const [draft, setDraft] = useState<ActivityLogFilters>(defaultFilters);
  const record = useDebouncedValue(draft.record);
  const actor = useDebouncedValue(draft.actor);
  const rangeError: MsgCode | null = validateActivityLogDateRange(
    draft.from,
    draft.to,
  );

  const list = usePagedList<
    ActivityLogItem,
    ActivityLogFilters
  >((query, signal) => listActivityLogs(query, signal), draft, PAGE_SIZE);
  const { filters, setFilters } = list;

  const applied = useMemo<ActivityLogFilters>(
    () => ({ ...draft, record, actor }),
    [draft, record, actor],
  );

  useEffect(() => {
    if (rangeError || sameFilters(applied, filters)) return;
    setFilters(applied);
  }, [applied, filters, rangeError, setFilters]);

  return {
    ...list,
    draft,
    updateDraft: (patch: Partial<ActivityLogFilters>) =>
      setDraft((current) => ({ ...current, ...patch })),
    rangeError,
  };
}
