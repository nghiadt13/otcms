import { expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { listPatientAnnouncements } from "./patientAnnouncementApi";
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
it("requests the patient announcement page with the current session", async () => {
  const request = vi.mocked(apiRequest).mockResolvedValue({ items: [] });
  const signal = new AbortController().signal;
  await listPatientAnnouncements({ page: 1, size: 10 }, signal);
  expect(request).toHaveBeenCalledWith("/api/v1/me/announcements?page=1&size=10", { signal });
});
