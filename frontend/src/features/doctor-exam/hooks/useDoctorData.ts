"use client";

import { useCallback, useSyncExternalStore } from "react";
import { useAdminRemoteData } from "@/hooks/useAdminRemoteData";
import { getDoctorExamApi, type DoctorExamApi } from "../api/doctorExamApi";
import { doctorDemoApi } from "../api/doctorExamDemoApi";
import { doctorLiveApi } from "../api/doctorExamLiveApi";
import {
  isDoctorDemoMode,
  subscribeDoctorDemoMode,
} from "../api/doctorDemoMode";

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

export function useTodayPatients() {
  return useAdminRemoteData(
    useCallback((signal) => getDoctorExamApi().listTodayPatients(signal), []),
  );
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

export function usePastVisits(patientProfileId: string) {
  return useAdminRemoteData(
    useCallback(
      (signal) => getDoctorExamApi().listPastVisits(patientProfileId, signal),
      [patientProfileId],
    ),
  );
}
