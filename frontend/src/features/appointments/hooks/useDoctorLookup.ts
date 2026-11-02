"use client";

import { useCallback, useEffect, useState } from "react";
import { messageFromError } from "@/lib/messages/appMessages";
import { lookupDoctors } from "../api/appointmentApi";
import type { DoctorLookupItem } from "../types/appointment";

type LookupState = { doctors?: DoctorLookupItem[]; error?: string };

/** Danh sách bác sĩ cho combobox. Lỗi không chặn tab; có `reload` để thử lại. */
export function useDoctorLookup(bookable?: boolean) {
  const [state, setState] = useState<LookupState>({});
  const [token, setToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    lookupDoctors(bookable === undefined ? {} : { bookable })
      .then((result) => {
        if (!cancelled) setState({ doctors: result.items });
      })
      .catch((error: unknown) => {
        if (!cancelled) setState({ error: messageFromError(error) });
      });
    return () => {
      cancelled = true;
    };
  }, [bookable, token]);

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
