import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { AuthSessionError, type AuthSession } from "@/lib/auth/authSession";
import { formatAppMessage } from "@/lib/messages/appMessages";
import {
  accountResponse,
  ACCOUNT_ID,
  OTHER_ACCOUNT_ID,
} from "../api/myAccountTestFixtures";
import { updateMyDisplayName } from "../api/myAccountProfileApi";
import { emitMyAccountChanged, useMyAccount } from "./useMyAccount";

const state = vi.hoisted(() => ({
  path: "/admin/account",
  session: null as AuthSession | null,
}));
vi.mock("next/navigation", () => ({ usePathname: () => state.path }));
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
vi.mock("@/lib/auth/authSession", async (original) => ({
  ...(await original<typeof import("@/lib/auth/authSession")>()),
  peekSession: () => state.session,
}));

beforeEach(() => {
  vi.resetAllMocks();
  state.path = "/admin/account";
  state.session = {
    accountId: ACCOUNT_ID,
    accessToken: "test-only-token",
    accountType: "ADMIN",
    expiresAt: Date.now() + 60000,
    mustChangePassword: false,
  };
});

function deferred() {
  let resolve!: (value: unknown) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<unknown>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

describe("useMyAccount đi qua HTTP API boundary", () => {
  it("load dữ liệu server, giữ quyền và forced-password", async () => {
    vi.mocked(apiRequest).mockResolvedValue(
      accountResponse({
        mustChangePassword: true,
        allowedActions: ["changePassword"],
      }),
    );
    const { result } = renderHook(() => useMyAccount());
    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.account).toMatchObject({
      id: ACCOUNT_ID,
      mustChangePassword: true,
      allowedActions: ["changePassword"],
    });
    expect(result.current.error).toBeNull();
  });

  it.each([
    { label: "401", error: new ApiError(401), msg: "MSG-14" },
    {
      label: "403",
      error: new ApiError(403, { errorCode: "MSG-14" }),
      msg: "MSG-14",
    },
    { label: "404", error: new ApiError(404), msg: "MSG-14" },
    {
      label: "500",
      error: new ApiError(500, { errorCode: "MSG-04" }),
      msg: "MSG-04",
    },
    { label: "503", error: new ApiError(503), msg: "MSG-04" },
    {
      label: "network",
      error: new TypeError("Failed to fetch"),
      msg: "MSG-04",
    },
    {
      label: "missing access token",
      error: new Error("Bạn cần đăng nhập để tiếp tục."),
      msg: "MSG-14",
    },
    { label: "session 401", error: new AuthSessionError(401), msg: "MSG-14" },
    { label: "session 403", error: new AuthSessionError(403), msg: "MSG-14" },
    { label: "session network", error: new AuthSessionError(0), msg: "MSG-04" },
  ] as const)(
    "reload lỗi $label xóa account cũ, không fallback mock",
    async ({ error, msg }) => {
      vi.mocked(apiRequest)
        .mockResolvedValueOnce(accountResponse())
        .mockRejectedValueOnce(error);
      const { result } = renderHook(() => useMyAccount());
      await waitFor(() => expect(result.current.account?.id).toBe(ACCOUNT_ID));
      act(() => result.current.reload());
      expect(result.current.account).toBeNull();
      await waitFor(() => expect(result.current.loading).toBe(false));
      expect(result.current.account).toBeNull();
      expect(result.current.error).toBe(formatAppMessage(msg));
    },
  );

  it("retry thành công sau lỗi dùng response mới", async () => {
    vi.mocked(apiRequest)
      .mockRejectedValueOnce(new ApiError(503))
      .mockResolvedValueOnce(accountResponse({ displayName: "Sau retry" }));
    const { result } = renderHook(() => useMyAccount());
    await waitFor(() =>
      expect(result.current.error).toBe(formatAppMessage("MSG-04")),
    );
    act(() => result.current.reload());
    await waitFor(() =>
      expect(result.current.account?.displayName).toBe("Sau retry"),
    );
    expect(result.current.error).toBeNull();
  });

  it("hủy/bỏ response GET đến muộn sau lần reload mới", async () => {
    const old = deferred(),
      latest = deferred();
    vi.mocked(apiRequest)
      .mockReturnValueOnce(old.promise)
      .mockReturnValueOnce(latest.promise);
    const { result } = renderHook(() => useMyAccount());
    await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(1));
    const signal = vi.mocked(apiRequest).mock.calls[0][1]?.signal;
    act(() => result.current.reload());
    expect(signal?.aborted).toBe(true);
    await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(2));
    await act(async () => {
      latest.resolve(accountResponse({ displayName: "Mới" }));
    });
    await waitFor(() =>
      expect(result.current.account?.displayName).toBe("Mới"),
    );
    await act(async () => {
      old.resolve(accountResponse({ displayName: "Cũ" }));
    });
    expect(result.current.account?.displayName).toBe("Mới");
  });

  it.each(["resolve", "reject"] as const)(
    "GET cũ %s sau PATCH không ghi đè response server vừa lưu",
    async (finish) => {
      const old = deferred();
      vi.mocked(apiRequest)
        .mockResolvedValueOnce(accountResponse())
        .mockReturnValueOnce(old.promise)
        .mockResolvedValueOnce(
          accountResponse({ displayName: "Server đã lưu", allowedActions: [] }),
        );
      const { result } = renderHook(() => useMyAccount());
      await waitFor(() => expect(result.current.account).not.toBeNull());
      act(() => result.current.reload());
      await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(2));
      await act(async () => {
        await updateMyDisplayName("Tên đã nhập");
      });
      expect(result.current.account).toMatchObject({
        displayName: "Server đã lưu",
        allowedActions: [],
      });
      await act(async () => {
        if (finish === "resolve")
          old.resolve(accountResponse({ displayName: "Cũ" }));
        else old.reject(new ApiError(401));
      });
      expect(result.current.account?.displayName).toBe("Server đã lưu");
      expect(result.current.error).toBeNull();
    },
  );

  it("PATCH đồng bộ hai consumer mà không thêm GET hoặc sửa shared cache", async () => {
    vi.mocked(apiRequest)
      .mockResolvedValueOnce(accountResponse())
      .mockResolvedValueOnce(accountResponse())
      .mockResolvedValueOnce(
        accountResponse({
          displayName: "Đã cập nhật",
          allowedActions: ["changePassword"],
        }),
      );
    const { result } = renderHook(() => ({
      a: useMyAccount(),
      b: useMyAccount(),
    }));
    await waitFor(() => expect(result.current.a.account).not.toBeNull());
    await waitFor(() => expect(result.current.b.account).not.toBeNull());
    await act(async () => {
      await updateMyDisplayName("Tên gửi");
    });
    for (const consumer of [result.current.a, result.current.b])
      expect(consumer.account).toMatchObject({
        displayName: "Đã cập nhật",
        allowedActions: ["changePassword"],
      });
    expect(apiRequest).toHaveBeenCalledTimes(3);
  });

  it.each([401, 403, 404])(
    "PATCH lỗi %s xóa state của mọi consumer",
    async (status) => {
      vi.mocked(apiRequest)
        .mockResolvedValueOnce(accountResponse())
        .mockResolvedValueOnce(accountResponse())
        .mockRejectedValueOnce(new ApiError(status));
      const { result } = renderHook(() => ({
        a: useMyAccount(),
        b: useMyAccount(),
      }));
      await waitFor(() =>
        expect(
          result.current.a.account && result.current.b.account,
        ).toBeTruthy(),
      );
      await act(async () => {
        await expect(updateMyDisplayName("Tên mới")).rejects.toMatchObject({
          status,
        });
      });
      expect(result.current.a.account).toBeNull();
      expect(result.current.b.account).toBeNull();
      expect(result.current.a.error).toBe(formatAppMessage("MSG-14"));
    },
  );

  it("sự kiện cũ không có payload vẫn refetch GET thật", async () => {
    vi.mocked(apiRequest)
      .mockResolvedValueOnce(accountResponse())
      .mockResolvedValueOnce(accountResponse({ displayName: "Refetch" }));
    const { result } = renderHook(() => useMyAccount());
    await waitFor(() => expect(result.current.account).not.toBeNull());
    act(() => emitMyAccountChanged());
    await waitFor(() =>
      expect(result.current.account?.displayName).toBe("Refetch"),
    );
  });

  it.each(["logout", "switch"] as const)(
    "response đến sau $label không hiển thị tài khoản của phiên trước",
    async (mode) => {
      const pending = deferred();
      vi.mocked(apiRequest).mockReturnValue(pending.promise);
      const { result } = renderHook(() => useMyAccount());
      state.session =
        mode === "logout"
          ? null
          : { ...state.session!, accountId: OTHER_ACCOUNT_ID };
      await act(async () => {
        pending.resolve(accountResponse());
      });
      await waitFor(() => expect(result.current.loading).toBe(false));
      expect(result.current.account).toBeNull();
      expect(result.current.error).toBe(formatAppMessage("MSG-14"));
    },
  );

  it("rerender sau đổi phiên không giữ account đã load", async () => {
    vi.mocked(apiRequest).mockResolvedValue(accountResponse());
    const { result, rerender } = renderHook(() => useMyAccount());
    await waitFor(() => expect(result.current.account).not.toBeNull());
    state.session = null;
    rerender();
    expect(result.current.account).toBeNull();
    expect(result.current.error).toBe(formatAppMessage("MSG-14"));
  });

  it("đổi route ẩn account cũ và hủy request khi unmount", async () => {
    const pending = deferred();
    vi.mocked(apiRequest)
      .mockResolvedValueOnce(accountResponse())
      .mockReturnValueOnce(pending.promise);
    const { result, rerender, unmount } = renderHook(() => useMyAccount());
    await waitFor(() => expect(result.current.account).not.toBeNull());
    state.path = "/portal/account";
    rerender();
    expect(result.current.account).toBeNull();
    expect(result.current.loading).toBe(true);
    const signal = vi.mocked(apiRequest).mock.calls[1][1]?.signal;
    unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => {
      pending.resolve(accountResponse());
    });
  });

  it("unmount gỡ listener thay đổi tài khoản", async () => {
    vi.mocked(apiRequest).mockResolvedValue(accountResponse());
    const remove = vi.spyOn(window, "removeEventListener");
    const { result, unmount } = renderHook(() => useMyAccount());
    await waitFor(() => expect(result.current.account).not.toBeNull());
    unmount();
    expect(remove).toHaveBeenCalledWith(
      "my-account:changed",
      expect.any(Function),
    );
    expect(remove).toHaveBeenCalledWith(
      "my-account:access-lost",
      expect.any(Function),
    );
    remove.mockRestore();
  });
});

