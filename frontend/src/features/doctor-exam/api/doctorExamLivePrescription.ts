import { ApiError, isApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import { prescriptionItems } from "../schema/prescription";
import type { Prescription, PrescriptionLine } from "../types/doctorExam";

type PrescriptionDto = {
  id: string;
  visitId: string;
  patientProfileId: string;
  status: "DRAFT" | "ISSUED";
  issuedAt: string | null;
  version: number;
  items: Array<PrescriptionLine & { lineNumber: number }>;
};
const path = (visitId: string) =>
  `/api/v1/visits/${encodeURIComponent(visitId)}/prescription` as const;
const map = (dto: PrescriptionDto): Prescription => ({
  id: dto.id,
  status: dto.status,
  issuedAt: dto.issuedAt,
  version: dto.version,
  lines: [...dto.items]
    .sort((a, b) => a.lineNumber - b.lineNumber)
    .map(({ medicine, dose, frequency, usage }) => ({
      medicine,
      dose,
      frequency,
      usage,
    })),
});
export async function getPrescription(
  visitId: string,
  signal?: AbortSignal,
): Promise<Prescription | null> {
  try {
    return map(await apiRequest<PrescriptionDto>(path(visitId), { signal }));
  } catch (error) {
    if (
      isApiError(error) &&
      error.status === 404 &&
      (!error.code || error.code === "MSG-41")
    )
      return null;
    throw error;
  }
}
export async function savePrescription(
  visitId: string,
  value: Prescription,
  issue: boolean,
): Promise<Prescription> {
  if (value.status === "ISSUED")
    throw new ApiError(409, { detail: "Đơn thuốc đã phát hành chỉ được xem." });
  const items = prescriptionItems(value.lines);
  // One key per user action. No retry with a different key after an ambiguous response.
  const key = issue ? crypto.randomUUID() : null;
  const draft = await apiRequest<PrescriptionDto>(path(visitId), {
    method: "PUT",
    body: JSON.stringify({
      version: value.id === null ? 0 : value.version,
      items,
    }),
  });
  if (!issue) return map(draft);
  return map(
    await apiRequest<PrescriptionDto>(`${path(visitId)}/issuance`, {
      method: "POST",
      headers: { "Idempotency-Key": key! },
      body: JSON.stringify({ version: draft.version }),
    }),
  );
}
