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
const profile = {
  id: "p",
  accountId: "a",
  username: "reception",
  role: "RECEPTIONIST",
  displayName: "Lễ tân",
  workContact: "work@example.com",
  photoUrl: "/uploads/p.jpg",
  status: "ACTIVE",
  allowedActions: ["edit"],
  doctor: null,
  publicUrl: null,
};
it("uses the merged staff API for non-doctor profiles and preserves paging", async () => {
  request.mockResolvedValue({
    items: [profile],
    page: 0,
    size: 20,
    totalItems: 1,
    totalPages: 1,
  });
  const api = await import("./staffProfileApi");
  const result = await api.listStaffProfiles({
    q: "Lễ tân",
    role: "RECEPTIONIST",
    status: "ACTIVE",
    isPublic: "",
    page: 0,
  });
  expect(request.mock.calls[0][0]).toContain("/api/v1/admin/staff-profiles?");
  expect(result.items[0].photoUrl).toBe("http://localhost:8080/uploads/p.jpg");
  expect(result.totalItems).toBe(1);
});
it("sends multipart identity details and photo to the existing backend", async () => {
  request.mockResolvedValue(profile);
  const api = await import("./staffProfileApi");
  const input = {
    accountId: "a",
    displayName: "Lễ tân",
    workContact: "work@example.com",
  };
  const photo = new File(["photo"], "avatar.png", { type: "image/png" });
  await api.createStaffProfile(input, photo);
  const [path, init] = request.mock.calls[0];
  expect(path).toBe("/api/v1/admin/staff-profiles");
  expect(init.method).toBe("POST");
  expect(init.body).toBeInstanceOf(FormData);
  expect(JSON.parse(await init.body.get("data").text())).toEqual(input);
  expect(init.body.get("photo").name).toBe("avatar.png");
});
it("does not silently use fixture data when the backend rejects a profile", async () => {
  const { ApiError } = await import("@/lib/api/apiError");
  request.mockRejectedValue(new ApiError(404));
  const api = await import("./staffProfileApi");
  expect(
    await api
      .getStaffProfile("missing")
      .catch((e) => ({ status: e.status, code: e.code })),
  ).toEqual({ status: 404, code: "MSG-23" });
  expect(request).toHaveBeenCalledTimes(1);
});
