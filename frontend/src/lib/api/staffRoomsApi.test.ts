import { beforeEach, expect, it, vi } from "vitest";
import { getStaffRoom, listStaffRooms } from "./staffRoomsApi";

const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));

beforeEach(() => request.mockReset());

const dto = {
  id: "r1",
  roomCode: "XR-01",
  displayLabel: "",
  type: "IMAGING",
  description: null,
  capabilities: null,
  supportedServiceIds: ["a", "b"],
  active: true,
};

it("maps the role-scoped room list", async () => {
  request.mockResolvedValue({ items: [dto] });
  expect(await listStaffRooms()).toEqual([
    { id: "r1", code: "XR-01", name: "XR-01", type: "IMAGING", description: "", capabilities: [], supportedServiceCount: 2, active: true },
  ]);
  expect(request).toHaveBeenCalledWith("/api/v1/staff/rooms", { signal: undefined });
});

it("reads one room by id", async () => {
  request.mockResolvedValue(dto);
  expect((await getStaffRoom("r 1")).code).toBe("XR-01");
  expect(request).toHaveBeenCalledWith("/api/v1/staff/rooms/r%201", { signal: undefined });
});
