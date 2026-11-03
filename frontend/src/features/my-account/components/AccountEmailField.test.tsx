import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { toast } from "sonner";
import { AccountEmailField } from "./AccountEmailField";
import {
  ACCOUNT_ID,
  OTHER_ACCOUNT_ID,
  accountResponse,
  patientResponse,
} from "../api/myAccountTestFixtures";

const state = vi.hoisted(() => ({ owner: "" }));
vi.mock("next/navigation", () => ({ usePathname: () => "/portal/account" }));
vi.mock("@/lib/api/client", () => ({
  apiRequest: vi.fn(),
  fetchBlob: vi.fn(),
}));
vi.mock("@/lib/auth/authSession", async (original) => ({
  ...(await original<typeof import("@/lib/auth/authSession")>()),
  peekSession: () => ({ accountId: state.owner }),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
let server = patientResponse();
let pendingEmail = "";
beforeEach(() => {
  vi.resetAllMocks();
  state.owner = ACCOUNT_ID;
  server = patientResponse({
    email: "an@example.com",
    allowedActions: ["changeEmail"],
  });
  pendingEmail = "";
  vi.mocked(apiRequest).mockImplementation(async (path, init) => {
    if (path === "/api/v1/me") return { ...server };
    const body = JSON.parse(String(init?.body)) as {
      newEmail?: string;
      code?: string;
    };
    if (path === "/api/v1/me/email-change") {
      pendingEmail = body.newEmail!;
      return { message: "MSG-06" };
    }
    if (path === "/api/v1/me/email-change/verify") {
      if (body.code !== "123456")
        throw new ApiError(400, { errorCode: "MSG-82" });
      server = { ...server, email: pendingEmail };
      return { ...server };
    }
    throw new Error("Unexpected HTTP request");
  });
});
async function openFlow() {
  await userEvent.click(
    await screen.findByRole("button", { name: "Đổi email" }),
  );
}
async function requestCode(email = "moi@example.com") {
  await userEvent.type(screen.getByLabelText("Email mới"), email);
  await userEvent.click(screen.getByRole("button", { name: "Gửi mã" }));
  await screen.findByLabelText("Mã xác minh");
}
function calls(path: string) {
  return vi.mocked(apiRequest).mock.calls.filter(([url]) => url === path);
}

describe("AccountEmailField flow thật qua apiRequest", () => {
  it("202 chỉ pending MSG-06, email cũ không đổi; verify đúng mới lấy email server", async () => {
    render(<AccountEmailField email="email-prop-cu@example.com" />);
    await openFlow();
    await requestCode();
    expect(screen.getByTitle("an@example.com")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      formatAppMessage("MSG-06"),
    );
    expect(screen.queryByText(/đã gửi tới/i)).toBeNull();
    expect(toast.success).not.toHaveBeenCalled();
    await userEvent.type(screen.getByLabelText("Mã xác minh"), "123456");
    await userEvent.click(screen.getByRole("button", { name: "Xác minh" }));
    expect(await screen.findByTitle("moi@example.com")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(toast.success).toHaveBeenCalledWith(formatAppMessage("MSG-05"));
    expect(calls("/api/v1/me/email-change/verify")[0][1]?.body).toBe(
      JSON.stringify({ code: "123456" }),
    );
  });
  it.each(["sai", "hết hạn", "bị suppress"])(
    "OTP %s hiển thị MSG-82, giữ email cũ và xóa code",
    async () => {
      render(<AccountEmailField email="an@example.com" />);
      await openFlow();
      await requestCode("taken@example.com");
      vi.mocked(apiRequest).mockRejectedValueOnce(
        new ApiError(400, { errorCode: "MSG-82" }),
      );
      await userEvent.type(screen.getByLabelText("Mã xác minh"), "111111");
      await userEvent.click(screen.getByRole("button", { name: "Xác minh" }));
      expect(await screen.findByRole("alert")).toHaveTextContent(
        formatAppMessage("MSG-82"),
      );
      expect(screen.getByTitle("an@example.com")).toBeInTheDocument();
      expect(screen.getByLabelText("Mã xác minh")).toHaveValue("");
      expect(toast.success).not.toHaveBeenCalled();
    },
  );
  it.each([
    accountResponse(),
    patientResponse({ allowedActions: [] }),
    patientResponse({
      mustChangePassword: true,
      allowedActions: ["changeEmail"],
    }),
  ])(
    "patient-only, quyền server và forced-password chặn flow",
    async (response) => {
      server = response;
      render(<AccountEmailField email="an@example.com" />);
      await waitFor(() => expect(apiRequest).toHaveBeenCalledTimes(1));
      await act(async () => {});
      expect(screen.queryByRole("button", { name: "Đổi email" })).toBeNull();
      expect(calls("/api/v1/me/email-change")).toHaveLength(0);
    },
  );
  it.each([400, 401, 403, 503])(
    "request lỗi %s không chuyển sang OTP hoặc đổi email",
    async (status) => {
      render(<AccountEmailField email="an@example.com" />);
      await openFlow();
      vi.mocked(apiRequest).mockRejectedValueOnce(
        new ApiError(status, {
          errorCode:
            status === 400 ? "MSG-08" : status === 503 ? "MSG-04" : "MSG-14",
        }),
      );
      await userEvent.type(
        screen.getByLabelText("Email mới"),
        "moi@example.com",
      );
      await userEvent.click(screen.getByRole("button", { name: "Gửi mã" }));
      await waitFor(() =>
        expect(screen.queryByLabelText("Mã xác minh")).toBeNull(),
      );
      if (status === 400 || status === 503)
        expect(await screen.findByRole("alert")).toHaveTextContent(
          formatAppMessage(status === 400 ? "MSG-08" : "MSG-04"),
        );
      else await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
      expect(server.email).toBe("an@example.com");
    },
  );
  it("verify lỗi mạng giữ email cũ và cho thử lại", async () => {
    render(<AccountEmailField email="an@example.com" />);
    await openFlow();
    await requestCode();
    vi.mocked(apiRequest).mockRejectedValueOnce(
      new TypeError("Failed to fetch"),
    );
    await userEvent.type(screen.getByLabelText("Mã xác minh"), "123456");
    await userEvent.click(screen.getByRole("button", { name: "Xác minh" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );
    expect(screen.getByTitle("an@example.com")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xác minh" })).toBeEnabled();
  });
  it("đóng flow xóa mã; mở lại bắt đầu từ email mới trống", async () => {
    render(<AccountEmailField email="an@example.com" />);
    await openFlow();
    await requestCode();
    await userEvent.type(screen.getByLabelText("Mã xác minh"), "123");
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    await openFlow();
    expect(screen.getByLabelText("Email mới")).toHaveValue("");
    expect(screen.queryByLabelText("Mã xác minh")).toBeNull();
  });
  it("hủy request đang chờ bỏ response cũ; double submit chỉ gửi một POST", async () => {
    let finish!: (value: unknown) => void;
    render(<AccountEmailField email="an@example.com" />);
    await openFlow();
    await userEvent.type(screen.getByLabelText("Email mới"), "moi@example.com");
    vi.mocked(apiRequest).mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const form = screen.getByLabelText("Email mới").closest("form")!;
    fireEvent.submit(form);
    fireEvent.submit(form);
    expect(calls("/api/v1/me/email-change")).toHaveLength(1);
    const signal = calls("/api/v1/me/email-change")[0][1]?.signal;
    await userEvent.click(screen.getByRole("button", { name: "Hủy" }));
    expect(signal?.aborted).toBe(true);
    await act(async () => finish({ message: "MSG-06" }));
    await openFlow();
    expect(screen.getByLabelText("Email mới")).toHaveValue("");
    expect(screen.queryByLabelText("Mã xác minh")).toBeNull();
  });
  it("verify chờ khi đổi tài khoản: không cập nhật B, không báo thành công", async () => {
    let finish!: (value: unknown) => void;
    const { rerender } = render(
      <AccountEmailField key="A" email="an@example.com" />,
    );
    await openFlow();
    await requestCode();
    await userEvent.type(screen.getByLabelText("Mã xác minh"), "123456");
    vi.mocked(apiRequest).mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const form = screen.getByLabelText("Mã xác minh").closest("form")!;
    fireEvent.submit(form);
    fireEvent.submit(form);
    expect(calls("/api/v1/me/email-change/verify")).toHaveLength(1);
    state.owner = OTHER_ACCOUNT_ID;
    server = patientResponse({
      id: OTHER_ACCOUNT_ID,
      email: "b@example.com",
      allowedActions: ["changeEmail"],
    });
    rerender(<AccountEmailField key="B" email="b@example.com" />);
    await screen.findByTitle("b@example.com");
    await act(async () =>
      finish(patientResponse({ email: "moi@example.com" })),
    );
    expect(screen.getByTitle("b@example.com")).toBeInTheDocument();
    expect(toast.success).not.toHaveBeenCalled();
    await openFlow();
    expect(screen.getByLabelText("Email mới")).toHaveValue("");
  });
  it("gửi lại không đặt thời hạn/retry FE và vẫn giữ email cũ", async () => {
    render(<AccountEmailField email="an@example.com" />);
    await openFlow();
    await requestCode();
    await userEvent.type(screen.getByLabelText("Mã xác minh"), "123");
    await userEvent.click(screen.getByRole("button", { name: "Gửi lại mã" }));
    expect(calls("/api/v1/me/email-change")).toHaveLength(2);
    expect(screen.getByLabelText("Mã xác minh")).toHaveValue("");
    expect(screen.getByTitle("an@example.com")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      formatAppMessage("MSG-06"),
    );
  });
});
