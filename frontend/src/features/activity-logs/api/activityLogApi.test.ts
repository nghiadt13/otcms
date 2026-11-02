import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { getActivityLog, listActivityLogs } from "./activityLogApi";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => {
  request.mockReset();
});
it("forwards filters, pagination and cancellation to the actual backend", async () => {
  const page = { items: [], totalItems: 0, totalPages: 0, page: 2, size: 20 };
  request.mockResolvedValue(page);
  const signal = new AbortController().signal;
  expect(
    await listActivityLogs(
      {
        from: "2026-11-01",
        to: "2026-11-07",
        action: "ROOM",
        record: "P.101 & khám",
        actor: "admin",
        page: 2,
        size: 20,
      },
      signal,
    ),
  ).toBe(page);
  const params = new URL(request.mock.calls[0][0], "http://local").searchParams;
  expect(Object.fromEntries(params)).toEqual({
    from: "2026-11-01",
    to: "2026-11-07",
    action: "ROOM",
    record: "P.101 & khám",
    actor: "admin",
    page: "2",
    size: "20",
  });
  expect(request.mock.calls[0][1].signal).toBe(signal);
});
it("encodes detail IDs and preserves server errors without fixture fallback", async () => {
  request.mockRejectedValue(new ApiError(404, { errorCode: "MSG-50" }));
  await expect(getActivityLog("room:unknown")).rejects.toMatchObject({
    code: "MSG-50",
  });
  expect(request.mock.calls[0][0]).toBe(
    "/api/v1/admin/activity-logs/room%3Aunknown",
  );
});
