import { apiRequest } from "@/lib/api/client";
import type { ResultSheet } from "../types/doctorExam";

type ResultSheetDto = {
  documentId: string;
  version: number;
  status: "ACTIVE" | "REPLACED";
  releasedAt: string | null;
};

export async function listResultSheets(visitId: string, signal?: AbortSignal): Promise<ResultSheet[]> {
  const result = await apiRequest<{ items: ResultSheetDto[] }>(
    `/api/v1/visits/${encodeURIComponent(visitId)}/result-sheets`,
    { signal },
  );
  return result.items.map((sheet) => ({
    documentId: sheet.documentId,
    version: sheet.version,
    status: sheet.status === "ACTIVE" ? "CURRENT" : "REPLACED",
    issuedAt: sheet.releasedAt,
  }));
}

export async function regenerateResultSheet(visitId: string): Promise<void> {
  await apiRequest(`/api/v1/visits/${encodeURIComponent(visitId)}/result-sheets/regeneration`, {
    method: "POST",
  });
}
