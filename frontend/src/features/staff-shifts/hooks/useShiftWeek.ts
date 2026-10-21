"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { todayIsoDate } from "@/lib/format/formatDate";
import { messageFromError } from "@/lib/messages/appMessages";
import { notifyError } from "@/lib/messages/notify";
import { STAFF_ROLES, type StaffRole } from "@/lib/domain/clinicRoles";
import { listShifts } from "../api/shiftApi";
import type { Shift, ShiftPeriod, ShiftView } from "../types/shift";
import { getShiftRange, isIsoDate, stepAnchor } from "../utils/shiftWeekRange";

/** Từ 640px trở lên mới dùng được lưới tuần. */
export const GRID_MEDIA_QUERY = "(min-width: 640px)";

export interface ShiftFilters {
  staffId?: string;
  role?: StaffRole;
  roomId?: string;
}

type UrlPatch = Record<string, string | undefined>;

/**
 * Trạng thái màn Ca làm việc: kỳ, chế độ xem và bộ lọc nằm trên URL (chỉ mã/UUID),
 * dữ liệu tải theo kỳ đang xem. Fetch huỷ request cũ nên kết quả cũ không đè kết quả mới.
 * Dưới 640px mặc định Danh sách + Ngày khi URL chưa chọn `view`/`period`.
 */
export function useShiftWeek() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const wide = useMediaQuery(GRID_MEDIA_QUERY);
  const [today] = useState(() => todayIsoDate());

  const query = searchParams?.toString() ?? "";
  const params = useMemo(() => new URLSearchParams(query), [query]);

  const dateParam = params.get("date");
  const anchor = isIsoDate(dateParam) ? dateParam : today;
  const periodParam = params.get("period");
  const viewParam = params.get("view");
  const period: ShiftPeriod =
    periodParam === "week" || periodParam === "day"
      ? periodParam
      : wide
        ? "week"
        : "day";
  // Lưới chỉ có ở kỳ Tuần và từ 640px; còn lại luôn là Danh sách.
  const view: ShiftView =
    wide && period === "week" && viewParam !== "list" ? "grid" : "list";
  const staffIdParam = params.get("staffId") ?? undefined;
  const roleParam = params.get("role");
  const roomIdParam = params.get("roomId") ?? undefined;
  const role = STAFF_ROLES.find((r) => r === roleParam);
  const filters: ShiftFilters = useMemo(
    () => ({ staffId: staffIdParam, role, roomId: roomIdParam }),
    [staffIdParam, role, roomIdParam],
  );
  const range = useMemo(() => getShiftRange(anchor, period), [anchor, period]);

  const update = useCallback(
    (patch: UrlPatch) => {
      const next = new URLSearchParams(query);
      for (const [key, value] of Object.entries(patch)) {
        if (value) next.set(key, value);
        else next.delete(key);
      }
      const text = next.toString();
      router.replace(text ? `${pathname}?${text}` : pathname, {
        scroll: false,
      });
    },
    [query, router, pathname],
  );

  const [shifts, setShifts] = useState<Shift[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);

  const { from, to } = range;
  const { staffId, roomId } = filters;
  useEffect(() => {
    const controller = new AbortController();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- bắt đầu tải là hệ quả của đổi kỳ/bộ lọc
    setLoading(true);
    setError(null);
    setForbidden(false);
    listShifts({ from, to, staffId, role, roomId }, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        setShifts(result.items);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        if (isApiError(err) && err.status === 404 && err.code === "MSG-33") {
          // Bộ lọc trỏ tới nhân viên/phòng không còn hợp lệ: báo, bỏ lọc rồi tải lại.
          notifyError(err);
          update(staffId ? { staffId: undefined } : { roomId: undefined });
          return;
        }
        if (isApiError(err) && err.status === 403) setForbidden(true);
        setError(messageFromError(err));
        setLoading(false);
      });
    return () => controller.abort();
    // `update` đổi theo URL; chỉ cần chạy lại khi tham số truy vấn đổi.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [from, to, staffId, role, roomId, reloadToken]);

  return {
    today,
    anchor,
    period,
    view,
    wide,
    filters,
    range,
    shifts,
    loading,
    error,
    forbidden,
    reload: useCallback(() => setReloadToken((n) => n + 1), []),
    goPrev: () => update({ date: stepAnchor(anchor, period, -1) }),
    goNext: () => update({ date: stepAnchor(anchor, period, 1) }),
    goToday: () => update({ date: undefined }),
    /** Đổi độ rộng kỳ, giữ ngày neo. */
    setPeriod: (next: ShiftPeriod) => update({ period: next }),
    setView: (next: ShiftView) => update({ view: next }),
    setAnchor: (iso: string) =>
      update({ date: iso === today ? undefined : iso }),
    setFilters: (patch: ShiftFilters) =>
      update({
        ...("staffId" in patch ? { staffId: patch.staffId } : {}),
        ...("role" in patch ? { role: patch.role } : {}),
        ...("roomId" in patch ? { roomId: patch.roomId } : {}),
      }),
  };
}
