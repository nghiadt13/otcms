import { beforeEach, expect, it, vi } from "vitest";
import { listImagingRooms } from "./imagingRoomApi";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));

beforeEach(() => request.mockReset());

it("reads imaging rooms from the protected endpoint and maps live status", async () => {
  request.mockResolvedValue({ items: [
    { id: "uuid", roomCode: "XR-01", displayLabel: "Phòng X-quang", type: "IMAGING", description: "Tầng 2", capabilities: ["X-quang"], active: true },
    { id: "exam", roomCode: "EX-01", displayLabel: "Phòng khám", type: "EXAMINATION", description: "", capabilities: [], active: true },
  ] });
  expect(await listImagingRooms()).toEqual([{ id: "uuid", code: "XR-01", name: "Phòng X-quang", type: "IMAGING", description: "Tầng 2", capabilities: ["X-quang"], availability: "Hoạt động" }]);
  expect(request).toHaveBeenCalledWith("/api/v1/staff/rooms", { signal: undefined });
});
