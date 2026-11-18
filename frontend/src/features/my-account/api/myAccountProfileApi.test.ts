import { expect, it, vi } from "vitest";
import { updateCurrentPatientDisplayName } from "@/lib/auth/currentPatientAccountApi";
import { updateMyDisplayName } from "./myAccountProfileApi";
vi.mock("@/lib/auth/currentPatientAccountApi", () => ({
  updateCurrentPatientDisplayName: vi.fn(),
}));
vi.mock("./myAccountApi", () => ({
  getMyAccount: vi.fn().mockResolvedValue({ displayName: "An" }),
}));
it("updates the patient name through the API and reloads the account", async () => {
  expect(await updateMyDisplayName("An")).toEqual({ displayName: "An" });
  expect(updateCurrentPatientDisplayName).toHaveBeenCalledWith("An");
});
