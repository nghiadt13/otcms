"use client";

import { useCallback, useEffect, useState } from "react";
import { useAdminRemoteData } from "@/hooks/useAdminRemoteData";
import { getDoctorExamApi, type DoctorExamApi } from "../api/doctorExamApi";
import { doctorLiveApi } from "../api/doctorExamLiveApi";
import type { TodayPatient } from "../types/doctorExam";

// Các hook của khu bác sĩ đọc dữ liệu từ API thật và trả
// { data, error, loading, refresh } (hủy request cũ khi đổi tham số).

export function useDoctorDemoMode(): boolean {
  return false;
}

/** API thật dùng trong phần render, ví dụ để đọc `capabilities`. */
export function useDoctorExamApi(): DoctorExamApi {
  return doctorLiveApi;
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

export function usePatientAppointments(
  patientProfileId: string,
  query: { page: number; size: number; date: string; status: string },
) {
  return useAdminRemoteData(
    useCallback(
      (signal) =>
        getDoctorExamApi().listPatientAppointments(patientProfileId, query, signal),
      [patientProfileId, query.page, query.size, query.date, query.status],
    ),
  );
}

export function usePastVisits(
  currentVisitId: string,
  query: { page: number; size: number; date: string },
) {
  return useAdminRemoteData(
    useCallback(
      (signal) =>
        getDoctorExamApi().listPastVisitsPage(currentVisitId, query, signal),
      [currentVisitId, query.page, query.size, query.date],
    ),
  );
}
