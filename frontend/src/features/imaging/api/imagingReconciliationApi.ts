import { apiRequest } from "@/lib/api/client";

export type ImagingReconciliationCase = {
  caseId: string;
  operationId: string | null;
  eventId: string | null;
  category: string;
  classification: "CONFLICT" | "ORPHANED" | "RETRYABLE" | string;
  state: "OPEN" | "IN_PROGRESS" | "RESOLVED" | string;
  safeReasonCode: string;
  assignedOwnerQueue: string;
  openedAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  resolutionDecision: string | null;
};

export type ImagingReconciliationPage = {
  items: ImagingReconciliationCase[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ImagingReconciliationDecision =
  | "KEEP_QUARANTINED"
  | "EXTERNAL_RESOLUTION_CONFIRMED"
  | "START_REVIEW";

export async function listImagingReconciliationCases(
  state: string,
  signal?: AbortSignal,
): Promise<ImagingReconciliationPage> {
  const query = new URLSearchParams({ page: "0", size: "100" });
  if (state !== "ALL") query.set("state", state);
  return apiRequest<ImagingReconciliationPage>(
    `/api/v1/imaging/reconciliation-cases?${query}`,
    { signal },
  );
}

export async function recordImagingReconciliationDecision(
  caseId: string,
  decision: ImagingReconciliationDecision,
  resolutionRef: string,
): Promise<void> {
  await apiRequest<void>(
    `/api/v1/imaging/reconciliation-cases/${encodeURIComponent(caseId)}/decisions`,
    {
      method: "POST",
      body: JSON.stringify({ decision, resolutionRef }),
    },
  );
}
