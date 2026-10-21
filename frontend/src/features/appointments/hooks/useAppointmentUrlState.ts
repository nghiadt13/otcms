"use client";

import { useCallback, useMemo } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  buildAppointmentQuery,
  parseAppointmentUrl,
} from "../schema/appointmentUrlRules";
import type { AppointmentUrlState } from "../types/appointment";

/**
 * URL là nguồn sự thật của tab và bộ lọc. Đổi tab = push (Back về tab trước);
 * đổi bộ lọc = replace. Trường nào không ghi thì dùng mặc định.
 */
export function useAppointmentUrlState() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const serialized = searchParams.toString();

  const state = useMemo(
    () => parseAppointmentUrl(new URLSearchParams(serialized)),
    [serialized],
  );

  const update = useCallback(
    (patch: Partial<AppointmentUrlState>, mode: "push" | "replace" = "replace") => {
      const next = { ...state, ...patch };
      const url = `${pathname}${buildAppointmentQuery(next)}`;
      if (mode === "push") router.push(url);
      else router.replace(url);
    },
    [state, pathname, router],
  );

  return { state, update };
}
