import { beforeEach, expect, it, vi } from "vitest";
import {
  callNextService,
  getServiceWorklist,
  markServiceEntryAbsent,
  returnServiceEntry,
} from "./imagingQueueApi";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));

beforeEach(() => request.mockReset());

it("reads the real service worklist of a room", async () => {
  request.mockResolvedValue({ roomId: "r1", entries: [] });
  await getServiceWorklist("r1");
  expect(request).toHaveBeenCalledWith(
    "/api/v1/queue/rooms/r1/service-worklist",
    { signal: undefined },
  );
});

it("calls the next patient of the SERVICE stage", async () => {
  request.mockResolvedValue({ messageCode: "MSG-43" });
  await callNextService("r 1");
  expect(request).toHaveBeenCalledWith(
    "/api/v1/queue/rooms/r%201/call-next?stage=SERVICE",
    { method: "POST" },
  );
});

it("marks absent and returns to the end of the queue with a trimmed reason", async () => {
  request.mockResolvedValue({ messageCode: "MSG-43" });
  await markServiceEntryAbsent("e1");
  expect(request).toHaveBeenLastCalledWith("/api/v1/queue/entries/e1/absence", {
    method: "POST",
  });
  await returnServiceEntry("e1", "  Đã quay lại  ");
  expect(request).toHaveBeenLastCalledWith("/api/v1/queue/entries/e1/return", {
    method: "POST",
    body: JSON.stringify({ beforeEntryId: null, reason: "Đã quay lại" }),
  });
});
