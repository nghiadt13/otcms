import { beforeEach, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { getSession } from "@/lib/auth/authSession";
import { getMyAccount, updateMyAvatar, removeMyAvatar } from "./myAccountApi";
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
vi.mock("@/lib/auth/authSession", () => ({ getSession: vi.fn() }));
vi.mock("@/lib/auth/accountAvatarCache", () => ({
  loadAccountAvatarUrl: vi.fn(),
  notifyAccountAvatarChanged: vi.fn(),
}));
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getSession).mockResolvedValue({ accountType: "ADMIN" } as never);
  vi.mocked(apiRequest).mockResolvedValue({
    id: "1",
    kind: "STAFF",
    avatarUrl: null,
  });
});
it("loads the signed-in staff account from the API", async () => {
  expect(await getMyAccount()).toMatchObject({ id: "1", kind: "STAFF" });
  expect(apiRequest).toHaveBeenCalledWith("/api/v1/me", { signal: undefined });
});
it("uploads and removes avatar through the API", async () => {
  await updateMyAvatar(new File(["x"], "a.png", { type: "image/png" }));
  expect(apiRequest).toHaveBeenCalledWith(
    "/api/v1/me/avatar",
    expect.objectContaining({ method: "PUT", body: expect.any(FormData) }),
  );
  await removeMyAvatar();
  expect(apiRequest).toHaveBeenCalledWith("/api/v1/me/avatar", {
    method: "DELETE",
  });
});
it("rejects a missing session", async () => {
  vi.mocked(getSession).mockResolvedValue(null);
  await expect(getMyAccount()).rejects.toMatchObject({ status: 401 });
});
