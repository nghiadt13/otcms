import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { toast } from "sonner";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { useMyAccount } from "../hooks/useMyAccount";
import { ACCOUNT_ID, accountResponse } from "../api/myAccountTestFixtures";
import { DisplayNameRow } from "./DisplayNameRow";

vi.mock("next/navigation", () => ({ usePathname: () => "/account" }));
vi.mock("@/lib/api/client", () => ({ apiRequest: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("@/lib/auth/authSession", async (original) => ({
  ...(await original<typeof import("@/lib/auth/authSession")>()),
  peekSession: () => ({ accountId: ACCOUNT_ID }),
}));

beforeEach(() => {
  vi.resetAllMocks();
});

/** Ghép hook và component thật; chỉ HTTP, phiên/router và toast là ranh giới giả. */
function AccountEditor() {
  const { account, error, loading } = useMyAccount();
  if (error) return <p role="alert">{error}</p>;
  if (!account)
    return <p role="status">{loading ? "Đang tải" : "Không có tài khoản"}</p>;
  return (
    <DisplayNameRow
      displayName={account.displayName}
      allowedActions={account.allowedActions}
      mustChangePassword={account.mustChangePassword}
    />
  );
}

async function editName(name: string) {
  const user = userEvent.setup();
  await user.click(
    await screen.findByRole("button", { name: "Sửa tên hiển thị" }),
  );
  fireEvent.change(screen.getByLabelText("Tên hiển thị"), {
    target: { value: name },
  });
  await user.click(screen.getByRole("button", { name: "Lưu" }));
}

describe("DisplayNameRow + useMyAccount + GET/PATCH thật", () => {
  it("load rồi lưu, hiển thị tên chuẩn server và MSG-05; không refetch dư", async () => {
    vi.mocked(apiRequest)
      .mockResolvedValueOnce(accountResponse())
      .mockResolvedValueOnce(
        accountResponse({
          displayName: "Server chuẩn hóa",
          allowedActions: [],
        }),
      );
    render(<AccountEditor />);
    await editName(" \tTên người dùng\n ");
    expect(await screen.findByText("Server chuẩn hóa")).toBeInTheDocument();
    expect(toast.success).toHaveBeenCalledWith(formatAppMessage("MSG-05"));
    expect(apiRequest).toHaveBeenNthCalledWith(2, "/api/v1/me", {
      method: "PATCH",
      body: JSON.stringify({ displayName: "Tên người dùng" }),
    });
    expect(apiRequest).toHaveBeenCalledTimes(2);
    expect(
      screen.queryByRole("button", { name: "Sửa tên hiển thị" }),
    ).not.toBeInTheDocument();
  });

  it.each([
    { allowedActions: [], mustChangePassword: false },
    { allowedActions: ["changePassword"], mustChangePassword: true },
    { allowedActions: ["updateDisplayName"], mustChangePassword: true },
  ])(
    "không có quyền/forced-password thì chỉ đọc, không PATCH",
    async ({ allowedActions, mustChangePassword }) => {
      vi.mocked(apiRequest).mockResolvedValue(
        accountResponse({ allowedActions, mustChangePassword }),
      );
      render(<AccountEditor />);
      expect(await screen.findByText("Tên từ backend")).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "Sửa tên hiển thị" }),
      ).not.toBeInTheDocument();
      expect(apiRequest).toHaveBeenCalledTimes(1);
    },
  );

  it("chưa được truyền quyền thì không tự bật nút sửa", () => {
    render(<DisplayNameRow displayName="Tên đang xem" />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it.each(["", "A", "😀", "😀".repeat(101)])(
    "validation Unicode không hợp lệ không gửi PATCH",
    async (name) => {
      vi.mocked(apiRequest).mockResolvedValue(accountResponse());
      render(<AccountEditor />);
      await editName(name);
      expect(await screen.findByRole("alert")).toBeInTheDocument();
      expect(apiRequest).toHaveBeenCalledTimes(1);
      expect(toast.success).not.toHaveBeenCalled();
    },
  );

  it("100 emoji hợp lệ dù 200 UTF-16 units", async () => {
    const name = "😀".repeat(100);
    vi.mocked(apiRequest)
      .mockResolvedValueOnce(accountResponse())
      .mockResolvedValueOnce(accountResponse({ displayName: name }));
    render(<AccountEditor />);
    await editName(" " + name + " ");
    await waitFor(() => expect(toast.success).toHaveBeenCalledTimes(1));
    expect(screen.getByText(name)).toBeInTheDocument();
  });

  it.each([
    [400, "MSG-08"],
    [403, "MSG-14"],
    [500, "MSG-04"],
  ] as const)(
    "PATCH lỗi HTTP %s hiển thị MSG đúng, không báo thành công",
    async (status, code) => {
      vi.mocked(apiRequest)
        .mockResolvedValueOnce(accountResponse())
        .mockRejectedValueOnce(new ApiError(status, { errorCode: code }));
      render(<AccountEditor />);
      await editName("Tên mới");
      expect(await screen.findByRole("alert")).toHaveTextContent(
        formatAppMessage(code),
      );
      expect(toast.success).not.toHaveBeenCalled();
      if (status === 403)
        expect(screen.queryByText("Tên từ backend")).not.toBeInTheDocument();
    },
  );

  it("PATCH lỗi mạng không hiển thị tên chưa lưu", async () => {
    vi.mocked(apiRequest)
      .mockResolvedValueOnce(accountResponse())
      .mockRejectedValueOnce(new TypeError("Failed to fetch"));
    render(<AccountEditor />);
    await editName("Tên chưa lưu");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );
    expect(toast.success).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "Hủy" }));
    expect(screen.getByText("Tên từ backend")).toBeInTheDocument();
  });

  it("GET thất bại không hiện mock hoặc thao tác sửa", async () => {
    vi.mocked(apiRequest).mockRejectedValue(new ApiError(401));
    render(<AccountEditor />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-14"),
    );
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.queryByText("Lê Thị Hoa")).not.toBeInTheDocument();
  });
});
