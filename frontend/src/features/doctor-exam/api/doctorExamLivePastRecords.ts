import { apiRequest } from "@/lib/api/client";
import type { AdviceInput, Diagnosis, PastVisit } from "../types/doctorExam";

type HistoryPage = {
  currentVisitId: string;
  patientProfileId: string;
  page: number;
  size: number;
  total: number;
  items: Array<{
    visitId: string;
    arrivedAt: string;
    examination: { diagnoses: Diagnosis[] } | null;
    advice: Partial<AdviceInput> | null;
    prescription: { id: string; issuedAt: string } | null;
    documents: unknown[];
    imagingResults: Array<{
      imagingType: string | null;
      findingsText: string | null;
    }>;
  }>;
};
const adviceFields: Array<keyof AdviceInput> = [
  "homeCareAdvice",
  "activityAdvice",
  "rehabilitationAdvice",
  "warningSigns",
  "followUpRecommendation",
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
    status: "COMPLETED",
    primaryDiagnosis:
      item.examination?.diagnoses.find((d) => d.type === "PRIMARY")?.code ??
      null,
    advice:
      adviceFields
        .map((field) => item.advice?.[field]?.trim())
        .filter(Boolean)
        .join(" · ") || null,
    prescriptions: item.prescription != null ? 1 : 0,
    imaging: item.imagingResults
      .map((result) =>
        [result.imagingType?.trim(), result.findingsText?.trim()]
          .filter(Boolean)
          .join(": "),
      )
      .filter(Boolean),
    laboratory: [],
  }));
}
