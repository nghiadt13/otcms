"use client";

import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import { useAdminRemoteData } from "@/hooks/useAdminRemoteData";
import { getDoctorExamApi, type DoctorExamApi } from "../api/doctorExamApi";
import { doctorDemoApi } from "../api/doctorExamDemoApi";
import { doctorLiveApi } from "../api/doctorExamLiveApi";
import {
  isDoctorDemoMode,
  subscribeDoctorDemoMode,
} from "../api/doctorDemoMode";
import type { TodayPatient } from "../types/doctorExam";

// Các hook đọc dữ liệu của khu bác sĩ. Mỗi hook chọn bản API (live/demo) ở mỗi lần gọi và
// trả { data, error, loading, refresh } như useAdminRemoteData (hủy request cũ khi đổi tham số).

export function useDoctorDemoMode(): boolean {
  return useSyncExternalStore(
    subscribeDoctorDemoMode,
    isDoctorDemoMode,
    () => false,
  );
}

/**
 * Bản API cho phần render (ví dụ đọc `capabilities`). Lấy chế độ qua useSyncExternalStore để lần
 * render hydrate khớp với server (server không có localStorage nên luôn là live).
 */
export function useDoctorExamApi(): DoctorExamApi {
  return useDoctorDemoMode() ? doctorDemoApi : doctorLiveApi;
}

/** Chu kỳ tự tải lại danh sách "Bệnh nhân hôm nay" (plan 261111-0208 mục 1). */
const TODAY_PATIENTS_REFRESH_MS = 15_000;

/**
 * Danh sách hôm nay tự tải lại theo chu kỳ và khi bấm làm mới. Khác `useAdminRemoteData`, lần tải lại
 * giữ dữ liệu cũ cho tới khi có kết quả mới, nên bảng không chớp skeleton mỗi 15 giây.
 */
export function useTodayPatients() {
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<{
    data?: TodayPatient[];
    error?: unknown;
    settled: boolean;
  }>({ settled: false });

  useEffect(() => {
    const controller = new AbortController();
    getDoctorExamApi()
      .listTodayPatients(controller.signal)
      .then(
        (data) => {
          if (!controller.signal.aborted) setResult({ data, settled: true });
        },
        (error: unknown) => {
          if (!controller.signal.aborted)
            setResult((cur) => ({ data: cur.data, error, settled: true }));
        },
      );
    return () => controller.abort();
  }, [reload]);

  useEffect(() => {
    const timer = window.setInterval(
      () => setReload((n) => n + 1),
      TODAY_PATIENTS_REFRESH_MS,
    );
    return () => window.clearInterval(timer);
  }, []);

  const refresh = useCallback(() => setReload((n) => n + 1), []);
  return {
    data: result.data,
    error: result.error,
    loading: !result.settled,
    refresh,
  };
}

export function useExamination(visitId: string) {
  return useAdminRemoteData(
    useCallback(
      (signal) => getDoctorExamApi().getExamination(visitId, signal),
      [visitId],
    ),
  );
}

export function useAdvice(visitId: string) {
  return useAdminRemoteData(
    useCallback(
      (signal) => getDoctorExamApi().getAdvice(visitId, signal),
      [visitId],
    ),
  );
}

export function useCatalog() {
  return useAdminRemoteData(
    useCallback((signal) => getDoctorExamApi().listCatalog(signal), []),
  );
}

export function useOrders(visitId: string) {
  return useAdminRemoteData(
    useCallback(
      (signal) => getDoctorExamApi().listOrders(visitId, signal),
      [visitId],
    ),
  );
}

export function useLabResults(visitId: string) {
  return useAdminRemoteData(
    useCallback(
      (signal) => getDoctorExamApi().listLabResults(visitId, signal),
      [visitId],
    ),
  );
}

export function usePrescription(visitId: string) {
  return useAdminRemoteData(
    useCallback(
      (signal) => getDoctorExamApi().getPrescription(visitId, signal),
      [visitId],
    ),
  );
}

export function useResultSheets(visitId: string) {
  return useAdminRemoteData(
    useCallback(
      (signal) => getDoctorExamApi().listResultSheets(visitId, signal),
      [visitId],
    ),
  );
}

export function useMedicalHistory(patientProfileId: string) {
  return useAdminRemoteData(
    useCallback(
      (signal) =>
        getDoctorExamApi().getMedicalHistory(patientProfileId, signal),
      [patientProfileId],
    ),
  );
}

export function usePatientAppointments(patientProfileId: string) {
  return useAdminRemoteData(
    useCallback(
      (signal) =>
        getDoctorExamApi().listPatientAppointments(patientProfileId, signal),
      [patientProfileId],
    ),
  );
}

export function usePastVisits(currentVisitId: string) {
  return useAdminRemoteData(
    useCallback(
      (signal) => getDoctorExamApi().listPastVisits(currentVisitId, signal),
      [currentVisitId],
    ),
  );
}
