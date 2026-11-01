import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  listAnnouncements,
  getAnnouncement,
  getAnnouncementOptions,
  createAnnouncement,
} from "./announcementApi";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => {
  request.mockReset();
});
it("passes filters, recipient options and mutations to backend", async () => {
  request.mockResolvedValue({});
  await listAnnouncements({
    q: "nghỉ lễ",
    audience: "PUBLIC",
    from: "2026-11-01",
    to: "2026-11-07",
    page: 1,
    size: 20,
  });
  expect(
    new URL(request.mock.calls[0][0], "http://local").searchParams.get(
      "audience",
    ),
  ).toBe("PUBLIC");
  await getAnnouncementOptions();
  expect(request.mock.calls.at(-1)![0]).toBe(
    "/api/v1/admin/announcements/options",
  );
  await getAnnouncement("a");
  expect(request.mock.calls.at(-1)![0]).toBe("/api/v1/admin/announcements/a");
  const body = {
    title: "Lịch nghỉ",
    body: "Nghỉ thứ hai",
    audience: "PUBLIC" as const,
    channels: ["IN_APP" as const],
  };
  await createAnnouncement(body);
  expect(request).toHaveBeenLastCalledWith("/api/v1/admin/announcements", {
    method: "POST",
    body: JSON.stringify(body),
  });
});
it("keeps MSG-71 from the server envelope", async () => {
  request.mockRejectedValue(new ApiError(400, { errorCode: "MSG-71" }));
  await expect(getAnnouncementOptions()).rejects.toMatchObject({
    code: "MSG-71",
  });
});
