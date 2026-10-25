import { apiDownload } from "@/lib/api/apiDownload";
import { apiRequest } from "@/lib/api/client";
import { mockDelay, mockFail } from "@/lib/api/mockServer";
import { toQueryString } from "@/lib/api/pagination";
import type {
  ReportExportFormat,
  ReportQuery,
  ReportResult,
  ReportSubjectOption,
} from "../types/report";
import {
  buildMockReport,
  MOCK_EXCLUDED_SUBJECTS,
  MOCK_FAILING_TO_DATE,
  MOCK_SUBJECTS,
} from "./reportMockData";
import { buildMockExportFile, saveMockBlob } from "./reportMockExport";

// Chuyển sang BE thật: đổi USE_MOCK thành false (mọi nhánh thật đã viết sẵn bên dưới).
const USE_MOCK = true;
const BASE = "/api/v1/admin/reports";
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Giả lập kiểm tra của BE: 400 MSG-72, 403 MSG-50, 500 MSG-04 (xem reportMockData). */
function assertMockQuery(query: ReportQuery): void {
  const subjectOk = MOCK_SUBJECTS.some((s) => s.code === query.subject);
  const fieldErrors = [
    ...(subjectOk ? [] : [{ field: "subject", code: "MSG-72" as const }]),
    ...(ISO_DATE.test(query.from) ? [] : [{ field: "from", code: "MSG-72" as const }]),
    ...(ISO_DATE.test(query.to) ? [] : [{ field: "to", code: "MSG-72" as const }]),
  ];
  if (fieldErrors.length === 0 && query.from > query.to)
    fieldErrors.push({ field: "to", code: "MSG-72" });
  if (fieldErrors.length > 0) mockFail(400, "MSG-72", undefined, fieldErrors);
  if (MOCK_EXCLUDED_SUBJECTS.includes(query.subject)) mockFail(403, "MSG-50");
  if (query.to === MOCK_FAILING_TO_DATE) mockFail(500, "MSG-04");
}

/** GET /api/v1/admin/reports/subjects: chỉ chủ đề trong quyền. */
export async function listReportSubjects(
  signal?: AbortSignal,
): Promise<ReportSubjectOption[]> {
  if (!USE_MOCK) {
    const res = await apiRequest<{ items: ReportSubjectOption[] }>(`${BASE}/subjects`, { signal });
    return res.items;
  }
  await mockDelay(300, signal);
  return MOCK_SUBJECTS.filter((s) => !MOCK_EXCLUDED_SUBJECTS.includes(s.code)).map(
    (s) => structuredClone(s),
  );
}

/** POST /api/v1/admin/reports: tính báo cáo, không lưu (200). */
export async function createReport(
  query: ReportQuery,
  signal?: AbortSignal,
): Promise<ReportResult> {
  if (!USE_MOCK)
    return apiRequest<ReportResult>(BASE, {
      method: "POST",
      body: JSON.stringify(query),
      signal,
    });
  await mockDelay(700, signal);
  assertMockQuery(query);
  return buildMockReport(query);
}

function exportPath(query: ReportQuery, format: ReportExportFormat) {
  const qs = toQueryString({ ...query, format });
  return `${BASE}/export${qs}` as const;
}

/** GET /api/v1/admin/reports/export: tải tệp đúng chủ đề và kỳ đã tạo. */
export async function exportReport(
  query: ReportQuery,
  format: ReportExportFormat,
  signal?: AbortSignal,
): Promise<void> {
  if (!USE_MOCK) return apiDownload(exportPath(query, format), { signal });
  await mockDelay(500, signal);
  assertMockQuery(query);
  const label = MOCK_SUBJECTS.find((s) => s.code === query.subject)?.label ?? "bao-cao";
  // Mock bỏ qua `format`: luôn CSV (chưa có thư viện xlsx/pdf).
  saveMockBlob(buildMockExportFile(buildMockReport(query), label));
}
