import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { fetchCurrentAccount, signOut } = vi.hoisted(() => ({
  fetchCurrentAccount: vi.fn(),
  signOut: vi.fn(),
}));

// Ranh giới ngoài được giả: đọc tài khoản từ backend và huỷ phiên.
vi.mock("@/lib/auth/currentAccountApi", () => ({
  fetchCurrentAccount,
  peekCurrentAccount: () => null,
}));
vi.mock("@/lib/auth/authSession", () => ({ signOut }));

import { SiteAccountLinks } from "./SiteAccountLinks";

const account = {
  id: "11111111-1111-1111-1111-111111111111",
  accountType: "PATIENT",
  email: "an@example.com",
  displayName: "Nguyễn Văn An",
};

beforeEach(() => {
  fetchCurrentAccount.mockReset();
  signOut.mockReset();
  signOut.mockResolvedValue(undefined);
});

describe("SiteAccountLinks", () => {
  it("chưa đăng nhập: hiện Đăng nhập và Đăng ký", async () => {
    fetchCurrentAccount.mockResolvedValue(null);
    render(<SiteAccountLinks variant="topbar" />);
    expect(
      await screen.findByRole("link", { name: "Đăng nhập" }),
    ).toHaveAttribute("href", "/login");
    expect(screen.getByRole("link", { name: "Đăng ký" })).toHaveAttribute(
      "href",
      "/register",
    );
  });

  it("đã đăng nhập: thay hai nút bằng biểu tượng và tên từ backend", async () => {
    fetchCurrentAccount.mockResolvedValue(account);
    render(<SiteAccountLinks variant="topbar" />);
    expect(
      await screen.findByRole("button", { name: "Tài khoản: Nguyễn Văn An" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Nguyễn Văn An")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Đăng nhập" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Đăng ký" })).toBeNull();
  });

  it("không đọc được tài khoản: giữ nút Đăng nhập", async () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    fetchCurrentAccount.mockRejectedValue(new Error("mạng"));
    render(<SiteAccountLinks variant="sheet" />);
    expect(
      await screen.findByRole("link", { name: "Đăng nhập" }),
    ).toBeInTheDocument();
  });

  it("menu di động: đăng xuất huỷ phiên rồi trả về nút Đăng nhập", async () => {
    fetchCurrentAccount.mockResolvedValue(account);
    const onNavigate = vi.fn();
    render(<SiteAccountLinks onNavigate={onNavigate} variant="sheet" />);
    expect(await screen.findByText("Nguyễn Văn An")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Đăng xuất" }));
    expect(signOut).toHaveBeenCalledTimes(1);
    expect(onNavigate).toHaveBeenCalled();
    expect(
      await screen.findByRole("link", { name: "Đăng nhập" }),
    ).toBeInTheDocument();
  });
});
