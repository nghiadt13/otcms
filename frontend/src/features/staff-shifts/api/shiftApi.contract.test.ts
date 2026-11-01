import { beforeEach, afterEach, expect, it, vi } from "vitest";
const request = vi.hoisted(() => vi.fn());
vi.mock("@/lib/api/client", () => ({ apiRequest: request }));
beforeEach(() => {
  vi.stubEnv("NODE_ENV", "development");
  request.mockReset();
  vi.resetModules();
});
afterEach(() => {
  vi.unstubAllEnvs();
});
const input = {
  staffId: "staff",
  date: "2028-11-01",
  startTime: "08:00",
  endTime: "12:00",
  roomId: "room",
};
const shift = {
  ...input,
  id: "shift",
  staffName: "Lễ tân",
  role: "RECEPTIONIST",
  roomName: "Quầy",
  roomCategory: "EXAMINATION",
  allowedActions: ["edit", "delete"],
};
it("creates and deletes staff shifts through the merged staff controller, not the doctor endpoint", async () => {
  request.mockImplementation(async (path, init) =>
    path.endsWith("staff-options")
      ? {
          items: [
            { staffId: "staff", fullName: "Lễ tân", role: "RECEPTIONIST" },
          ],
        }
      : init?.method === "POST"
        ? shift
        : undefined,
  );
  const api = await import("./shiftApi");
  expect(await api.createShift(input)).toEqual(shift);
  await api.deleteShift("shift");
  expect(
    request.mock.calls.some(
      ([p, i]) =>
        p === "/api/v1/admin/staff-shifts" && i?.body === JSON.stringify(input),
    ),
  ).toBe(true);
  expect(request).toHaveBeenLastCalledWith("/api/v1/admin/staff-shifts/shift", {
    method: "DELETE",
  });
});
it("reads staff and doctor shifts without mutating the main workflow", async () => {
  request.mockImplementation(async (path) =>
    path.endsWith("staff-options")
      ? { items: [{ staffId: "d", fullName: "Doctor", role: "DOCTOR" }] }
      : path.includes("visit-rooms")
        ? {
            items: [
              {
                id: "room",
                type: "EXAMINATION",
                active: true,
                displayLabel: "P101",
              },
            ],
          }
        : path.includes("/doctors/d/shifts?")
          ? {
              items: [
                {
                  id: "ds",
                  doctorProfileId: "d",
                  roomId: "room",
                  startsAt: "2028-11-01T01:00:00Z",
                  endsAt: "2028-11-01T05:00:00Z",
                },
              ],
            }
          : { items: [shift] },
  );
  const api = await import("./shiftApi");
  const result = await api.listShifts({ from: "2028-11-01", to: "2028-11-01" });
  expect(result.items.map((s) => s.id).sort()).toEqual(["ds", "shift"]);
  expect(result.items.find((s) => s.id === "ds")).toMatchObject({
    date: "2028-11-01",
    startTime: "08:00",
    endTime: "12:00",
    roomId: "room",
  });
  const path = request.mock.calls.find(([p]) =>
    p.includes("/doctors/d/shifts?"),
  )![0];
  const query = new URL("http://local" + path).searchParams;
  expect(query.get("from")).toBe("2028-10-31T17:00:00.000Z");
  expect(query.get("to")).toBe("2028-11-01T17:00:00.000Z");
});
it("rejects a reversed date range before any backend request", async () => {
  const api = await import("./shiftApi");
  expect(
    await api
      .listShifts({ from: "2028-11-02", to: "2028-11-01" })
      .catch((e) => e.status),
  ).toBe(400);
  expect(request).not.toHaveBeenCalled();
});
