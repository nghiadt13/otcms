import { apiRequest } from "@/lib/api/client";
import type {
  Advice,
  AmendChanges,
  Examination,
  PrescriptionLine,
  ResultSheet,
} from "../types/doctorExam";
import {
  examinationPayload,
  toAdvice,
  toExamination,
  type AdviceDto,
  type ExaminationDto,
} from "./doctorExamLiveClinical";

type ResultSheetDto = {
  documentId: string;
  version: number;
  status: "ACTIVE" | "REPLACED";
  releasedAt: string | null;
};

type AmendableResultDto = {
  examination: ExaminationDto;
  advice: AdviceDto;
  prescriptionItems: Array<PrescriptionLine & { lineNumber: number }>;
  resultSheetVersion: number | null;
};

const root = (visitId: string) =>
  `/api/v1/visits/${encodeURIComponent(visitId)}` as const;

export async function listResultSheets(
  visitId: string,
  signal?: AbortSignal,
): Promise<ResultSheet[]> {
  const result = await apiRequest<{ items: ResultSheetDto[] }>(
    `${root(visitId)}/result-sheets`,
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
  await apiRequest(`${root(visitId)}/result-sheets/regeneration`, {
    method: "POST",
  });
}

/** UC38 bước 2: giá trị hiện hành của lượt đã hoàn tất (GET khám/lời dặn thường không dùng được sau khi đóng lượt). */
export async function getAmendableResult(
  visitId: string,
  signal?: AbortSignal,
): Promise<{
  examination: Examination;
  advice: Advice;
  prescriptionItems: PrescriptionLine[];
  resultSheetVersion: number | null;
}> {
  const dto = await apiRequest<AmendableResultDto>(
    `${root(visitId)}/amendments/current`,
    { signal },
  );
  return {
    examination: toExamination(dto.examination),
    advice: toAdvice(dto.advice),
    prescriptionItems: [...dto.prescriptionItems]
      .sort((a, b) => a.lineNumber - b.lineNumber)
      .map(({ medicine, dose, frequency, usage }) => ({
        medicine,
        dose,
        frequency,
        usage,
      })),
    resultSheetVersion: dto.resultSheetVersion,
  };
}

/** UC38: sửa kết quả có lý do; BE sinh phiên bản phiếu mới rồi trả danh sách phiên bản đã cập nhật. */
export async function amendResult(
  visitId: string,
  reason: string,
  changes: AmendChanges = {},
): Promise<ResultSheet[]> {
  await apiRequest(`${root(visitId)}/amendments`, {
    method: "POST",
    body: JSON.stringify({
      ...changes,
      examination:
        changes.examination && examinationPayload(changes.examination),
      reason,
    }),
  });
  return listResultSheets(visitId);
}
