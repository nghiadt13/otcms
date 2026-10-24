"use client";

import { useCallback, useMemo } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { todayIsoDate } from "@/lib/format/formatDate";
import {
  buildStaffAppointmentQuery,
  parseStaffAppointmentUrl,
  type StaffAppointmentUrlState,
} from "../schema/staffAppointmentUrlRules";

/**
 * URL là nguồn sự thật của ngày, bác sĩ và trạng thái. Đổi bộ lọc = replace, không thêm mục Back.
 * Thiếu ngày thì mặc định hôm nay (giờ VN).
 */
export function useStaffAppointmentUrlState() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const serialized = searchParams.toString();

  const state = useMemo(
    () => parseStaffAppointmentUrl(new URLSearchParams(serialized), todayIsoDate()),
    [serialized],
  );

  const update = useCallback(
    (patch: Partial<StaffAppointmentUrlState>) => {
      const next = { ...state, ...patch };
      router.replace(`${pathname}${buildStaffAppointmentQuery(next)}`);
    },
    [state, pathname, router],
  );

  return { state, update };
}
