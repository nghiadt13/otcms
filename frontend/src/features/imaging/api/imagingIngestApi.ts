import { apiRequest, apiRequestWithUploadProgress } from "@/lib/api/client";

export type AssignedImagingUploadWork = {
  requestId: string;
  attemptId: string | null;
  visitId: string;
  patient: {
    patientProfileId: string;
    fullName: string;
    dateOfBirth: string | null;
    gender: string | null;
  };
  currentStudyRef: string | null;
  serviceId: string | null;
  serviceName: string | null;
  imagingType: string;
  bodyArea: string | null;
  clinicalReason: string | null;
  technicianInstructions: string | null;
  roomId: string;
  status: "PENDING" | "IN_PROGRESS" | "RESULT_AVAILABLE";
  version: number;
  createdAt: string;
  updatedAt: string;
};

export type AssignedImagingUploadWorkPage = {
  items: AssignedImagingUploadWork[];
  page: number;
  size: number;
  totalElements: number;
};

export type ImagingOperationState =
  | "PROCESSING"
  | "ARCHIVED_NOT_SENT"
  | "CONFIRMATION_PENDING"
  | "LINKED"
  | "QUARANTINED"
  | "FAILED"
  | "RECONCILIATION_REQUIRED";

export type ImagingOperationResponse = {
  operationId: string;
  state: ImagingOperationState;
  createdAt: string;
  updatedAt: string;
  statusUrl: string;
  error?: { code: string; message: string } | null;
  instanceCount?: number | null;
  adminResolutionRecorded?: boolean;
  externalResolutionConfirmed?: boolean;
};

export type ImagingUploadSubmission = {
  body: Blob;
  contentType: string;
  idempotencyKey: string;
};

export async function listAssignedImagingUploadWork(
  page = 0,
  size = 100,
  signal?: AbortSignal,
): Promise<AssignedImagingUploadWorkPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return apiRequest<AssignedImagingUploadWorkPage>(
    `/api/v1/imaging-requests/assigned-upload-work?${query}` as const,
    { signal },
  );
}

export async function startImagingAcquisition(requestId: string, expectedVersion: number, idempotencyKey: string) {
  return apiRequest(`/api/v1/imaging-requests/${encodeURIComponent(requestId)}/acquisition-starts`, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey, "X-Expected-Version": String(expectedVersion) },
  });
}

/** Build one immutable multipart entity so a retry can reuse byte-identical request bytes. */
export function prepareImagingUpload(files: File[]): ImagingUploadSubmission {
  if (files.length === 0) throw new Error("Chọn ít nhất một file DICOM.");
  const idempotencyKey = crypto.randomUUID();
  if (files.length === 1 && files[0].name.toLowerCase().endsWith(".zip")) {
    return {
      body: files[0],
      contentType: "application/zip",
      idempotencyKey,
    };
  }
  if (files.some((file) => file.name.toLowerCase().endsWith(".zip"))) {
    throw new Error(
      "Hãy tải một file ZIP riêng, hoặc chỉ chọn các file DICOM của cùng study.",
    );
  }
  const boundary = `----OTCMS-${crypto.randomUUID().replaceAll("-", "")}`;
  const parts: BlobPart[] = [];

  files.forEach((file, index) => {
    parts.push(
      `--${boundary}\r\nContent-Disposition: form-data; name="files"; filename="instance-${String(index + 1).padStart(6, "0")}.dcm"\r\nContent-Type: application/dicom\r\n\r\n`,
      file,
      "\r\n",
    );
  });
  parts.push(`--${boundary}--\r\n`);

  return {
    body: new Blob(parts),
    contentType: `multipart/form-data; boundary=${boundary}`,
    idempotencyKey,
  };
}

export async function uploadImagingStudy(
  work: AssignedImagingUploadWork,
  submission: ImagingUploadSubmission,
  onProgress?: (loaded: number, total: number) => void,
): Promise<ImagingOperationResponse> {
  if (!work.attemptId) {
    throw new Error("Chỉ định chưa có attempt được FEAT-003 xác nhận.");
  }
  const path = `/api/v1/imaging/requests/${encodeURIComponent(work.requestId)}/attempts/${encodeURIComponent(work.attemptId)}/ingest-operations` as const;
  const init: RequestInit = {
    method: "POST",
    headers: {
      "Content-Type": submission.contentType,
      "Idempotency-Key": submission.idempotencyKey,
    },
    body: submission.body,
  };
  return onProgress
    ? apiRequestWithUploadProgress<ImagingOperationResponse>(path, init, onProgress)
    : apiRequest<ImagingOperationResponse>(path, init);
}

export async function getImagingOperation(
  operationId: string,
  signal?: AbortSignal,
): Promise<ImagingOperationResponse> {
  return apiRequest<ImagingOperationResponse>(
    `/api/v1/imaging/ingest-operations/${encodeURIComponent(operationId)}` as const,
    { signal },
  );
}

export async function getLatestImagingOperation(
  requestId: string,
  attemptId: string,
  signal?: AbortSignal,
): Promise<ImagingOperationResponse | null> {
  const operations = await apiRequest<ImagingOperationResponse[]>(
    `/api/v1/imaging/requests/${encodeURIComponent(requestId)}/attempts/${encodeURIComponent(attemptId)}/ingest-operations` as const,
    { signal },
  );
  return operations[0] ?? null;
}

export async function sendImagingOperation(
  operationId: string,
  idempotencyKey = crypto.randomUUID(),
): Promise<ImagingOperationResponse> {
  return apiRequest<ImagingOperationResponse>(
    `/api/v1/imaging/ingest-operations/${encodeURIComponent(operationId)}/send` as const,
    { method: "POST", headers: { "Idempotency-Key": idempotencyKey } },
  );
}
