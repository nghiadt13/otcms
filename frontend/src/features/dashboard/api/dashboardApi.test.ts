import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/apiError";
import { getDashboardOverview } from "./dashboardApi";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => {
  request.mockReset();
});
it("forwards period and cancellation to the live dashboard endpoint", async () => {
  const result = { period: { from: "2026-11-01", to: "2026-11-07" } };
  request.mockResolvedValue(result);
  const signal = new AbortController().signal;
  expect(await getDashboardOverview(result.period, signal)).toBe(result);
  expect(request).toHaveBeenCalledWith(
    "/api/v1/admin/dashboard?from=2026-11-01&to=2026-11-07",
    { signal },
  );
});
it("maps direct backend MSG codes without fixture fallback", async () => {
  request.mockRejectedValue(new ApiError(400, { errorCode: "MSG-72" }));
  await expect(
    getDashboardOverview({ from: "bad", to: "bad" }),
  ).rejects.toMatchObject({ code: "MSG-72" });
});
