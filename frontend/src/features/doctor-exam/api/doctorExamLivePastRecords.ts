import { apiRequest } from "@/lib/api/client";
import type {
  AdviceInput,
  Diagnosis,
  PastVisit,
  PastVisitPage,
} from "../types/doctorExam";

type HistoryPage = {
  currentVisitId: string;
  patientProfileId: string;
  page: number;
  size: number;
  total: number;
  items: Array<{
    visitId: string;
    arrivedAt: string;
    /** BE: CLOSED hoặc INCOMPLETE (UC40 gồm cả lượt chưa hoàn tất). */
    status: string;
    examination: { diagnoses: Diagnosis[] } | null;
    advice: Partial<AdviceInput> | null;
    prescription: { id: string; issuedAt: string; items?: Array<{
      medicine: string; dose: string; frequency: string; usage: string;
    }> } | null;
    documents: unknown[];
    imagingResults: Array<{
      imagingType: string | null;
      findingsText: string | null;
      studyRef?: string | null;
    }>;
    laboratoryResults?: Array<{ serviceName: string; values: Array<{
      indicator: string; value: string; unit: string | null; referenceRange: string | null;
      comment: string | null; abnormal: boolean | null;
    }> }>;
  }>;
};
const adviceFields: Array<[keyof AdviceInput, string]> = [
  ["homeCareAdvice", "Chăm sóc tại nhà"],
  ["activityAdvice", "Vận động và sinh hoạt"],
  ["rehabilitationAdvice", "Phục hồi chức năng"],
  ["warningSigns", "Dấu hiệu cần quay lại khám"],
  ["followUpRecommendation", "Đề xuất tái khám"],
];
export async function listPastVisits(
  currentVisitId: string,
  signal?: AbortSignal,
): Promise<PastVisit[]> {
  const path =
    `/api/v1/visits/${encodeURIComponent(currentVisitId)}/past-medical-records` as const;
  let page = await apiRequest<HistoryPage>(path, { signal });
  const items = [...page.items];
  while (items.length < page.total && page.items.length > 0 && page.size > 0) {
    page = await apiRequest<HistoryPage>(
      `${path}?page=${page.page + 1}&size=${page.size}`,
      { signal },
    );
    items.push(...page.items);
  }
  return items.map((item) => ({
    visitId: item.visitId,
    visitDate: item.arrivedAt,
    status: item.status === "INCOMPLETE" ? "INCOMPLETE" : "COMPLETED",
    primaryDiagnosis:
      item.examination?.diagnoses.find((d) => d.type === "PRIMARY")?.code ??
      null,
    advice: adviceFields
      .map(([field]) => item.advice?.[field]?.trim())
      .filter(Boolean)
      .join(" · ") || null,
    adviceSections: adviceFields
      .map(([field, label]) => ({
        label,
        text: item.advice?.[field]?.trim() ?? "",
      }))
      .filter((section) => section.text.length > 0),
    prescriptions: item.prescription != null ? 1 : 0,
    prescriptionItems: item.prescription?.items ?? [],
    imaging: item.imagingResults
      .map((result) =>
        [result.imagingType?.trim(), result.findingsText?.trim()]
          .filter(Boolean)
          .join(": "),
      )
      .filter(Boolean),
    imagingStudies: item.imagingResults,
    laboratory: (item.laboratoryResults ?? []).flatMap((result) =>
      result.values.map((value) => [result.serviceName, value.indicator, value.value,
        value.unit, value.referenceRange ? `Tham chiếu: ${value.referenceRange}` : null,
        value.abnormal === true ? "Bất thường" : null, value.comment].filter(Boolean).join(" · ")),
    ),
  }));
}

export async function listPastVisitsPage(
  currentVisitId: string,
  query: { page: number; size: number; date: string },
  signal?: AbortSignal,
): Promise<PastVisitPage> {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  });
  if (query.date) params.set("date", query.date);
  const path = `/api/v1/visits/${encodeURIComponent(currentVisitId)}/past-medical-records?${params}` as const;
  const response = await apiRequest<HistoryPage>(path, { signal });
  return {
    items: mapVisits(response.items),
    page: response.page,
    size: response.size,
    total: response.total,
  };
}

function mapVisits(items: HistoryPage["items"]): PastVisit[] {
  return items.map((item) => ({
    visitId: item.visitId,
    visitDate: item.arrivedAt,
    status: item.status === "INCOMPLETE" ? "INCOMPLETE" : "COMPLETED",
    primaryDiagnosis:
      item.examination?.diagnoses.find((d) => d.type === "PRIMARY")?.code ??
      null,
    advice: adviceFields
      .map(([field]) => item.advice?.[field]?.trim())
      .filter(Boolean)
      .join(" · ") || null,
    adviceSections: adviceFields
      .map(([field, label]) => ({
        label,
        text: item.advice?.[field]?.trim() ?? "",
      }))
      .filter((section) => section.text.length > 0),
    prescriptions: item.prescription != null ? 1 : 0,
    prescriptionItems: item.prescription?.items ?? [],
    imaging: item.imagingResults
      .map((result) =>
        [result.imagingType?.trim(), result.findingsText?.trim()]
          .filter(Boolean)
          .join(": "),
      )
      .filter(Boolean),
    imagingStudies: item.imagingResults,
    laboratory: (item.laboratoryResults ?? []).flatMap((result) =>
      result.values.map((value) => [result.serviceName, value.indicator, value.value,
        value.unit, value.referenceRange ? `Tham chiếu: ${value.referenceRange}` : null,
        value.abnormal === true ? "Bất thường" : null, value.comment].filter(Boolean).join(" · ")),
    ),
  }));
}