it("GET mất xác thực ở một consumer xóa account ở các consumer khác", async () => {
  vi.mocked(apiRequest)
    .mockResolvedValueOnce(accountResponse())
    .mockResolvedValueOnce(accountResponse())
    .mockRejectedValueOnce(new ApiError(401));
  const { result } = renderHook(() => ({
    a: useMyAccount(),
    b: useMyAccount(),
  }));
  await waitFor(() =>
    expect(result.current.a.account && result.current.b.account).toBeTruthy(),
  );
  act(() => result.current.a.reload());
  await waitFor(() =>
    expect(result.current.a.error).toBe(formatAppMessage("MSG-14")),
  );
  expect(result.current.a.account).toBeNull();
  expect(result.current.b.account).toBeNull();
  expect(result.current.b.error).toBe(formatAppMessage("MSG-14"));
});

it.each(["success", 401, 403, 404] as const)(
  "PATCH của phiên A hoàn tất (%s) không xóa tài khoản B đang mount",
  async (outcome) => {
    const pending = deferred();
    vi.mocked(apiRequest)
      .mockResolvedValueOnce(accountResponse())
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValueOnce(accountResponse({ id: OTHER_ACCOUNT_ID }));
    const first = renderHook(() => useMyAccount());
    await waitFor(() =>
      expect(first.result.current.account?.id).toBe(ACCOUNT_ID),
    );
    const mutation = updateMyDisplayName("Tên của A");
    const settled = mutation.catch((error: unknown) => error);
    first.unmount();
    state.session = { ...state.session!, accountId: OTHER_ACCOUNT_ID };
    const second = renderHook(() => useMyAccount());
    await waitFor(() =>
      expect(second.result.current.account?.id).toBe(OTHER_ACCOUNT_ID),
    );
    await act(async () => {
      if (outcome === "success") pending.resolve(accountResponse());
      else pending.reject(new ApiError(outcome));
      await settled;
    });
    expect(second.result.current.account?.id).toBe(OTHER_ACCOUNT_ID);
    expect(second.result.current.error).toBeNull();
  },
);

it("GET lỗi của phiên A không phát mất quyền sang consumer của phiên B", async () => {
  const pending = deferred();
  vi.mocked(apiRequest)
    .mockReturnValueOnce(pending.promise)
    .mockResolvedValueOnce(accountResponse({ id: OTHER_ACCOUNT_ID }));
  const first = renderHook(() => useMyAccount());
  state.session = { ...state.session!, accountId: OTHER_ACCOUNT_ID };
  const second = renderHook(() => useMyAccount());
  await waitFor(() =>
    expect(second.result.current.account?.id).toBe(OTHER_ACCOUNT_ID),
  );
  await act(async () => pending.reject(new ApiError(401)));
  expect(first.result.current.account).toBeNull();
  expect(second.result.current.account?.id).toBe(OTHER_ACCOUNT_ID);
  expect(second.result.current.error).toBeNull();
});
