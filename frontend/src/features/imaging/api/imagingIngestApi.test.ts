import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import {
  listAssignedImagingUploadWork,
  prepareImagingUpload,
  sendImagingOperation,
  uploadImagingStudy,
} from "./imagingIngestApi";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));

describe("imagingIngestApi", () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset());

  it("loads only the authenticated technician's assigned upload work", async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
    });

    await listAssignedImagingUploadWork(0, 20);

    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/imaging-requests/assigned-upload-work?page=0&size=20",
      { signal: undefined },
    );
  });

  it("rejects upload until an owner-created current attempt is available", async () => {
    const submission = prepareImagingUpload([new File(["dicom"], "image.dcm")]);

    await expect(
      uploadImagingStudy(
        {
          requestId: "request-id",
          attemptId: null,
          currentStudyRef: null,
          visitId: "visit-id",
          imagingType: "MR",
          roomId: "room-id",
          status: "PENDING",
          version: 1,
          createdAt: "2026-11-05T00:00:00Z",
          updatedAt: "2026-11-05T00:00:00Z",
        },
        submission,
      ),
    ).rejects.toThrow("attempt được FEAT-003 xác nhận");
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("uploads the exact immutable multipart bytes using the request and current attempt IDs", async () => {
    const file = new File(["sample-dicom-bytes"], "patient-name.dcm");
    const submission = prepareImagingUpload([file]);
    const bodyText = await submission.body.text();
    expect(bodyText).toContain('name="files"; filename="instance-000001.dcm"');
    expect(bodyText).toContain("sample-dicom-bytes");
    expect(bodyText).not.toContain("patient-name.dcm");

    const response = { operationId: "operation-id", state: "PROCESSING" };
    vi.mocked(apiRequest).mockResolvedValueOnce(response);
    const result = await uploadImagingStudy(
      {
        requestId: "request-id",
        attemptId: "attempt-id",
        currentStudyRef: null,
        visitId: "visit-id",
        imagingType: "MR",
        roomId: "room-id",
        status: "IN_PROGRESS",
        version: 2,
        createdAt: "2026-11-05T00:00:00Z",
        updatedAt: "2026-11-05T00:00:00Z",
      },
      submission,
    );

    expect(result).toEqual(response);
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/imaging/requests/request-id/attempts/attempt-id/ingest-operations",
      {
        method: "POST",
        headers: {
          "Content-Type": submission.contentType,
          "Idempotency-Key": submission.idempotencyKey,
        },
        body: submission.body,
      },
    );
    expect(await submission.body.text()).toBe(bodyText);
  });

  it("sends a single ZIP as a raw archive body", () => {
    const zip = new File(["zip-bytes"], "study.zip", {
      type: "application/zip",
    });
    const submission = prepareImagingUpload([zip]);

    expect(submission.body).toBe(zip);
    expect(submission.contentType).toBe("application/zip");
    expect(submission.idempotencyKey).toBeTruthy();
  });

  it("sends an archived operation through the explicit SEND endpoint", async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce({
      operationId: "operation-id",
      state: "CONFIRMATION_PENDING",
    });

    await sendImagingOperation("operation-id", "send-key");

    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/imaging/ingest-operations/operation-id/send",
      { method: "POST", headers: { "Idempotency-Key": "send-key" } },
    );
  });
});
