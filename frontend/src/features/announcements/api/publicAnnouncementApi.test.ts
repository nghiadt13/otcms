import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import {
  listPublicAnnouncements,
  getPublicAnnouncement,
} from "./publicAnnouncementApi";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ publicApiRequest: request }));
beforeEach(() => {
  request.mockReset();
});
it("uses public endpoints only with server pagination", async () => {
  request.mockResolvedValue({
    items: [],
    page: 2,
    size: 10,
    totalItems: 0,
    totalPages: 0,
  });
  await listPublicAnnouncements({ page: 2, size: 10 });
  expect(request.mock.calls[0][0]).toBe(
    "/api/v1/public/announcements?page=2&size=10",
  );
  await getPublicAnnouncement("notice");
  expect(request.mock.calls.at(-1)![0]).toBe(
    "/api/v1/public/announcements/notice",
  );
});
it("does not substitute private or fake announcements for a 404", async () => {
  request.mockRejectedValue(new ApiError(404, { errorCode: "MSG-50" }));
  await expect(getPublicAnnouncement("staff-only")).rejects.toMatchObject({
    code: "MSG-50",
    status: 404,
  });
});
