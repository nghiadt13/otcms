import { beforeEach, expect, it, vi } from "vitest";
import { listResultSheets } from "./doctorExamLiveResultSheets";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => request.mockReset());

it("đọc phiên bản phiếu và ánh xạ bản ACTIVE", async () => {
  request.mockResolvedValue({ items: [{ documentId: "doc-1", version: 1, status: "ACTIVE", releasedAt: "2026-11-17T03:00:00Z" }] });
  await expect(listResultSheets("visit/1")).resolves.toEqual([{ documentId: "doc-1", version: 1, status: "CURRENT", issuedAt: "2026-11-17T03:00:00Z" }]);
  expect(request).toHaveBeenCalledWith("/api/v1/visits/visit%2F1/result-sheets", { signal: undefined });
});
