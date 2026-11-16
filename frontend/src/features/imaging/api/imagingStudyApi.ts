import { apiRequest } from "@/lib/api/client";
import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import {
  toQueryString,
  type PageQuery,
  type PageResponse,
} from "@/lib/api/pagination";
import type {
  ImagingModality,
  ImagingStudyListQuery,
  PatientImagingStudy,
  PatientImagingStudySummary,
} from "../types/imagingStudy";
import {
  IMAGING_FORBIDDEN_STUDY_ID,
  IMAGING_LIST_ERROR_PATIENT_ID,
  MOCK_STUDIES,
  type MockStudy,
} from "./imagingStudyMockData";

// Mẫu chỉ dùng trong test; ứng dụng thật ghép dữ liệu từ API kết quả lượt khám và lịch sử ảnh.
const USE_MOCK = process.env.NODE_ENV === "test";

type VisitSummary = {
  visitId: string;
  visitDate: string;
  status: string;
  serviceName: string;
  patient: PatientImagingStudySummary["patient"];
};

type VisitPage = {
  items: VisitSummary[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

type ImagingHistoryItem = {
  visitId: string;
  imagingType: string;
  studyRef: string | null;
  findingsText: string | null;
};

type ImagingHistoryPage = {
  items: ImagingHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
};

const latency = (signal?: AbortSignal) =>
  mockDelay(process.env.NODE_ENV === "test" ? 0 : 300, signal);

/** Chuẩn hóa dữ liệu mẫu dùng trong test. */
function toSummary(study: MockStudy): PatientImagingStudySummary {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars -- UID chỉ xuất hiện ở API chi tiết.
  const { studyUid, ...summary } = study;
  return {
    ...summary,
    finding: study.visitStatus === "COMPLETED" ? study.finding : null,
  };
}

async function getAllPages<T>(
  first: T[],
  totalPages: number,
  loadPage: (page: number) => Promise<T[]>,
): Promise<T[]> {
  if (totalPages <= 1) return first;
  const rest = await Promise.all(
    Array.from({ length: totalPages - 1 }, (_, index) => loadPage(index + 1)),
  );
  return first.concat(...rest);
}

function modalityFromImagingType(value: string): ImagingModality | null {
  const type = value.trim().toUpperCase();
  if (type.includes("MRI") || type === "MR") return "MRI";
  if (type.includes("CT")) return "CT";
  if (type.includes("ULTRASOUND") || type === "US") return "ULTRASOUND";
  if (type.includes("X-RAY") || type.includes("XRAY") || type === "XR") return "XRAY";
  return null;
}

async function loadLiveStudies(
  patientId: string | undefined,
  signal?: AbortSignal,
): Promise<PatientImagingStudySummary[]> {
  const visitQuery = (page: number) => {
    const qs = toQueryString({ patientId, page, size: 100 });
    return apiRequest<VisitPage>(`/api/v1/visits/results${qs}` as const, { signal });
  };
  const firstVisits = await visitQuery(0);
  const visits = await getAllPages(firstVisits.items, firstVisits.totalPages, async (page) =>
    (await visitQuery(page)).items,
  );
  const visitById = new Map(visits.map((visit) => [visit.visitId, visit]));
  const profileIds = [...new Set(visits.map((visit) => visit.patient.id))];

  const histories = await Promise.all(profileIds.map(async (profileId) => {
    const historyQuery = (page: number) => {
      const query = new URLSearchParams({ patientId: profileId, page: String(page), size: "100" });
      return apiRequest<ImagingHistoryPage>(`/api/v1/imaging-history?${query}` as const, { signal });
    };
    const firstHistory = await historyQuery(0);
    return getAllPages(firstHistory.items, Math.ceil(firstHistory.totalElements / firstHistory.size), async (page) =>
      (await historyQuery(page)).items,
    );
  }));

  const studies: PatientImagingStudySummary[] = [];
  for (const item of histories.flat()) {
    const visit = visitById.get(item.visitId);
    const modality = modalityFromImagingType(item.imagingType);
    if (!visit || !item.studyRef || !modality) continue;
    studies.push({
      id: item.studyRef,
      visitId: visit.visitId,
      visitStatus: visit.status === "COMPLETED" ? "COMPLETED" : "INCOMPLETE",
      serviceName: item.imagingType,
      modality,
      studyDate: visit.visitDate,
      patient: visit.patient,
      finding: item.findingsText,
    });
  }
  return studies.sort((left, right) => right.studyDate.localeCompare(left.studyDate));
}

/** Danh sách ảnh bệnh nhân ghép từ lịch sử lượt khám và lịch sử chẩn đoán đã phát hành. */
export async function listPatientStudies(
  query: ImagingStudyListQuery & PageQuery,
  signal?: AbortSignal,
): Promise<PageResponse<PatientImagingStudySummary>> {
  if (!USE_MOCK) {
    const items = await loadLiveStudies(query.patientId, signal);
    const page = query.page ?? 0;
    const size = query.size ?? 20;
    const totalItems = items.length;
    return {
      items: items.slice(page * size, (page + 1) * size),
      page,
      size,
      totalItems,
      totalPages: Math.ceil(totalItems / size),
    };
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

/** Tìm study trong các ảnh đã phát hành mà tài khoản có quyền xem. */
export async function getPatientStudy(
  id: string,
  signal?: AbortSignal,
): Promise<PatientImagingStudy> {
  if (!USE_MOCK) {
    const found = (await loadLiveStudies(undefined, signal)).find(
      (study) => study.id === id,
    );
    if (!found) throw new Error("Imaging study not found");
    return found;
  }
  await latency(signal);
  if (id === IMAGING_FORBIDDEN_STUDY_ID) mockFail(403, "MSG-40");
  const found = MOCK_STUDIES.find((study) => study.id === id);
  if (!found) mockFail(404, "MSG-41");
  return clone({
    ...toSummary(found),
    studyInstanceUid: found.studyUid,
  });
}
