import { describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { unlinkPatientProfile } from "./patientProfileLinkApi";
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
describe("unlinkPatientProfile", () => {
  it("gỡ người thân qua endpoint dependents", async () => {
    vi.mocked(apiRequest).mockResolvedValueOnce(undefined);
    await unlinkPatientProfile("patient/id");
    expect(apiRequest).toHaveBeenCalledWith("/api/v1/me/dependents/patient%2Fid", { method: "DELETE" });
  });
});
