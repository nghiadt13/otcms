"use client";

import { useCallback, useState } from "react";
import { formatAppMessage } from "@/lib/messages/appMessages";
import {
  DEFAULT_PRESET,
  quickPeriodRange,
  todayInVietnam,
  validateDashboardPeriod,
  type QuickPreset,
} from "../lib/dashboardPeriodRules";
import type { DashboardPeriod } from "../types/dashboardOverview";

/**
 * Trạng thái chọn kỳ. Kỳ nhanh áp dụng ngay; sửa ô ngày chỉ đổi bản nháp
 * (bỏ chọn kỳ nhanh) và chỉ áp dụng khi bấm Xem.
 */
export function useDashboardPeriodFilter() {
  const [initialRange] = useState(() =>
    quickPeriodRange(DEFAULT_PRESET, todayInVietnam()),
  );
  const [preset, setPreset] = useState<QuickPreset | null>(DEFAULT_PRESET);
  const [draft, setDraftState] = useState<DashboardPeriod>(initialRange);
  const [applied, setApplied] = useState<DashboardPeriod>(initialRange);
  const [error, setError] = useState<string | null>(null);

  const selectPreset = useCallback((next: QuickPreset) => {
    const range = quickPeriodRange(next, todayInVietnam());
    setPreset(next);
    setDraftState(range);
    setApplied(range);
    setError(null);
  }, []);

  const setDraft = useCallback((range: DashboardPeriod) => {
    setPreset(null);
    setDraftState(range);
    setError(null);
  }, []);

  /** Kiểm bản nháp; hợp lệ thì áp dụng, không hợp lệ thì hiện lỗi và không tải. */
  const apply = useCallback(() => {
    const code = validateDashboardPeriod(draft);
    if (code) {
      setError(formatAppMessage(code));
      return;
    }
    setError(null);
    setApplied((current) =>
      current.from === draft.from && current.to === draft.to ? current : draft,
    );
  }, [draft]);

  return { preset, draft, applied, error, selectPreset, setDraft, apply };
}
