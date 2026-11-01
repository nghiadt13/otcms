import { apiDownload } from "@/lib/api/apiDownload";
import { apiRequest } from "@/lib/api/client";
import { ApiError, type MsgCode } from "@/lib/api/apiError";
import { toQueryString } from "@/lib/api/pagination";
import type {
  ReportExportFormat,
  ReportQuery,
  ReportResult,
  ReportSubjectOption,
} from "../types/report";
const USE_MOCK = false;
const BASE = "/api/v1/admin/reports" as const;
function rethrow(error: unknown): never {
  if (error instanceof ApiError && error.errorCode?.startsWith("MSG-"))
    throw new ApiError(error.status, { code: error.errorCode as MsgCode });
  throw error;
}
export async function listReportSubjects(
  signal?: AbortSignal,
): Promise<ReportSubjectOption[]> {
  if (USE_MOCK) throw new Error("Reporting requires a real backend");
  return (
    await apiRequest<{ items: ReportSubjectOption[] }>(`${BASE}/subjects`, {
      signal,
    }).catch(rethrow)
  ).items;
}
export function createReport(
  query: ReportQuery,
  signal?: AbortSignal,
): Promise<ReportResult> {
  return apiRequest<ReportResult>(BASE, {
    method: "POST",
    body: JSON.stringify(query),
    signal,
  }).catch(rethrow);
}
export function exportReport(
  query: ReportQuery,
  format: ReportExportFormat,
  signal?: AbortSignal,
): Promise<void> {
  return apiDownload(`${BASE}/export${toQueryString({ ...query, format })}`, {
    signal,
  }).catch(rethrow);
}
