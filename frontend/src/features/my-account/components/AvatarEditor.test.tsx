import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest, fetchBlob } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { toast } from "sonner";
import { AvatarEditor } from "./AvatarEditor";
import { useMyAccount } from "../hooks/useMyAccount";
import {
  ACCOUNT_ID,
  OTHER_ACCOUNT_ID,
  accountResponse,
} from "../api/myAccountTestFixtures";

const state = vi.hoisted(() => ({ owner: "" }));
vi.mock("next/navigation", () => ({ usePathname: () => "/account" }));
vi.mock("@/lib/api/client", () => ({
  apiRequest: vi.fn(),
  fetchBlob: vi.fn(),
}));
vi.mock("@/lib/auth/authSession", async (original) => ({
  ...(await original<typeof import("@/lib/auth/authSession")>()),
  peekSession: () => ({ accountId: state.owner }),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
let server = accountResponse();
beforeEach(() => {
  vi.resetAllMocks();
  state.owner = ACCOUNT_ID;
  server = accountResponse({
    allowedActions: ["updateAvatar"],
    avatarUrl: null,
  });
  vi.mocked(apiRequest).mockImplementation(async (path, init) => {
    if (path === "/api/v1/me") return { ...server };
    if (init?.method === "PUT") {
      server = {
        ...server,
        avatarUrl: `/api/v1/accounts/${server.id}/avatar`,
        allowedActions: ["updateAvatar", "removeAvatar"],
      };
      return { ...server };
    }
    if (init?.method === "DELETE") {
      server = { ...server, avatarUrl: null, allowedActions: ["updateAvatar"] };
      return undefined;
    }
    throw new Error("Unexpected HTTP request");
  });
  vi.mocked(fetchBlob).mockResolvedValue({
    blob: new Blob(["server-photo"]),
    fileName: "",
  });
  let count = 0;
  vi.spyOn(URL, "createObjectURL").mockImplementation(
    () => `blob:photo-${++count}`,
  );
  vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
});
afterEach(() => vi.restoreAllMocks());

function View({ slot = "a" }: { slot?: string }) {
  const { account, error, avatarError } = useMyAccount();
  if (!account) return <p data-testid={slot}>{error ?? "loading"}</p>;
  return (
    <section data-testid={slot}>
      <p>{account.displayName}</p>
      <output>{account.avatarUrl ?? "none"}</output>
      {avatarError ? <p role="alert">{avatarError}</p> : null}
      <AvatarEditor account={account} />
    </section>
  );
}
const photo = () =>
  new File([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])], "a.png", {
    type: "image/png",
  });
async function pick(slot = "a") {
  await waitFor(() =>
    expect(
      within(screen.getByTestId(slot)).getByRole("button", {
        name: "Chọn ảnh",
      }),
    ).toBeInTheDocument(),
  );
  const area = within(screen.getByTestId(slot));
  fireEvent.change(area.getByLabelText("Tệp ảnh đại diện"), {
    target: { files: [photo()] },
  });
}
function writes(method: string) {
  return vi
    .mocked(apiRequest)
    .mock.calls.filter(([, init]) => init?.method === method);
}

describe("AvatarEditor + useMyAccount + HTTP boundary", () => {
  it("upload đồng bộ hai consumer; thay ảnh cùng URL vẫn tải blob mới; delete refetch và thu hồi", async () => {
    render(
      <>
        <View />
        <View slot="b" />
      </>,
    );
    await pick();
    await userEvent.click(
      within(screen.getByTestId("a")).getByRole("button", { name: "Lưu ảnh" }),
    );
    await waitFor(() =>
      expect(
        screen.getByTestId("a").querySelector("output")?.textContent,
      ).toMatch(/^blob:/),
    );
    await waitFor(() =>
      expect(
        screen.getByTestId("b").querySelector("output")?.textContent,
      ).toMatch(/^blob:/),
    );
    expect(fetchBlob).toHaveBeenCalledTimes(2);
    expect(toast.success).toHaveBeenCalled();
    const firstUrl = screen
      .getByTestId("b")
      .querySelector("output")?.textContent;
    await pick();
    await userEvent.click(
      within(screen.getByTestId("a")).getByRole("button", { name: "Lưu ảnh" }),
    );
    await waitFor(() => expect(fetchBlob).toHaveBeenCalledTimes(4));
    await waitFor(() =>
      expect(
        screen.getByTestId("b").querySelector("output")?.textContent,
      ).not.toBe(firstUrl),
    );
    expect(URL.revokeObjectURL).toHaveBeenCalledWith(firstUrl);
    await userEvent.click(
      within(screen.getByTestId("a")).getByRole("button", { name: "Gỡ ảnh" }),
    );
    await waitFor(() =>
      expect(screen.getByTestId("a").querySelector("output")).toHaveTextContent(
        "none",
      ),
    );
    await waitFor(() =>
      expect(screen.getByTestId("b").querySelector("output")).toHaveTextContent(
        "none",
      ),
    );
    expect(writes("PUT")).toHaveLength(2);
    expect(writes("DELETE")).toHaveLength(1);
  });
  it("preview được thu hồi khi hủy chọn và unmount", async () => {
    const { unmount } = render(<View />);
    await pick();
    await userEvent.click(screen.getByRole("button", { name: "Hủy" }));
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:photo-1");
    await pick();
    unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:photo-2");
  });
  it("file không hợp lệ không upload; lỗi magic bytes từ backend hiển thị validation ảnh", async () => {
    render(<View />);
    await screen.findByRole("button", { name: "Chọn ảnh" });
    fireEvent.change(screen.getByLabelText("Tệp ảnh đại diện"), {
      target: { files: [new File(["gif"], "a.gif", { type: "image/gif" })] },
    });
    expect(await screen.findByText(/Ảnh không hợp lệ/)).toBeInTheDocument();
    expect(writes("PUT")).toHaveLength(0);
    await pick();
    vi.mocked(apiRequest).mockRejectedValueOnce(
      new ApiError(400, { errorCode: "MSG-08" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "Lưu ảnh" }));
    expect(await screen.findByText(/Ảnh không hợp lệ/)).toBeInTheDocument();
    expect(toast.success).not.toHaveBeenCalled();
  });
  it("upload lỗi mạng giữ dữ liệu cũ, cho thử lại", async () => {
    render(<View />);
    await pick();
    vi.mocked(apiRequest).mockRejectedValueOnce(
      new TypeError("Failed to fetch"),
    );
    await userEvent.click(screen.getByRole("button", { name: "Lưu ảnh" }));
    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    expect(screen.getByRole("button", { name: "Lưu ảnh" })).toBeEnabled();
    expect(screen.getByTestId("a").querySelector("output")).toHaveTextContent(
      "none",
    );
  });
  it("delete 503 giữ ảnh đang có", async () => {
    server = {
      ...server,
      avatarUrl: "/api/v1/accounts/id/avatar",
      allowedActions: ["removeAvatar"],
    };
    render(<View />);
    await waitFor(() =>
      expect(
        screen.getByTestId("a").querySelector("output")?.textContent,
      ).toMatch(/^blob:/),
    );
    vi.mocked(apiRequest).mockRejectedValueOnce(new ApiError(503));
    await userEvent.click(screen.getByRole("button", { name: "Gỡ ảnh" }));
    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    expect(
      screen.getByTestId("a").querySelector("output")?.textContent,
    ).toMatch(/^blob:/);
    expect(URL.revokeObjectURL).not.toHaveBeenCalled();
  });
  it.each([true, false])(
    "forced-password hoặc thiếu quyền không có nút mutation (%s)",
    async (forced) => {
      server = {
        ...server,
        mustChangePassword: forced,
        allowedActions: forced ? ["updateAvatar", "removeAvatar"] : [],
      };
      render(<View />);
      await screen.findByText(server.displayName);
      expect(screen.queryByRole("button", { name: "Chọn ảnh" })).toBeNull();
      expect(screen.queryByRole("button", { name: "Gỡ ảnh" })).toBeNull();
      expect(writes("PUT")).toHaveLength(0);
    },
  );
  it("double submit upload chỉ gửi một request; đổi account bỏ response cũ và không toast", async () => {
    let finish!: (value: unknown) => void;
    const { rerender } = render(<View key="A" />);
    await pick();
    vi.mocked(apiRequest).mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const save = screen.getByRole("button", { name: "Lưu ảnh" });
    fireEvent.click(save);
    fireEvent.click(save);
    expect(writes("PUT")).toHaveLength(1);
    const signal = writes("PUT")[0][1]?.signal;
    state.owner = OTHER_ACCOUNT_ID;
    server = accountResponse({
      id: OTHER_ACCOUNT_ID,
      displayName: "Tài khoản B",
    });
    rerender(<View key="B" />);
    await screen.findByText("Tài khoản B");
    expect(signal?.aborted).toBe(true);
    await act(async () =>
      finish(accountResponse({ avatarUrl: "/api/v1/accounts/old/avatar" })),
    );
    expect(screen.getByText("Tài khoản B")).toBeInTheDocument();
    expect(toast.success).not.toHaveBeenCalled();
    expect(fetchBlob).not.toHaveBeenCalled();
  });
});
