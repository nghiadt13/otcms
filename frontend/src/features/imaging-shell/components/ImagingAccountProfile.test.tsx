import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest, fetchBlob } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { toast } from "sonner";
import {
  ACCOUNT_ID,
  accountResponse,
} from "@/features/my-account/api/myAccountTestFixtures";
import { createDemoState } from "@/features/imaging/api/imagingMockData";
import { ImagingAccountProfile } from "./ImagingAccountProfile";

vi.mock("next/navigation", () => ({ usePathname: () => "/imaging/profile" }));
vi.mock("@/lib/api/client", () => ({
  apiRequest: vi.fn(),
  fetchBlob: vi.fn(),
}));
vi.mock("@/lib/auth/authSession", async (original) => ({
  ...(await original<typeof import("@/lib/auth/authSession")>()),
  peekSession: () => ({ accountId: ACCOUNT_ID }),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

let server = accountResponse();
let preview: string;
beforeEach(() => {
  vi.resetAllMocks();
  server = accountResponse({
    accountType: "IMAGING_TECHNICIAN",
    role: "IMAGING_TECHNICIAN",
    username: "imaging.live",
    displayName: "Tài khoản Imaging thật",
    allowedActions: ["updateAvatar", "changePassword"],
  });
  preview = JSON.stringify(createDemoState());
  localStorage.setItem("otcms-imaging-preview-v1", preview);
  vi.mocked(apiRequest).mockImplementation(async (path, init) => {
    if (path === "/api/v1/me") return { ...server };
    if (path === "/api/v1/me/avatar" && init?.method === "PUT") {
      server = {
        ...server,
        avatarUrl: `/api/v1/accounts/${ACCOUNT_ID}/avatar`,
        allowedActions: ["updateAvatar", "removeAvatar", "changePassword"],
      };
      return { ...server };
    }
    if (path === "/api/v1/me/avatar" && init?.method === "DELETE") {
      server = { ...server, avatarUrl: null, allowedActions: ["updateAvatar"] };
      return undefined;
    }
    throw new Error(`Unexpected request: ${path}`);
  });
  vi.mocked(fetchBlob).mockResolvedValue({
    blob: new Blob(["photo"]),
    fileName: "",
  });
  let count = 0;
  vi.spyOn(URL, "createObjectURL").mockImplementation(
    () => `blob:imaging-${++count}`,
  );
  vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
});
afterEach(() => {
  expect(localStorage.getItem("otcms-imaging-preview-v1")).toBe(preview);
  localStorage.removeItem("otcms-imaging-preview-v1");
  vi.restoreAllMocks();
});

async function pickPhoto() {
  await screen.findByRole("button", { name: "Chọn ảnh" });
  fireEvent.change(screen.getByLabelText("Tệp ảnh đại diện"), {
    target: {
      files: [
        new File(
          [new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])],
          "avatar.png",
          { type: "image/png" },
        ),
      ],
    },
  });
}

describe("Imaging profile with live account HTTP boundary", () => {
  it("reads /me, uses authoritative fields and leaves the entire preview intact", async () => {
    render(<ImagingAccountProfile />);
    expect(screen.getByRole("status")).toHaveTextContent("Đang tải");
    await screen.findByText("imaging.live");
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/me",
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    );
    expect(
      screen.getByRole("heading", { name: server.displayName }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Mã nhân viên")).toBeNull();
    expect(screen.queryByText("Liên hệ công việc")).toBeNull();
    expect(screen.queryByText("KTV-001")).toBeNull();
    expect(
      screen.getByRole("link", { name: "Cập nhật tài khoản" }),
    ).toHaveAttribute("href", "/imaging/profile/edit");
    expect(screen.getByRole("link", { name: "Đổi mật khẩu" })).toHaveAttribute(
      "href",
      "/imaging/change-password",
    );
  });

  it("edit uploads multipart through the existing mutation, then removes with DELETE and refetch", async () => {
    render(<ImagingAccountProfile edit />);
    await pickPhoto();
    await userEvent.click(screen.getByRole("button", { name: "Lưu ảnh" }));
    await screen.findByRole("button", { name: "Gỡ ảnh" });
    const upload = vi
      .mocked(apiRequest)
      .mock.calls.find(([, init]) => init?.method === "PUT");
    expect(upload?.[0]).toBe("/api/v1/me/avatar");
    expect((upload?.[1]?.body as FormData).get("photo")).toBeInstanceOf(File);
    await waitFor(() =>
      expect(fetchBlob).toHaveBeenCalledWith(
        `/api/v1/accounts/${ACCOUNT_ID}/avatar`,
        expect.anything(),
      ),
    );
    await userEvent.click(screen.getByRole("button", { name: "Gỡ ảnh" }));
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: "Gỡ ảnh" })).toBeNull(),
    );
    expect(apiRequest).toHaveBeenCalledWith(
      "/api/v1/me/avatar",
      expect.objectContaining({ method: "DELETE" }),
    );
    expect(
      vi
        .mocked(apiRequest)
        .mock.calls.filter(([path]) => path === "/api/v1/me"),
    ).toHaveLength(2);
    expect(toast.success).toHaveBeenCalledTimes(2);
  });

  it("shows protected avatar as an object URL, then revokes it on unmount", async () => {
    server.avatarUrl = `/api/v1/accounts/${ACCOUNT_ID}/avatar`;
    const { unmount } = render(<ImagingAccountProfile />);
    await waitFor(() =>
      expect(screen.getByAltText("Ảnh đại diện tài khoản")).toHaveAttribute(
        "src",
        "blob:imaging-1",
      ),
    );
    unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:imaging-1");
  });

  it("load failure shows retry without fallback to preview account", async () => {
    vi.mocked(apiRequest).mockRejectedValueOnce(new ApiError(503));
    render(<ImagingAccountProfile />);
    await screen.findByRole("alert");
    expect(screen.queryByText("imaging.live")).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText("imaging.live");
  });

  it.each([true, false])(
    "no avatar mutation when forced password or no allowed action (%s)",
    async (forced) => {
      server.mustChangePassword = forced;
      server.allowedActions = forced ? ["updateAvatar", "removeAvatar"] : [];
      render(<ImagingAccountProfile edit />);
      await screen.findByText("imaging.live");
      expect(screen.queryByRole("button", { name: "Chọn ảnh" })).toBeNull();
      expect(screen.queryByRole("button", { name: "Gỡ ảnh" })).toBeNull();
    },
  );

  it("upload failure keeps retry available and never reports success", async () => {
    render(<ImagingAccountProfile edit />);
    await pickPhoto();
    vi.mocked(apiRequest).mockRejectedValueOnce(new ApiError(503));
    await userEvent.click(screen.getByRole("button", { name: "Lưu ảnh" }));
    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    expect(screen.getByRole("button", { name: "Lưu ảnh" })).toBeEnabled();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it("access denial during edit clears account details and mutation controls", async () => {
    render(<ImagingAccountProfile edit />);
    await pickPhoto();
    vi.mocked(apiRequest).mockRejectedValueOnce(new ApiError(403));
    await userEvent.click(screen.getByRole("button", { name: "Lưu ảnh" }));
    await screen.findByRole("alert");
    expect(screen.queryByText("imaging.live")).toBeNull();
    expect(screen.queryByRole("button", { name: "Lưu ảnh" })).toBeNull();
  });

  it("ignores /me response after the screen unmounts", async () => {
    let finish!: (value: unknown) => void;
    vi.mocked(apiRequest).mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const { unmount } = render(<ImagingAccountProfile />);
    const signal = vi.mocked(apiRequest).mock.calls[0][1]?.signal;
    unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => finish(server));
    expect(fetchBlob).not.toHaveBeenCalled();
  });
});
