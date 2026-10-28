import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearMockSignedInKind,
  setMockSignedInKind,
} from "@/lib/session/mockSignedInKind";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { resetMyAccountMockStore } from "../api/myAccountApi";
import {
  MOCK_MY_ACCOUNT,
  MOCK_PATIENT_ACCOUNT,
} from "../api/myAccountMockData";
import { RoleHomeRedirect } from "./RoleHomeRedirect";

const { replace, getSession } = vi.hoisted(() => ({
  replace: vi.fn(),
  getSession: vi.fn(),
}));

// Ranh giới ngoài: lớp phiên đăng nhập và router. Tài khoản đi qua API giả thật.
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));
vi.mock("@/lib/auth/authSession", () => ({ getSession }));

const signedIn = { accessToken: "token-mock" };

beforeEach(() => {
  replace.mockClear();
  getSession.mockReset();
  resetMyAccountMockStore(MOCK_MY_ACCOUNT);
  resetMyAccountMockStore(MOCK_PATIENT_ACCOUNT);
  clearMockSignedInKind();
});

describe("RoleHomeRedirect", () => {
  it("chưa có phiên thì về đăng nhập", async () => {
    getSession.mockResolvedValue(null);
    render(<RoleHomeRedirect />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
  });

  it("bệnh nhân đăng nhập thì vào cổng bệnh nhân", async () => {
    getSession.mockResolvedValue(signedIn);
    setMockSignedInKind("PATIENT");
    render(<RoleHomeRedirect />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/portal"));
  });

  it("quản trị viên đăng nhập thì vào khu quản trị", async () => {
    getSession.mockResolvedValue(signedIn);
    render(<RoleHomeRedirect />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/admin"));
  });

  it("lỗi đọc phiên: hiện thông báo kèm nút thử lại, thử lại thì chuyển trang", async () => {
    getSession
      .mockRejectedValueOnce(new Error("mất mạng"))
      .mockResolvedValueOnce(signedIn);
    render(<RoleHomeRedirect />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      formatAppMessage("MSG-04"),
    );
    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/admin"));
  });
});
