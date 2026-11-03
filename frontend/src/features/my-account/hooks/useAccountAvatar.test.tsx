import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useAccountAvatar } from "./useAccountAvatar";
import { mapMyAccountResponse } from "../api/myAccountResponse";
import {
  accountResponse,
  ACCOUNT_ID,
  OTHER_ACCOUNT_ID,
} from "../api/myAccountTestFixtures";
import { MY_ACCOUNT_ACCESS_LOST } from "../api/myAccountEvents";

const state = vi.hoisted(() => ({
  owner: "",
  token: "binary-test-token" as string | null,
}));
vi.mock("@/lib/auth/authSession", async (original) => ({
  ...(await original<typeof import("@/lib/auth/authSession")>()),
  peekSession: () => (state.owner ? { accountId: state.owner } : null),
  getAccessToken: async () => state.token,
}));
function source(id = ACCOUNT_ID) {
  return mapMyAccountResponse(
    accountResponse({ id, avatarUrl: `/api/v1/accounts/${id}/avatar` }),
  );
}
let fetchMock: ReturnType<typeof vi.fn>;
beforeEach(() => {
  state.owner = ACCOUNT_ID;
  state.token = "binary-test-token";
  fetchMock = vi
    .fn()
    .mockImplementation(
      async () => new Response(new Blob(["photo"], { type: "image/png" })),
    );
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:authenticated");
  vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
});
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("avatar đi qua fetchBlob thật và HTTP fetch boundary", () => {
  it("gửi JWT, tạo blob URL, không trả protected URL; unmount thu hồi URL", async () => {
    const account = source();
    const { result, unmount } = renderHook(() => useAccountAvatar(account));
    expect(result.current.avatarUrl).toBeNull();
    await waitFor(() =>
      expect(result.current.avatarUrl).toBe("blob:authenticated"),
    );
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toContain(`/api/v1/accounts/${ACCOUNT_ID}/avatar`);
    expect(new Headers(init.headers).get("Authorization")).toBe(
      "Bearer binary-test-token",
    );
    expect(init.cache).toBe("no-store");
    expect(URL.createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
    unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:authenticated");
    expect(init.signal?.aborted).toBe(true);
  });
  it("xóa ảnh và đổi projection thu hồi URL cũ, không giữ ảnh cũ", async () => {
    let account = source();
    const { result, rerender } = renderHook(() => useAccountAvatar(account));
    await waitFor(() => expect(result.current.avatarUrl).not.toBeNull());
    account = mapMyAccountResponse(accountResponse({ avatarUrl: null }));
    rerender();
    expect(result.current.avatarUrl).toBeNull();
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1);
    account = source();
    rerender();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(result.current.avatarUrl).not.toBeNull());
  });
  it("đổi tài khoản khi ảnh đang tải: abort và bỏ response cũ", async () => {
    let finish!: (value: Response) => void;
    fetchMock.mockReturnValueOnce(
      new Promise<Response>((resolve) => {
        finish = resolve;
      }),
    );
    let account = source();
    const { result, rerender } = renderHook(() => useAccountAvatar(account));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    const signal = (fetchMock.mock.calls[0][1] as RequestInit).signal;
    state.owner = OTHER_ACCOUNT_ID;
    account = source(OTHER_ACCOUNT_ID);
    rerender();
    expect(signal?.aborted).toBe(true);
    await waitFor(() =>
      expect(result.current.avatarUrl).toBe("blob:authenticated"),
    );
    await act(async () => finish(new Response(new Blob(["old"]))));
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
  });
  it("đổi tài khoản sau tải thu hồi URL thuộc tài khoản trước", async () => {
    let account = source();
    const { result, rerender } = renderHook(() => useAccountAvatar(account));
    await waitFor(() => expect(result.current.avatarUrl).not.toBeNull());
    state.owner = OTHER_ACCOUNT_ID;
    account = source(OTHER_ACCOUNT_ID);
    rerender();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:authenticated");
    expect(result.current.avatarUrl).toBeNull();
  });
  it("forced-password không tải endpoint avatar bị mutation gate", () => {
    const account = { ...source(), mustChangePassword: true };
    renderHook(() => useAccountAvatar(account));
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it.each([401, 403])(
    "binary HTTP %s thu hồi quyền của consumer hiện tại",
    async (status) => {
      fetchMock.mockResolvedValue(new Response("{}", { status }));
      const lost = vi.fn();
      window.addEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
      try {
        renderHook(() => useAccountAvatar(source()));
        await waitFor(() => expect(lost).toHaveBeenCalledTimes(1));
        expect(URL.createObjectURL).not.toHaveBeenCalled();
      } finally {
        window.removeEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
      }
    },
  );
  it.each([404, 503])(
    "binary HTTP %s không xóa account; trả trạng thái lỗi ảnh",
    async (status) => {
      fetchMock.mockResolvedValue(new Response("{}", { status }));
      const lost = vi.fn();
      window.addEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
      const account = source();
      try {
        const { result } = renderHook(() => useAccountAvatar(account));
        await waitFor(() => expect(result.current.avatarError).not.toBeNull());
        expect(result.current.avatarUrl).toBeNull();
        expect(lost).not.toHaveBeenCalled();
      } finally {
        window.removeEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
      }
    },
  );
  it("không còn token trước khi gửi binary request: không fetch và báo mất quyền", async () => {
    state.token = null;
    const lost = vi.fn();
    window.addEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
    const account = source();
    try {
      renderHook(() => useAccountAvatar(account));
      await waitFor(() => expect(lost).toHaveBeenCalledTimes(1));
      expect(fetchMock).not.toHaveBeenCalled();
    } finally {
      window.removeEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
    }
  });
});

it("binary lỗi sau khi session bị xóa vẫn phát access-lost", async () => {
  let fail!: (error: unknown) => void;
  fetchMock.mockReturnValueOnce(
    new Promise((_, reject) => {
      fail = reject;
    }),
  );
  const lost = vi.fn();
  window.addEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
  const account = source();
  try {
    renderHook(() => useAccountAvatar(account));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    state.owner = "";
    await act(async () => fail(new Error("Bạn cần đăng nhập để tiếp tục.")));
    expect(lost).toHaveBeenCalledTimes(1);
  } finally {
    window.removeEventListener(MY_ACCOUNT_ACCESS_LOST, lost);
  }
});
