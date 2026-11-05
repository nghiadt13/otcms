import { apiRequest } from "@/lib/api/client";

export type PatientImagingHistoryItem = {
  requestId: string;
  visitId: string;
  imagingType: string;
  status: string;
  studyRef: string | null;
  findingsVersion: number | null;
  findingsText: string | null;
  reportId: string | null;
};

export type PatientImagingHistoryPage = {
  items: PatientImagingHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
};

export function listPatientImagingHistory(
  patientId: string,
  page = 0,
  size = 20,
  signal?: AbortSignal,
): Promise<PatientImagingHistoryPage> {
  const query = new URLSearchParams({
    patientId,
    page: String(page),
    size: String(size),
  });
  return apiRequest<PatientImagingHistoryPage>(
    `/api/v1/imaging-history?${query}` as const,
    { signal },
  );
}
