import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { listPatientImagingHistory } from "./patientImagingHistoryApi";

vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));

describe("patientImagingHistoryApi", () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset());

  it("uses the authenticated owner-authorized FEAT-003 imaging history contract", async () => {
    const response = { items: [], page: 0, size: 20, totalElements: 0 };
    vi.mocked(apiRequest).mockResolvedValueOnce(response);

    await expect(
      listPatientImagingHistory("patient-uuid", 0, 20),
    ).resolves.toEqual(response);
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/imaging-history?patientId=patient-uuid&page=0&size=20",
      { signal: undefined },
    );
  });
});
