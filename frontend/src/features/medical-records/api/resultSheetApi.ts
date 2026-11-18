import { apiRequest } from "@/lib/api/client";

export interface PatientResultSheet {
  documentId: string;
  version: number;
  status: "ACTIVE" | "REPLACED";
  releasedAt: string;
}

/** Danh sách phiếu đã phát hành của lượt; backend kiểm tra quyền hồ sơ. */
export async function listPatientResultSheets(
  visitId: string,
  signal?: AbortSignal,
): Promise<PatientResultSheet[]> {
  const response = await apiRequest<{ items: PatientResultSheet[] }>(
    `/api/v1/visits/${encodeURIComponent(visitId)}/result-sheets`,
    { signal },
  );
  return response.items;
}
