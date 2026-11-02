"use client";

import { useCallback, useEffect, useState } from "react";
import { messageFromError } from "@/lib/messages/appMessages";
import { notifyError } from "@/lib/messages/notify";
import { listMyAppointments } from "../api/patientAppointmentSource";
import type {
  AppointmentWhen,
  PatientAppointment,
} from "../types/patientAppointment";

/** Cỡ trang của danh sách khu bệnh nhân. "Xem thêm" tải trang kế và nối vào cuối. */
export const PATIENT_APPOINTMENT_PAGE_SIZE = 10;

interface ListState {
  items: PatientAppointment[];
  /** Trang đã tải được (0-based). Chỉ tăng khi tải thành công. */
  page: number;
  totalPages: number;
  status: "loading" | "ready" | "error";
  /** Câu lỗi khi tải trang đầu; lỗi tải thêm chỉ hiện toast. */
  error: string | null;
  loadingMore: boolean;
}

const INITIAL_STATE: ListState = {
  items: [],
  page: 0,
  totalPages: 0,
  status: "loading",
  error: null,
  loadingMore: false,
};

/**
 * Danh sách lịch hẹn theo trang: trang đầu khi mount, "Xem thêm" nối trang kế, thử lại tải lại từ
 * đầu. Một phiên bản hook cho một bộ lọc: đổi tab hoặc người khám thì màn đặt `key` lại.
 */
export function usePatientAppointmentList(
  when: AppointmentWhen,
  patientId?: string,
) {
  const [state, setState] = useState<ListState>(INITIAL_STATE);
  // Mỗi lần tải (đầu, thử lại, xem thêm) là một object mới để effect chạy đúng một lần.
  const [request, setRequest] = useState<{ page: number }>({ page: 0 });

  useEffect(() => {
    const controller = new AbortController();
    const { page } = request;
    listMyAppointments(
      { when, patientId, page, size: PATIENT_APPOINTMENT_PAGE_SIZE },
      controller.signal,
    )
      .then((result) => {
        if (controller.signal.aborted) return;
        setState((current) => ({
          items:
            page === 0 ? result.items : [...current.items, ...result.items],
          page: result.page,
          totalPages: result.totalPages,
          status: "ready",
          error: null,
          loadingMore: false,
        }));
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        if (page === 0) {
          setState({
            ...INITIAL_STATE,
            status: "error",
            error: messageFromError(error),
          });
          return;
        }
        // Trang kế lỗi: giữ các dòng đã có, không đẩy số trang đi.
        setState((current) => ({ ...current, loadingMore: false }));
        notifyError(error);
      });
    return () => controller.abort();
  }, [when, patientId, request]);

  const hasMore = state.status === "ready" && state.page + 1 < state.totalPages;

  const loadMore = useCallback(() => {
    if (state.loadingMore || !hasMore) return;
    setState((current) => ({ ...current, loadingMore: true }));
    setRequest({ page: state.page + 1 });
  }, [state.loadingMore, state.page, hasMore]);

  const reload = useCallback(() => {
    setState(INITIAL_STATE);
    setRequest({ page: 0 });
  }, []);

  useEffect(() => {
    const refreshOnFocus = () => {
      if (document.visibilityState === "visible") reload();
    };
    window.addEventListener("focus", refreshOnFocus);
    document.addEventListener("visibilitychange", refreshOnFocus);
    return () => {
      window.removeEventListener("focus", refreshOnFocus);
      document.removeEventListener("visibilitychange", refreshOnFocus);
    };
  }, [reload]);

  return {
    items: state.items,
    loading: state.status === "loading",
    error: state.status === "error" ? state.error : null,
    loadingMore: state.loadingMore,
    hasMore,
    loadMore,
    reload,
  };
}
