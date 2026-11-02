import { apiRequest } from "@/lib/api/client";
import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import {
  toQueryString,
  type PageQuery,
  type PageResponse,
} from "@/lib/api/pagination";
import type {
  ImagingStudyListQuery,
  PatientImagingStudy,
  PatientImagingStudySummary,
} from "../types/imagingStudy";
import {
  IMAGING_FORBIDDEN_STUDY_ID,
  IMAGING_LIST_ERROR_PATIENT_ID,
  MOCK_STUDIES,
  mockViewerUrl,
  type MockStudy,
} from "./imagingStudyMockData";

// Lớp API giả cho ảnh chụp của bệnh nhân. Chữ ký khớp REST của BE; khi nối BE đặt USE_MOCK = false.
const USE_MOCK = true;

const latency = (signal?: AbortSignal) =>
  mockDelay(process.env.NODE_ENV === "test" ? 0 : 300, signal);

/** Kết luận chỉ hiện khi lượt Đã hoàn tất (BE quyết định, FE không tự suy). */
function toSummary(study: MockStudy): PatientImagingStudySummary {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars -- studyUid chỉ dùng dựng viewerUrl, không trả ra ngoài
  const { studyUid, ...summary } = study;
  return {
    ...summary,
    finding: study.visitStatus === "COMPLETED" ? study.finding : null,
  };
}

/** GET /api/v1/imaging-studies: study của lượt Đã hoàn tất và Chưa hoàn tất, mới nhất trước. */
export async function listPatientStudies(
  query: ImagingStudyListQuery & PageQuery,
  signal?: AbortSignal,
): Promise<PageResponse<PatientImagingStudySummary>> {
  if (!USE_MOCK) {
    const qs = toQueryString({
      patientId: query.patientId,
      page: query.page,
      size: query.size,
    });
    return apiRequest<PageResponse<PatientImagingStudySummary>>(
      `/api/v1/imaging-studies${qs}` as const,
      { signal },
    );
  }
  await latency(signal);
  if (query.patientId === IMAGING_LIST_ERROR_PATIENT_ID)
    mockFail(500, "MSG-04");
  const items = MOCK_STUDIES.filter(
    (study) => !query.patientId || study.patient.id === query.patientId,
  )
    .map(toSummary)
    .sort((a, b) => b.studyDate.localeCompare(a.studyDate));
  return clone(paginate(items, query));
}

/** GET /api/v1/imaging-studies/{id}: `viewerUrl` ngắn hạn do BE cấp; 403 MSG-40, 404 MSG-41. */
export async function getPatientStudy(
  id: string,
  signal?: AbortSignal,
): Promise<PatientImagingStudy> {
  if (!USE_MOCK)
    return apiRequest<PatientImagingStudy>(
      `/api/v1/imaging-studies/${encodeURIComponent(id)}` as const,
      { signal },
    );
  await latency(signal);
  if (id === IMAGING_FORBIDDEN_STUDY_ID) mockFail(403, "MSG-40");
  const found = MOCK_STUDIES.find((study) => study.id === id);
  if (!found) mockFail(404, "MSG-41");
  return clone({
    ...toSummary(found),
    viewerUrl: mockViewerUrl(found.studyUid),
  });
}
