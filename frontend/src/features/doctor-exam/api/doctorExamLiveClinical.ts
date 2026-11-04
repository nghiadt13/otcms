import { isApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import { EMPTY_EXAMINATION } from "../schema/examinationRules";
import type {
  Advice,
  AdviceInput,
  Diagnosis,
  Examination,
  ExaminationInput,
  ExaminationStatus,
} from "../types/doctorExam";

// Ghi nhận khám (UC37) và lời dặn (UC42) qua `clinical` của BE: GET/PUT /visits/{id}/examination,
// POST …/examination/completion, GET/PUT …/after-visit-advice. Chưa có bản ghi → 404 (MSG-41).

type ExaminationDto = {
  reasonForVisit: string | null;
  symptoms: string | null;
  examinationFindings: string | null;
  injuryBodySite: string | null;
  injurySide: string | null;
  injuryMechanism: string | null;
  status: ExaminationStatus;
  diagnoses: Diagnosis[] | null;
  version: number | null;
};

type AdviceDto = {
  homeCareAdvice: string | null;
  activityAdvice: string | null;
  rehabilitationAdvice: string | null;
  warningSigns: string | null;
  followUpRecommendation: string | null;
  examinationStatus: ExaminationStatus | null;
  updatedAt: string | null;
  version: number | null;
};

const root = (visitId: string) =>
  `/api/v1/visits/${encodeURIComponent(visitId)}` as const;

const text = (value: string | null | undefined) => value ?? "";

function toExamination(dto: ExaminationDto): Examination {
  return {
    reasonForVisit: text(dto.reasonForVisit),
    symptoms: text(dto.symptoms),
    examinationFindings: text(dto.examinationFindings),
    injuryBodySite: text(dto.injuryBodySite),
    injurySide: text(dto.injurySide),
    injuryMechanism: text(dto.injuryMechanism),
    diagnoses: dto.diagnoses ?? [],
    status: dto.status,
    version: dto.version,
  };
}

const notFound = (error: unknown) => isApiError(error) && error.status === 404;

export async function getExamination(
  visitId: string,
  signal?: AbortSignal,
): Promise<Examination> {
  try {
    return toExamination(
      await apiRequest<ExaminationDto>(`${root(visitId)}/examination`, {
        signal,
      }),
    );
  } catch (error) {
    if (notFound(error))
      return { ...EMPTY_EXAMINATION, status: null, version: null };
    throw error;
  }
}

export async function saveExamination(
  visitId: string,
  input: ExaminationInput,
  mode: "draft" | "complete",
): Promise<Examination> {
  const dto =
    mode === "draft"
      ? await apiRequest<ExaminationDto>(`${root(visitId)}/examination`, {
          method: "PUT",
          body: JSON.stringify(input),
        })
      : await apiRequest<ExaminationDto>(
          `${root(visitId)}/examination/completion`,
          { method: "POST", body: JSON.stringify(input) },
        );
  return toExamination(dto);
}

function toAdvice(dto: AdviceDto): Advice {
  return {
    homeCareAdvice: text(dto.homeCareAdvice),
    activityAdvice: text(dto.activityAdvice),
    rehabilitationAdvice: text(dto.rehabilitationAdvice),
    warningSigns: text(dto.warningSigns),
    followUpRecommendation: text(dto.followUpRecommendation),
    examinationStatus: dto.examinationStatus,
    updatedAt: dto.updatedAt,
    version: dto.version,
  };
}

export async function getAdvice(
  visitId: string,
  signal?: AbortSignal,
): Promise<Advice | null> {
  try {
    return toAdvice(
      await apiRequest<AdviceDto>(`${root(visitId)}/after-visit-advice`, {
        signal,
      }),
    );
  } catch (error) {
    if (notFound(error)) return null;
    throw error;
  }
}

export async function saveAdvice(
  visitId: string,
  input: AdviceInput,
): Promise<Advice> {
  return toAdvice(
    await apiRequest<AdviceDto>(`${root(visitId)}/after-visit-advice`, {
      method: "PUT",
      body: JSON.stringify(input),
    }),
  );
}
