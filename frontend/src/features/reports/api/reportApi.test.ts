import { beforeEach, expect, it, vi } from "vitest";
import { listReportSubjects, createReport, exportReport } from "./reportApi";
import { ApiError } from "@/lib/api/apiError";
const mock = vi.hoisted(() => ({ request: vi.fn(), download: vi.fn() }));
vi.mock("@/lib/api/client", () => ({ apiRequest: mock.request }));
vi.mock("@/lib/api/apiDownload", () => ({ apiDownload: mock.download }));
beforeEach(() => {
  mock.request.mockReset();
  mock.download.mockReset();
});
const query = {
  subject: "APPOINTMENTS" as const,
  from: "2026-11-01",
  to: "2026-11-07",
  breakdown: "DATE" as const,
};
it("loads supported subjects and forwards the exact report query", async () => {
  mock.request
    .mockResolvedValueOnce({ items: [{ code: "APPOINTMENTS" }] })
    .mockResolvedValueOnce({ query, allowedActions: [] });
  expect(await listReportSubjects()).toHaveLength(1);
  expect((await createReport(query)).allowedActions).toEqual([]);
  expect(mock.request).toHaveBeenLastCalledWith(
    "/api/v1/admin/reports",
    expect.objectContaining({ method: "POST", body: JSON.stringify(query) }),
  );
});
it("exports the exact report query as CSV and never fakes a file when the backend fails", async () => {
  mock.download.mockResolvedValueOnce(undefined);
  await exportReport(query, "csv");
  expect(mock.download).toHaveBeenCalledWith(
    "/api/v1/admin/reports/export?subject=APPOINTMENTS&from=2026-11-01&to=2026-11-07&breakdown=DATE&format=csv",
    expect.anything(),
  );
  mock.download.mockRejectedValue(new ApiError(503, { code: "MSG-04" }));
  await expect(exportReport(query, "csv")).rejects.toMatchObject({
    status: 503,
  });
});
