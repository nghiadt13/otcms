import { apiRequest } from "@/lib/api/client";
import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import {
  toQueryString,
  type PageQuery,
  type PageResponse,
} from "@/lib/api/pagination";
import type {
  ExaminationResult,
  ResultVisitListQuery,
  ResultVisitSummary,
} from "../types/examinationResult";
import {
  RESULT_CHANGED_VISIT_ID,
  RESULT_FORBIDDEN_VISIT_ID,
  RESULT_LIST_ERROR_PATIENT_ID,
} from "./examinationResultMockData";
import { MOCK_VISITS } from "./examinationResultMockVisits";

// Lớp API giả cho kết quả khám. Chữ ký khớp REST của BE; khi nối BE đặt USE_MOCK = false.
const USE_MOCK = true;

const latency = (signal?: AbortSignal) =>
  mockDelay(process.env.NODE_ENV === "test" ? 0 : 300, signal);

function mockSummaries(patientId: string | undefined): ResultVisitSummary[] {
  return MOCK_VISITS.filter(
    ({ result }) => !patientId || result.patient.id === patientId,
  )
    .map(({ serviceName, result }) => ({
      visitId: result.visitId,
      visitDate: result.visitDate,
      status: result.status,
      serviceName,
      patient: result.patient,
    }))
    .sort((a, b) => b.visitDate.localeCompare(a.visitDate));
}

function mockVisitOrFail(visitId: string): ExaminationResult {
  if (visitId === RESULT_FORBIDDEN_VISIT_ID) mockFail(403, "MSG-40");
  if (visitId === RESULT_CHANGED_VISIT_ID) mockFail(409, "MSG-42");
  const found = MOCK_VISITS.find(({ result }) => result.visitId === visitId);
  if (!found) mockFail(404, "MSG-41");
  return clone(found.result);
}

/** GET /api/v1/visits/results: chỉ lượt Đã hoàn tất và Chưa hoàn tất, mới nhất trước. */
export async function listVisitResults(
  query: ResultVisitListQuery & PageQuery,
  signal?: AbortSignal,
): Promise<PageResponse<ResultVisitSummary>> {
  if (!USE_MOCK) {
    const qs = toQueryString({
      patientId: query.patientId,
      page: query.page,
      size: query.size,
    });
    return apiRequest<PageResponse<ResultVisitSummary>>(
      `/api/v1/visits/results${qs}` as const,
      { signal },
    );
  }
  await latency(signal);
  if (query.patientId === RESULT_LIST_ERROR_PATIENT_ID) mockFail(500, "MSG-04");
  return clone(paginate(mockSummaries(query.patientId), query));
}

/** GET /api/v1/visits/{visitId}/results: 403 MSG-40, 404 MSG-41, 409 MSG-42 khi không xem được. */
export async function getVisitResult(
  visitId: string,
  signal?: AbortSignal,
): Promise<ExaminationResult> {
  if (!USE_MOCK)
    return apiRequest<ExaminationResult>(
      `/api/v1/visits/${encodeURIComponent(visitId)}/results` as const,
      { signal },
    );
  await latency(signal);
  return mockVisitOrFail(visitId);
}
