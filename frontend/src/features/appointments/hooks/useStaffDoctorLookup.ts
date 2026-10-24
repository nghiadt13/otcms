"use client";

import { useCallback, useEffect, useState } from "react";
import { messageFromError } from "@/lib/messages/appMessages";
import { listStaffDoctors } from "../api/staffAppointmentApi";
import type { DoctorLookupItem } from "../types/appointment";

type LookupState = { doctors?: DoctorLookupItem[]; error?: string };

/** Bác sĩ cho bộ lọc của Lễ tân. Lỗi không chặn danh sách; có `reload` để thử lại. */
export function useStaffDoctorLookup() {
  const [state, setState] = useState<LookupState>({});
  const [token, setToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    listStaffDoctors()
      .then((result) => {
        if (!cancelled) setState({ doctors: result.items });
      })
      .catch((error: unknown) => {
        if (!cancelled) setState({ error: messageFromError(error) });
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  const reload = useCallback(() => {
    setState({});
    setToken((n) => n + 1);
  }, []);

  return {
    doctors: state.doctors,
    error: state.error ?? null,
    loading: !state.doctors && !state.error,
    reload,
  };
}
